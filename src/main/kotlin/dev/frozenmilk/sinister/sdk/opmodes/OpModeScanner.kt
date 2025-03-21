package dev.frozenmilk.sinister.sdk.opmodes

import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.Scanner
import dev.frozenmilk.sinister.sdk.apphooks.SDKOpModeRegistrar
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import java.util.function.Supplier

/**
 * this is a utility class for a Scanner that registers opmodes in some fashion
 * (usually detecting annotations on classes, or calling static methods, etc)
 *
 * all scanners that will register an opmode should extend this if possible, with the exemption of
 * [SDKOpModeRegistrar], which supports legacy sdk systems.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class OpModeScanner : Scanner {
    override val loadAdjacencyRule = Scanner.INDEPENDENT
    override val unloadAdjacencyRule = Scanner.INDEPENDENT

    //
    // State
    //

    private val found = mutableMapOf<ClassLoader, MutableList<OpModeMeta>>()
    private var registrationHelper: RegistrationHelper? = null

    //
    // Registration
    //

    open class RegistrationHelper(protected val metas: MutableList<OpModeMeta>) {
        fun register(meta: OpModeMeta, supplier: Supplier<out OpMode>) {
            SinisterRegisteredOpModes.register(meta, supplier)
            metas.add(meta)
        }

        /**
         * this method can only be used on a class that extends [OpMode]
         *
         * use [register] with a supplier instead if you need to do something more complex
         *
         * which this calls anyway
         */
        fun register(meta: OpModeMeta, cls: Class<out OpMode>)  {
            TeleopAutonomousOpModeScanner.checkOpModeClass(cls)?.let {
                Logger.e(TeleopAutonomousOpModeScanner.javaClass.simpleName, it)
                RobotLog.setGlobalErrorMsg(it)
                return
            }
            register(meta) { cls.newInstance() }
        }
        fun register(meta: OpModeMeta, instance: OpMode) = register(meta) { instance }

        fun unregister(meta: OpModeMeta) = SinisterRegisteredOpModes.unregister(meta)
    }

    //
    // Scanner
    //

    final override fun beforeScan(loader: ClassLoader) {
        registrationHelper = RegistrationHelper(
            found.getOrPut(loader) { mutableListOf() }
        )
    }

    final override fun scan(loader: ClassLoader, cls: Class<*>) = scan(loader, cls, registrationHelper!!)
    final override fun afterScan(loader: ClassLoader) {
        registrationHelper = null
    }

    final override fun beforeUnload(loader: ClassLoader) {}
    final override fun unload(loader: ClassLoader, cls: Class<*>) {}
    final override fun afterUnload(loader: ClassLoader) {
        found.remove(loader)?.forEach { SinisterRegisteredOpModes.unregister(it) }
    }

    protected abstract fun scan(loader: ClassLoader, cls: Class<*>, registrationHelper: RegistrationHelper)
}