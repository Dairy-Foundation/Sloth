package dev.frozenmilk.sinister.sdk.apphooks

import dev.frozenmilk.sinister.loading.Pinned
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.sdk.opmodes.OpModeScanner
import dev.frozenmilk.sinister.staticInstancesOf
import dev.frozenmilk.sinister.targeting.WideSearch
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.graph.rule.dependsOn

/**
 * a more type-safe equivalent of [com.qualcomm.robotcore.eventloop.opmode.OpModeRegistrar]
 *
 * this does not work the same way, as the systems that
 * [com.qualcomm.robotcore.eventloop.opmode.OpModeRegistrar] uses have been
 * replaced with a different, but similar system
 *
 * [com.qualcomm.robotcore.eventloop.opmode.OpModeRegistrar] is still supported via [SDKOpModeRegistrar]
 *
 * however, this is preferred over it.
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface SinisterOpModeRegistrar {
    fun registerOpModes(registrationHelper: OpModeScanner.RegistrationHelper)
}

@Suppress("unused")
object SinisterOpModeRegistrarScanner : OpModeScanner() {
    override val targets = WideSearch()
    override val loadAdjacencyRule = super.loadAdjacencyRule and dependsOn(OnCreateScanner)
    override fun scan(loader: ClassLoader, cls: Class<*>, registrationHelper: RegistrationHelper) {
        cls.staticInstancesOf(SinisterOpModeRegistrar::class.java).forEach {
            Logger.v(javaClass.simpleName, "running $it")
            it.registerOpModes(registrationHelper)
        }
    }
}
