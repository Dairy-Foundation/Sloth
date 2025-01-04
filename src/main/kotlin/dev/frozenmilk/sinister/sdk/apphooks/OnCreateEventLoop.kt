package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import com.qualcomm.ftccommon.FtcEventLoop
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
 * a more type-safe version of [org.firstinspires.ftc.ftccommon.external.OnCreateEventLoop]
 *
 * static implementations of this class will be run as [org.firstinspires.ftc.ftccommon.external.OnCreateEventLoop] methods are
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface OnCreateEventLoop {
	val adjacencyRule: AdjacencyRule<OnCreateEventLoop, Graph<OnCreateEventLoop>>
		get() = INDEPENDENT

	fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop)

	class SDKMethod internal constructor(val method: Method) : OnCreateEventLoop {
		override fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
			method.invoke(null, context, ftcEventLoop)
		}
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<OnCreateEventLoop, Graph<OnCreateEventLoop>> = independent()
	}
}

@Suppress("unused")
object OnCreateEventLoopScanner : AppHookScanner<OnCreateEventLoop>() {
	override fun scan(cls: Class<*>, registrationHelper: RegistrationHelper) {
		cls.staticInstancesOf(OnCreateEventLoop::class.java).forEach { registrationHelper.register(it) }
		cls.declaredMethods
			.filter {
				it.isStatic()
						&& it.isPublic()
						&& it.isAnnotationPresent(org.firstinspires.ftc.ftccommon.external.OnCreateEventLoop::class.java)
						&& it.parameterCount == 2
						&& it.parameterTypes[0] == Context::class.java
						&& it.parameterTypes[1] == FtcEventLoop::class.java
						&& it.declaringClass != CALLSITE::class.java
			}
			.forEach {
				registrationHelper.register(OnCreateEventLoop.SDKMethod(it))
			}
	}

	/**
	 * prevents [onCreateEventLoop] from being publicly exposed
	 */
	@Preload
	private object CALLSITE {
		init {
			RobotLog.dd(javaClass.enclosingClass.simpleName, "Replacing OnCreateEventLoop hooks with shim")
			javaClass.getDeclaredMethod("onCreateEventLoop", Context::class.java, FtcEventLoop::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("onCreateEventLoopMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
		}
		@JvmStatic
		@org.firstinspires.ftc.ftccommon.external.OnCreateEventLoop
		fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
			val set = mutableSetOf<OnCreateEventLoop>()
			iterateAppHooks(set::add)
			set.emitGraph { it.adjacencyRule }.sort().forEach {
				it.onCreateEventLoop(context, ftcEventLoop)
			}
		}
	}
}