package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import com.qualcomm.robotcore.util.RobotLog
import com.qualcomm.robotcore.util.WebHandlerManager
import dev.frozenmilk.sinister.sdk.apphooks.WebHandlerRegistrarScanner.CALLSITE.webHandlerRegistrar
import dev.frozenmilk.sinister.isPublic
import dev.frozenmilk.sinister.isStatic
import dev.frozenmilk.sinister.loading.Pinned
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.sdk.FalseSingletonSet
import dev.frozenmilk.sinister.staticInstancesOf
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
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<WebHandlerRegistrar, Graph<WebHandlerRegistrar>> = independent()
	}
}

@Suppress("unused")
object WebHandlerRegistrarScanner : AppHookScanner<WebHandlerRegistrar>() {
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
			RobotLog.dd(javaClass.enclosingClass.simpleName, "Replacing WebHandlerRegistrar hooks with shim")
			javaClass.getDeclaredMethod("webHandlerRegistrar", Context::class.java, WebHandlerManager::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("webHandlerRegistrarMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
		}

		@JvmStatic
		@org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
		fun webHandlerRegistrar(context: Context, webHandlerManager: WebHandlerManager) {
			val set = mutableSetOf<WebHandlerRegistrar>()
			iterateAppHooks(set::add)
			set.emitGraph { it.adjacencyRule }.sort().forEach {
				it.webHandlerRegistrar(context, webHandlerManager)
			}
		}
	}
}