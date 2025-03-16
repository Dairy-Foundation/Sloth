package dev.frozenmilk.sinister.sdk

import com.qualcomm.robotcore.hardware.HardwareDevice
import dev.frozenmilk.sinister.Scanner
import dev.frozenmilk.sinister.targeting.WideSearch
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.cell.LateInitCell
import dev.frozenmilk.util.cell.MirroredCell
import org.firstinspires.ftc.robotcore.internal.opmode.BlocksClassFilter
import java.lang.reflect.Method

@Suppress("unused")
object BlocksClassScanner : Scanner {
	override val loadAdjacencyRule = Scanner.INDEPENDENT
	override val unloadAdjacencyRule = Scanner.INDEPENDENT
	override val targets = WideSearch()

	// staticMethodsByClass is used for methods with the ExportToBlocks annotation.
	private val staticMethodsByClass by MirroredCell<MutableMap<Class<*>, MutableSet<Method>>>(BlocksClassFilter.getInstance(), "staticMethodsByClass")
	// aprilTagLibraryMethodsByClass is used for methods with the ExportAprilTagLibraryToBlocks annotation.
	private val aprilTagLibraryMethodsByClass by MirroredCell<MutableMap<Class<*>, MutableSet<Method>>>(BlocksClassFilter.getInstance(), "aprilTagLibraryMethodsByClass")
	// staticMethods is used for methods with either the ExportToBlocks annotation or the ExportAprilTagLibraryToBlocks annotation.
	private val staticMethods by MirroredCell<MutableMap<String, Method>>(BlocksClassFilter.getInstance(), "staticMethods")

	private val hardwareMethodsByClass by MirroredCell<MutableMap<Class<out HardwareDevice>, MutableSet<Method>>>(BlocksClassFilter.getInstance(), "hardwareMethodsByClass")
	private val hardwareMethods by MirroredCell<MutableMap<String, Method>>(BlocksClassFilter.getInstance(), "hardwareMethods")

	// NOTE: this value is new as of 10.2.0, so atm we'll safely check for it
	private val enumClassesByEnclosingClassCell = try {
		MirroredCell<MutableMap<Class<*>, MutableSet<Class<out Enum<*>>>>>(BlocksClassFilter.getInstance(), "enumClassesByEnclosingClass")
	} catch (_: Throwable) {
		Logger.d(javaClass.simpleName, "App version < 10.2.0, fallback behaviour enabled safely")
		LateInitCell(mutableMapOf())
	}
	// enumClassesByEnclosingClass is used for enums with the ExportEnumToBlocks annotation.
	// If an enum class does not have an enclosing class, the key is the enum class itself.
	private val enumClassesByEnclosingClass by enumClassesByEnclosingClassCell

	override fun scan(loader: ClassLoader, cls: Class<*>) {
		BlocksClassFilter.getInstance().filterClass(cls)
	}

	override fun unload(loader: ClassLoader, cls: Class<*>) {
		var iter: MutableIterator<Map.Entry<Class<*>?, Set<Method?>>> =
			staticMethodsByClass.entries.iterator()
		while (iter.hasNext()) {
			val entry = iter.next()
			if (entry.key == cls) {
				for (method in entry.value) {
					staticMethods.remove(BlocksClassFilter.getLookupString(method))
				}
				iter.remove()
			}
		}
		iter = aprilTagLibraryMethodsByClass.entries.iterator()
		while (iter.hasNext()) {
			val entry: Map.Entry<Class<*>?, Set<Method?>> = iter.next()
			if (entry.key == cls) {
				for (method in entry.value) {
					staticMethods.remove(BlocksClassFilter.getLookupString(method))
				}
				iter.remove()
			}
		}
		val iter2: MutableIterator<Map.Entry<Class<out HardwareDevice?>, Set<Method>>> =
			hardwareMethodsByClass.entries.iterator()
		while (iter2.hasNext()) {
			val entry = iter2.next()
			if (entry.key == cls) {
				for (method in entry.value) {
					hardwareMethods.remove(BlocksClassFilter.getLookupString(method))
				}
				iter2.remove()
			}
		}
		val iter3: MutableIterator<Map.Entry<Class<*>, Set<Class<out Enum<*>?>>>> =
			enumClassesByEnclosingClass.entries.iterator()
		while (iter3.hasNext()) {
			val entry = iter3.next()
			if (entry.key == cls) {
				iter3.remove()
			}
		}
	}
}