package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import com.qualcomm.robotcore.eventloop.opmode.AnnotatedOpModeManager
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.OpModeManager
import com.qualcomm.robotcore.eventloop.opmode.OpModeRegistrar
import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.Scanner
import dev.frozenmilk.sinister.isPublic
import dev.frozenmilk.sinister.isStatic
import dev.frozenmilk.sinister.sdk.opmodes.OpModeScanner
import dev.frozenmilk.sinister.sdk.opmodes.SinisterRegisteredOpModes
import dev.frozenmilk.sinister.sdk.opmodes.TeleopAutonomousOpModeScanner
import dev.frozenmilk.sinister.targeting.WideSearch
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import org.firstinspires.ftc.robotcore.internal.system.AppUtil

// note that this doesn't use the AppHookScanner, as it needs a more special registration helper
// note that the SinisterOpModeRegistrar is better than this, and can do the same stuff
@Suppress("unused")
object SDKOpModeRegistrar : Scanner {
    override val loadAdjacencyRule = afterConfiguration()
	override val unloadAdjacencyRule = beforeConfiguration()

    //
    // State
    //

    private val TAG = javaClass.simpleName
    private val found = mutableMapOf<ClassLoader, MutableList<OpModeMeta>>()
    private var registrationHelper: DynamicAnnotatedOpModeManager? = null

    //
    // Scanner
    //

    override val targets = WideSearch()

    override fun beforeScan(loader: ClassLoader) {
        registrationHelper = DynamicAnnotatedOpModeManager(
            found.getOrPut(loader) { mutableListOf() }
        )
    }
    override fun scan(loader: ClassLoader, cls: Class<*>) {
        cls.declaredMethods
            .filter { it.isStatic()
                    && it.isPublic()
                    && it.isAnnotationPresent(OpModeRegistrar::class.java)
                    && ((it.parameterCount == 1 && OpModeManager::class.java.isAssignableFrom(it.parameterTypes[0]))
                    || (it.parameterCount == 2 && it.parameterTypes[0] == Context::class.java && OpModeManager::class.java.isAssignableFrom(it.parameterTypes[1]))) }
            .onEach { it.isAccessible = true }
            .forEach {
                try {
                    if (it.parameterCount == 1) it.invoke(null, registrationHelper!!)
                    else if (it.parameterCount == 2) it.invoke(null,
						AppUtil.getDefContext(), registrationHelper!!)
                }
                catch (e: Throwable) {
					RobotLog.setGlobalErrorMsg("something went wrong running a method annotated with @OpModeRegistrar, check the tag \"$TAG\" in the log for more details")
					RobotLog.ee(
						TAG,
						"something went wrong running a method annotated with @OpModeRegistrar",
						e
					)
                }
            }
    }
    override fun afterScan(loader: ClassLoader) {
        registrationHelper = null
    }

    override fun unload(loader: ClassLoader, cls: Class<*>) {}
    override fun afterUnload(loader: ClassLoader) {
        found.remove(loader)?.forEach {
			SinisterRegisteredOpModes.unregister(
				it
			)
		}
    }

    //
    // AnnotatedOpModeManager
    //

    private class DynamicAnnotatedOpModeManager(metas: MutableList<OpModeMeta>) : OpModeScanner.RegistrationHelper(metas),
		AnnotatedOpModeManager {
        override fun register(opModeClass: Class<*>) {
            val (meta, error) = TeleopAutonomousOpModeScanner.metaForClass(opModeClass) // no meta extractable, we are going to ignore these errors
            if (error != null) {
				RobotLog.e("OpMode Configuration Error:\n$error")
				RobotLog.setGlobalErrorMsg(error)
                return
            }
            if (meta == null) return
            @Suppress("UNCHECKED_CAST")
            register(meta, opModeClass as Class<out OpMode>)
        }

        override fun register(name: String, opModeClass: Class<out OpMode>) {
            TeleopAutonomousOpModeScanner.checkOpModeClass(opModeClass)?.let {
                RobotLog.e(it)
				RobotLog.setGlobalErrorMsg(it)
                return
            }
            if (!OpModeMeta.nameIsLegalForOpMode(name, false)) {
				RobotLog.e("\"$name\" is not a legal OpMode name")
				RobotLog.setGlobalErrorMsg("\"$name\" is not a legal OpMode name")
                return
            }
            register(OpModeMeta.Builder().setName(name).build(), opModeClass)
        }

        override fun register(name: String, opModeInstance: OpMode) {
            if (!OpModeMeta.nameIsLegalForOpMode(name, false)) {
				RobotLog.e("\"$name\" is not a legal OpMode name")
				RobotLog.setGlobalErrorMsg("\"$name\" is not a legal OpMode name")
                return
            }
            register(OpModeMeta.Builder().setName(name).build(), opModeInstance)
        }
    }
}