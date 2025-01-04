package dev.frozenmilk.sinister

import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.sinister.util.notify.Notifier
import org.firstinspires.ftc.onbotjava.ExternalLibraries
import org.firstinspires.ftc.onbotjava.OnBotJavaClassLoader
import org.firstinspires.ftc.onbotjava.OnBotJavaHelperImpl

@Suppress("unused")
object ExternalLibrariesLoader {
	private val TAG = javaClass.simpleName
	private var loadEvent: LoadEvent<OnBotJavaClassLoader>? = null
	private val helper = OnBotJavaHelperImpl()
	fun switchLoader() {
		synchronized(this) {
			// attempt to cancel,
			// can only work if event hasn't been released
			loadEvent?.cancel()
			val loader = helper.createOnBotJavaClassLoader() as OnBotJavaClassLoader
			Notifier.notify("Staged ExternalLibraries Load")
			Logger.v(TAG, "Staged ExternalLibraries Load")
			SinisterImpl.stageLoad(loader, ExternalLibraries.getInstance().externalLibrariesNames) { it
				.afterCancel {
					Notifier.notify("Cancelled ExternalLibraries Load")
					Logger.v(TAG, "Cancelled ExternalLibraries Load")
				}
				.afterRelease {
					Notifier.notify("Processed ExternalLibraries Load")
					Logger.v(TAG, "Processed ExternalLibraries Load")
				}
				.afterUnload {
					Notifier.notify("Unloaded ExternalLibraries Load")
					Logger.v(TAG, "Unloaded ExternalLibraries Load")
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
}