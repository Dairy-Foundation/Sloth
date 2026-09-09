package dev.frozenmilk.sinister

import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.sinister.util.notify.Notifier
import org.firstinspires.ftc.onbotjava.OnBotJavaClassLoader
import org.firstinspires.ftc.onbotjava.OnBotJavaHelperImpl
import org.firstinspires.ftc.onbotjava.OnBotJavaManager
import java.util.zip.ZipFile

@Suppress("unused")
object OnBotJavaLoader {
    private val TAG = javaClass.simpleName
    private var loadEvent: LoadEvent<OnBotJavaClassLoader>? = null
    private val helper = OnBotJavaHelperImpl()

    init {
        switchLoader()
    }

    fun switchLoader() {
        synchronized(this) {
            // attempt to cancel,
            // can only work if event hasn't been released
            val oldLoadEvent = loadEvent?.cancel()
            val loader = helper.createOnBotJavaClassLoader() as OnBotJavaClassLoader
            val classes = run {
                val files = OnBotJavaManager.getOutputJarFiles()
                files.map { ZipFile(it) }.flatMap { file ->
                    file
                        .entries()
                        .asSequence()
                        .filter { !it.isDirectory }
                        .map { it.name }
                        .filter { ZipUtils.isClassFile(it) }
                        .map { it.substring(0, it.length - ".class".length) }
                        .toList().also {
                            file.close()
                        }
                }
            }
            Notifier.notify("Staged OnBotJava Load")
            Logger.v(TAG, "Staged OnBotJava Load")
            SinisterImpl.stageLoad(oldLoadEvent, loader, classes) { newLoadEvent ->
                newLoadEvent.afterCancel {
                    Notifier.notify("Cancelled OnBotJava Load")
                    Logger.v(TAG, "Cancelled OnBotJava Load")
                }
                newLoadEvent.afterRelease {
                    Notifier.notify("Processed OnBotJava Load")
                    Logger.v(TAG, "Processed OnBotJava Load")
                }
                newLoadEvent.afterUnload {
                    Notifier.notify("Unloaded OnBotJava Load")
                    Logger.v(TAG, "Unloaded OnBotJava Load")
                }
                oldLoadEvent?.let { oldLoadEvent ->
                    newLoadEvent.beforeRelease {
                        // attempt to unload
                        oldLoadEvent.unload()
                    }
                }
                loadEvent = newLoadEvent
            }
        }
    }
}