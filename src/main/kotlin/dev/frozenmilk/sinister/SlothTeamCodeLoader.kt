@file:Suppress("DEPRECATION")

package dev.frozenmilk.sinister

import com.qualcomm.robotcore.util.RobotLog
import dalvik.system.DexFile
import dev.frozenmilk.sinister.loaders.SlothClassLoader
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.sinister.util.notify.Notifier
import org.firstinspires.ftc.robotcore.internal.files.RecursiveFileObserver
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Preload
@Suppress("unused")
object SlothTeamCodeLoader : RecursiveFileObserver.Listener {
    private val TAG = javaClass.simpleName
    private val dir = File("${AppUtil.FIRST_FOLDER}/dairy/sloth")
    private val lock = File("$dir/sloth.lock")
    private var loadEvent: LoadEvent<SlothClassLoader>? = null
    private var loadedFile: File? = null

    init {
        ensureFileHierarchy()
        val fileToLoad = findFileToLoad()
        if (fileToLoad !== null) {
            switchLoader(fileToLoad)
        } else {
            val loader = SlothClassLoader(
                AppUtil.getInstance().application.packageCodePath,
                "", // TODO
                SinisterImpl.rootLoader,
                SinisterImpl.ignoredClasses,
            )
            Notifier.notify("Staged TeamCode Load")
            Logger.v(TAG, "Staged TeamCode Load")
            SinisterImpl.stageLoad(null, loader, loader.classes) { newLoadEvent ->
                newLoadEvent.afterCancel {
                    Notifier.notify("Cancelled TeamCode Load")
                    Logger.v(TAG, "Cancelled TeamCode Load")
                }
                newLoadEvent.beforeRelease {
                    Logger.v(TAG, "Processing TeamCode Load")
                }
                newLoadEvent.afterRelease {
                    Notifier.notify("Processed TeamCode Load")
                    Logger.v(TAG, "Processed TeamCode Load")
                }
                newLoadEvent.beforeUnload {
                    Logger.v(TAG, "Unloading TeamCode Load")
                }
                newLoadEvent.afterUnload {
                    Notifier.notify("Unloaded TeamCode Load")
                    Logger.v(TAG, "Unloaded TeamCode Load")
                }
                loadEvent = newLoadEvent
            }
        }
    }

    private fun classes(file: File) = handleDex(
        TimeSource.Monotonic.markNow() + 2.5.seconds,
        {
            val file = DexFile(file.absolutePath)
            try {
                file.entries().asSequence().filter {
                    SinisterImpl.teamCodeSearch.determineInclusion(it) //
                            && SinisterImpl.rootLoader.pinned(it) == null
                }.toList()
            } finally {
                file.close()
            }
        },
        null,
    )

    private inline fun <T> handleDex(timeout: TimeMark, f: () -> T, orElse: T): T {
        while (timeout.hasNotPassedNow()) {
            try {
                return f()
            } catch (e: IOException) {
            }
        }
        return orElse
    }

    private fun switchLoader(file: File) {
        lock.createNewFile()
        // attempt to cancel,
        // can only work if event hasn't been released
        val oldLoadEvent = loadEvent?.cancel() ?: loadEvent
        // this loads classes from the loaded jar
        // it locks down to only teamcode classes
        // and will not load pinned classes from itself
        val classes = checkNotNull(classes(file)) { "Unable to open DexFile $file" }
        val loader = SlothClassLoader(
            file.absolutePath,
            "", // TODO
            SinisterImpl.rootLoader,
            classes,
        )

        Notifier.notify("Staged Sloth Load")
        Logger.v(TAG, "Staged Sloth Load")
        SinisterImpl.stageLoad(oldLoadEvent, loader, loader.classes) { newLoadEvent ->
            newLoadEvent.afterCancel {
                file.delete()
                Notifier.notify("Cancelled Sloth Load")
                Logger.v(TAG, "Cancelled Sloth Load")
            }
            newLoadEvent.beforeRelease {
                Logger.v(TAG, "Processing Sloth Load") //
            }
            newLoadEvent.afterRelease {
                Notifier.notify("Processed Sloth Load")
                Logger.v(TAG, "Processed Sloth Load")
            }
            newLoadEvent.beforeUnload {
                Logger.v(TAG, "Unloading Sloth Load") //
            }
            newLoadEvent.afterUnload {
                file.delete()
                Notifier.notify("Unloaded Sloth Load")
                Logger.v(TAG, "Unloaded Sloth Load")
            }
            oldLoadEvent?.let { oldLoadEvent ->
                newLoadEvent.beforeRelease {
                    // attempt to unload
                    Logger.d(
                        TAG,
                        "Attempting to unload previous Sloth Load, stage: ${oldLoadEvent.stage}"
                    )
                    oldLoadEvent.unload()
                }
            }
            loadEvent = newLoadEvent
        }
        loadedFile = file
        lock.delete()
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
    }

    private fun findFileToLoad(): File? {
        val files: Array<out File> =
            dir.listFiles { it.extension == "jar" }
                ?: return null
        files.sortBy { it.lastModified() }
        for (i in 1..<files.size) files[i].delete()
        return files.firstOrNull()
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
            if (event and RecursiveFileObserver.CREATE != 0 && file.isFile && file.extension == "jar") {
                lock.createNewFile()
                try {
                    switchLoader(file)
                } catch (e: Throwable) {
                    Logger.e(TAG, "failed to switch loader", e)
                    RobotLog.setGlobalErrorMsg(
                        "Failed to sloth load\n" +
                                e.stackTraceToString()
                    )
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