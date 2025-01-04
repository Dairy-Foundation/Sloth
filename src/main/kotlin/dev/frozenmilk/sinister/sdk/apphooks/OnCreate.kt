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
 * a more type-safe version of [org.firstinspires.ftc.ftccommon.external.OnCreate]
 *
 * static implementations of this class will be run as [org.firstinspires.ftc.ftccommon.external.OnCreate] methods are
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface OnCreate {
	val adjacencyRule: AdjacencyRule<OnCreate, Graph<OnCreate>>
		get() = INDEPENDENT

	/**
	 * provides an easy way to perform initialization when the robot controller activity is created.
	 *
	 * @see org.firstinspires.ftc.ftccommon.external.OnCreate
	 */
	fun onCreate(context: Context)

	class SDKMethod internal constructor(val method: Method) : OnCreate {
		override fun onCreate(context: Context) {
			method.invoke(null, context)
		}
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<OnCreate, Graph<OnCreate>> = independent()
	}
}

@Suppress("unused")
object OnCreateScanner : AppHookScanner<OnCreate>() {
	override fun scan(cls: Class<*>, registrationHelper: RegistrationHelper) {
		cls.staticInstancesOf(OnCreate::class.java).forEach { registrationHelper.register(it) }
		cls.declaredMethods
			.filter {
				it.isStatic()
						&& it.isPublic()
						&& it.isAnnotationPresent(org.firstinspires.ftc.ftccommon.external.OnCreate::class.java)
						&& it.parameterCount == 1
						&& it.parameterTypes[0] == Context::class.java
						&& it.declaringClass != CALLSITE::class.java
			}
			.forEach {
				registrationHelper.register(OnCreate.SDKMethod(it))
			}
	}

	/**
	 * prevents [onCreate] from being publicly exposed
	 *
	 * note: this CALLSITE is special in comparison to others, it must be run by the SinisterRuntime
	 */
	internal object CALLSITE : OnCreateEventLoop {
		/**
		 * if true, means that this system is entirely in charge of
		 * `@OnCreate` methods, and will run [OnCreate.SDKMethod]s.
		 * else, if false, means that the SDK is still running `@OnCreate` methods,
		 * which we can't interrupt, so don't run them
		 */
		private var sdkOverpowered = false

		/**
		 * not be called except by the sinister runtime
		 */
		@JvmStatic
		fun onCreate(context: Context) {
			val set = mutableSetOf<OnCreate>()
			iterateAppHooks(set::add)
			if (!sdkOverpowered) set.removeIf { it is OnCreate.SDKMethod }
			set.emitGraph { it.adjacencyRule }.sort().forEach {
				it.onCreate(context)
			}
		}

		override fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
			RobotLog.dd(javaClass.enclosingClass.simpleName, "Replacing OnCreate hooks with shim")
			javaClass.getDeclaredMethod("onCreate", Context::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("onCreateMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
			sdkOverpowered = true
		}
	}
}
