package dev.frozenmilk.sinister.sdk.apphooks

import dev.frozenmilk.sinister.loading.Pinned
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.staticInstancesOf
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.graph.Graph
import dev.frozenmilk.util.graph.emitGraph
import dev.frozenmilk.util.graph.rule.AdjacencyRule
import dev.frozenmilk.util.graph.rule.independent
import dev.frozenmilk.util.graph.sort

/**
 * a hook that is run on startup and on every sloth load
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface OnSlothLoad {
    val adjacencyRule: AdjacencyRule<OnSlothLoad, Graph<OnSlothLoad>>
        get() = INDEPENDENT

    fun onSlothLoad()

    companion object {
        @JvmStatic
        val INDEPENDENT: AdjacencyRule<OnSlothLoad, Graph<OnSlothLoad>> = independent()
    }
}

@Suppress("unused")
object OnSlothLoadScanner : AppHookScanner<OnSlothLoad>() {
    private val TAG = javaClass.simpleName
    override fun scan(
        cls: Class<*>,
        registrationHelper: AppHookScanner<OnSlothLoad>.RegistrationHelper
    ) {
        cls.staticInstancesOf(OnSlothLoad::class.java).forEach { registrationHelper.register(it) }
        Logger.v(TAG, "Running hooks")
        try {
            val set = mutableSetOf<OnSlothLoad>()
            iterateAppHooks(set::add)
            set.emitGraph { it.adjacencyRule }.sort().forEach {
                Logger.v(TAG, "running OnSlothLoad hook $it")
                try {
                    it.onSlothLoad()
                }
                catch (e: Throwable) {
                    Logger.e(
                        TAG,
                        "something went wrong running OnSlothLoad hook $it",
                        e,
                    )
                }
            }
        }
        catch (e: Throwable) {
            Logger.e(
                TAG,
                "something went wrong running the OnSlothLoad hooks",
                e,
            )
        }
    }
}