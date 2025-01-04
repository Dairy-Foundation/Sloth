package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import com.qualcomm.robotcore.util.RobotLog
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
 * a more type-safe version of [org.firstinspires.ftc.ftccommon.external.OnDestroy]
 *
 * static implementations of this class will be run as [org.firstinspires.ftc.ftccommon.external.OnDestroy] methods are
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface OnDestroy {
	val adjacencyRule: AdjacencyRule<OnDestroy, Graph<OnDestroy>>
		get() = INDEPENDENT

	fun onDestroy(context: Context)

	class SDKMethod internal constructor(val method: Method) : OnDestroy {
		override fun onDestroy(context: Context) {
			method.invoke(null, context)
		}
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<OnDestroy, Graph<OnDestroy>> = independent()
	}
}

@Suppress("unused")
object OnDestroyScanner : AppHookScanner<OnDestroy>() {
	override fun scan(cls: Class<*>, registrationHelper: RegistrationHelper) {
		cls.staticInstancesOf(OnDestroy::class.java).forEach { registrationHelper.register(it) }
		cls.declaredMethods
			.filter {
				it.isStatic()
						&& it.isPublic()
						&& it.isAnnotationPresent(org.firstinspires.ftc.ftccommon.external.OnDestroy::class.java)
						&& it.parameterCount == 1
						&& it.parameterTypes[0] == Context::class.java
						&& it.declaringClass != CALLSITE::class.java
			}
			.forEach {
				registrationHelper.register(OnDestroy.SDKMethod(it))
			}
	}

	/**
	 * prevents [onDestroy] from being publicly exposed
	 */
	@Preload
	private object CALLSITE {
		init {
			RobotLog.dd(javaClass.enclosingClass.simpleName, "Replacing OnDestroy hooks with shim")
			javaClass.getDeclaredMethod("onDestroy", Context::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("onDestroyMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
		}
		@JvmStatic
		@org.firstinspires.ftc.ftccommon.external.OnDestroy
		fun onDestroy(context: Context) {
			val set = mutableSetOf<OnDestroy>()
			iterateAppHooks(set::add)
			set.emitGraph { it.adjacencyRule }.sort().forEach {
				it.onDestroy(context)
			}
		}
	}
}