package dev.frozenmilk.sinister

import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.loaders.SlothClassLoader
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.sinister.util.notify.Notifier
import org.firstinspires.ftc.robotcore.internal.files.RecursiveFileObserver
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import java.io.File

@Preload
@Suppress("unused")
object SlothTeamCodeLoader : RecursiveFileObserver.Listener {
    private val TAG = javaClass.simpleName
    private val dir = File("${AppUtil.FIRST_FOLDER}/dairy/sloth")
    private val lock = File("$dir/sloth.lock")
    private val jarToLoad = File("$dir/to_load.jar")
    private val loadedJar = File("$dir/loaded.jar")
    private var loadEvent: LoadEvent<SlothClassLoader>? = null

	init {
		ensureFileHierarchy()
		if (!switchLoader()) {
			// attempt to cancel,
			// can only work if event hasn't been released
			loadEvent?.cancel()
			//val classes = classes(File(AppUtil.getInstance().application.packageCodePath))
			val loader = SlothClassLoader(
				AppUtil.getInstance().application.packageCodePath,
				"", // TODO
				SinisterImpl.rootLoader,
				SinisterImpl.ignoredClasses
			)
			Notifier.notify("Staged TeamCode Load")
			Logger.v(TAG, "Staged TeamCode Load")
			SinisterImpl.stageLoad(loader, loader.classes) {
				it.afterCancel {
					Notifier.notify("Cancelled TeamCode Load")
					Logger.v(TAG, "Cancelled TeamCode Load")
				}.beforeRelease { Logger.v(TAG, "Processing TeamCode Load") }.afterRelease {
					Notifier.notify("Processed TeamCode Load")
					Logger.v(TAG, "Processed TeamCode Load")
				}.beforeUnload { Logger.v(TAG, "Unloading TeamCode Load") }.afterUnload {
					Notifier.notify("Unloaded TeamCode Load")
					Logger.v(TAG, "Unloaded TeamCode Load")
				}
				loadEvent?.let { loadEvent ->
					it.beforeRelease {
						// attempt to unload
						loadEvent.unload()
					}
				}
				loadEvent = it
			}
		}
	}

	@Suppress("DEPRECATION")
	private fun classes() =
		run {
			val file = openDex(File(loadedJar.absolutePath), 15)
			val res = file.entries().asSequence().filter {
				SinisterImpl.teamCodeSearch.determineInclusion(it) && SinisterImpl.rootLoader.pinned(
					it
				) == null
			}.toList()
			file.close()
			res
		}

	private fun switchLoader(): Boolean {
		if (loadedJar.exists()) {
			if (loadedJar.isFile) {
				lock.createNewFile()
				// attempt to cancel,
				// can only work if event hasn't been released
				loadEvent?.cancel()
				// this loads classes from the loaded jar
				// it locks down to only teamcode classes
				// and will not load pinned classes from itself
				val classes = classes()

				fun openLoader(attempts: Int): SlothClassLoader =
					try {
						SlothClassLoader(
							loadedJar.absolutePath, "", // TODO
							SinisterImpl.rootLoader, classes
						).also { it.loadClass(classes.firstOrNull()) }
					} catch (e: Throwable) {
						if (attempts > 0) {
							RobotLog.vv(
								TAG,
								"Error occurred while creating Sloth loader: $e, trying again. $attempts attempt(s) remaining..."
							)
							openLoader(attempts - 1)
						} else throw e
					}

				val loader = openLoader(15)
				Notifier.notify("Staged Sloth Load")
				Logger.v(TAG, "Staged Sloth Load")
				SinisterImpl.stageLoad(loader, loader.classes) {
					it.afterCancel {
						Notifier.notify("Cancelled Sloth Load")
						Logger.v(TAG, "Cancelled Sloth Load")
					}.beforeRelease { Logger.v(TAG, "Processing Sloth Load") }.afterRelease {
						Notifier.notify("Processed Sloth Load")
						Logger.v(TAG, "Processed Sloth Load")
					}.beforeUnload { Logger.v(TAG, "Unloading Sloth Load") }.afterUnload {
						Notifier.notify("Unloaded Sloth Load")
						Logger.v(TAG, "Unloaded Sloth Load")
					}
					loadEvent?.let { loadEvent ->
						it.beforeRelease {
							// attempt to unload
							Logger.d(
								TAG,
								"Attempting to unload previous Sloth Load, stage: ${loadEvent.stage}"
							)
							loadEvent.unload()
						}
					}
					loadEvent = it
				}
				lock.delete()
				return true
			} else {
				loadedJar.delete()
			}
		}
		return false
	}

	private fun ensureFileHierarchy() {
		if (!dir.exists()) {
			Logger.d(TAG, "making sloth dir")
			dir.mkdirs()
		} else if (!dir.isDirectory) {
			Logger.d(TAG, "remaking sloth dir")
			dir.delete()
			dir.mkdirs()
		}
		if (lock.exists()) {
			Logger.v(TAG, "deleting dead lock.jar")
			lock.delete()
		}
		if (jarToLoad.exists()) {
			Logger.v(TAG, "deleting dead to_load.jar")
			jarToLoad.delete()
		}
	}

	private fun generateFileWatcher() = RecursiveFileObserver(
		dir,
		RecursiveFileObserver.CREATE or RecursiveFileObserver.DELETE_SELF or RecursiveFileObserver.MOVE_SELF or RecursiveFileObserver.IN_Q_OVERFLOW,
		RecursiveFileObserver.Mode.RECURSIVE,
		this
	).apply {
		this.startWatching()
	}

	private var watcher = generateFileWatcher()

	override fun onEvent(event: Int, file: File) {
		synchronized(this) {
			if (event and RecursiveFileObserver.CREATE != 0 && file.isFile && file.name == "to_load.jar") {
				lock.createNewFile()
				try {
					check(jarToLoad.renameTo(loadedJar)) { "Failed to rename to_load.jar to loaded.jar" }
					switchLoader()
				} catch (e: Throwable) {
					Logger.e(TAG, "failed to switch loader", e)
				} finally {
					lock.delete()
				}
			} else if (event and RecursiveFileObserver.IN_Q_OVERFLOW != 0) {
				watcher.stopWatching()
				ensureFileHierarchy()
				watcher = generateFileWatcher()
			}
		}
	}
}