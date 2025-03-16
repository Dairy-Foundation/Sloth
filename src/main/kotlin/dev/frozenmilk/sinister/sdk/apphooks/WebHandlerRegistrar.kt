package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import com.qualcomm.robotcore.util.WebHandlerManager
import dev.frozenmilk.sinister.sdk.apphooks.WebHandlerRegistrarScanner.CALLSITE.webHandlerRegistrar
import dev.frozenmilk.sinister.isPublic
import dev.frozenmilk.sinister.isStatic
import dev.frozenmilk.sinister.loading.Pinned
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.sdk.FalseSingletonSet
import dev.frozenmilk.sinister.staticInstancesOf
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.graph.Graph
import dev.frozenmilk.util.graph.emitGraph
import dev.frozenmilk.util.graph.rule.AdjacencyRule
import dev.frozenmilk.util.graph.rule.independent
import dev.frozenmilk.util.graph.sort
import org.firstinspires.ftc.ftccommon.internal.AnnotatedHooksClassFilter
import java.lang.reflect.Method

/**
 * a more type-safe version of [org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar]
 *
 * static implementations of this class will be run as [org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar] methods are
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface WebHandlerRegistrar {
	val adjacencyRule: AdjacencyRule<WebHandlerRegistrar, Graph<WebHandlerRegistrar>>
		get() = INDEPENDENT

	fun webHandlerRegistrar(context: Context, webHandlerManager: WebHandlerManager)

	class SDKMethod internal constructor(val method: Method) : WebHandlerRegistrar {
		override fun webHandlerRegistrar(context: Context, webHandlerManager: WebHandlerManager) {
			method.invoke(null, context, webHandlerManager)
		}

		override fun toString() = method.toString()
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<WebHandlerRegistrar, Graph<WebHandlerRegistrar>> = independent()
	}
}

@Suppress("unused")
object WebHandlerRegistrarScanner : AppHookScanner<WebHandlerRegistrar>() {
	private val TAG = javaClass.simpleName
	override fun scan(cls: Class<*>, registrationHelper: RegistrationHelper) {
		cls.staticInstancesOf(WebHandlerRegistrar::class.java).forEach { registrationHelper.register(it) }
		cls.declaredMethods
			.filter {
				it.isStatic()
						&& it.isPublic()
						&& it.isAnnotationPresent(org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar::class.java)
						&& it.parameterCount == 2
						&& it.parameterTypes[0] == Context::class.java
						&& it.parameterTypes[1] == WebHandlerManager::class.java
						&& it.declaringClass != CALLSITE::class.java
			}
			.forEach {
				registrationHelper.register(WebHandlerRegistrar.SDKMethod(it))
			}
	}

	/**
	 * prevents [webHandlerRegistrar] from being exposed publicly
	 */
	@Preload
	private object CALLSITE {
		init {
			Logger.v(TAG, "Replacing WebHandlerRegistrar hooks with shim")
			javaClass.getDeclaredMethod("webHandlerRegistrar", Context::class.java, WebHandlerManager::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("webHandlerRegistrarMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
		}

		@JvmStatic
		@org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
		fun webHandlerRegistrar(context: Context, webHandlerManager: WebHandlerManager) {
			try {
				Logger.v(TAG, "Running Hooks, params: $context, $webHandlerManager")
				val set = mutableSetOf<WebHandlerRegistrar>()
				iterateAppHooks(set::add)
				set.emitGraph { it.adjacencyRule }.sort().forEach {
					Logger.v(TAG, "running WebHandlerRegistrar hook $it")
					try {
						it.webHandlerRegistrar(context, webHandlerManager)
					}
					catch (e: Throwable) {
						Logger.e(
							TAG,
							"something went wrong running WebHandlerRegistrar hook $it",
							e,
						)
					}
				}
			}
			catch (e: Throwable) {
				Logger.e(
					TAG,
					"something went wrong running the WebHandlerRegistrar hooks",
					e,
				)
			}
		}
	}
}