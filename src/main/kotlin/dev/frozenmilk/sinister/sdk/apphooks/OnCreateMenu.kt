package dev.frozenmilk.sinister.sdk.apphooks

import android.content.Context
import android.view.Menu
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
 * a more type-safe version of [org.firstinspires.ftc.ftccommon.external.OnCreateMenu]
 *
 * static implementations of this class will be run as [org.firstinspires.ftc.ftccommon.external.OnCreateMenu] methods are
 */
@Preload
@Pinned
@FunctionalInterface
@JvmDefaultWithoutCompatibility
fun interface OnCreateMenu {
	val adjacencyRule: AdjacencyRule<OnCreateMenu, Graph<OnCreateMenu>>
		get() = INDEPENDENT

	fun onCreateMenu(context: Context, menu: Menu)

	class SDKMethod internal constructor(val method: Method) : OnCreateMenu {
		override fun onCreateMenu(context: Context, menu: Menu) {
			method.invoke(null, context, menu)
		}

		override fun toString() = method.toString()
	}

	companion object {
		@JvmStatic
		val INDEPENDENT: AdjacencyRule<OnCreateMenu, Graph<OnCreateMenu>> = independent()
	}
}

@Suppress("unused")
object OnCreateMenuScanner : AppHookScanner<OnCreateMenu>() {
	private val TAG = javaClass.simpleName
	override fun scan(cls: Class<*>, registrationHelper: RegistrationHelper) {
		cls.staticInstancesOf(OnCreateMenu::class.java).forEach { registrationHelper.register(it) }
		cls.declaredMethods
			.filter {
				it.isStatic()
						&& it.isPublic()
						&& it.isAnnotationPresent(org.firstinspires.ftc.ftccommon.external.OnCreateMenu::class.java)
						&& it.parameterCount == 2
						&& it.parameterTypes[0] == Context::class.java
						&& it.parameterTypes[1] == Menu::class.java
						&& it.declaringClass != CALLSITE::class.java
			}
			.forEach {
				registrationHelper.register(OnCreateMenu.SDKMethod(it))
			}
	}

	/**
	 * prevents [onCreateMenu] from being publicly exposed
	 */
	@Preload
	private object CALLSITE {
		init {
			Logger.d(TAG, "Replacing OnCreateMenu hooks with shim")
			javaClass.getDeclaredMethod("onCreateMenu", Context::class.java, Menu::class.java).let {
				AnnotatedHooksClassFilter::class.java.getDeclaredField("onCreateMenuMethods").apply {
					isAccessible = true
				}.set(AnnotatedHooksClassFilter.getInstance(), FalseSingletonSet(it))
			}
		}

		@JvmStatic
		@org.firstinspires.ftc.ftccommon.external.OnCreateMenu
		fun onCreateMenu(context: Context, menu: Menu) {
			Logger.v(TAG, "Running Hooks, params: $context, $menu")
			try {
				val set = mutableSetOf<OnCreateMenu>()
				iterateAppHooks(set::add)
				set.emitGraph { it.adjacencyRule }.sort().forEach {
					Logger.v(TAG, "running OnCreateMenu hook $it")
					try {
						it.onCreateMenu(context, menu)
					}
					catch (e: Throwable) {
						Logger.e(
							TAG,
							"something went wrong running OnCreateMenu hook $it",
							e,
						)
					}
				}
			}
			catch (e: Throwable) {
				Logger.e(
					TAG,
					"something went wrong running the OnCreateMenu hooks",
					e,
				)
			}
		}
	}
}