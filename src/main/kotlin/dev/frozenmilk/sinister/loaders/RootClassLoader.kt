package dev.frozenmilk.sinister.loaders

import dev.frozenmilk.sinister.targeting.SearchTarget
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class RootClassLoader(delegate: ClassLoader) : ClassLoader(delegate) {
	private val pinnedClasses = mutableMapOf<String, Class<*>>()
	private val lock = ReentrantReadWriteLock()
	private val inclusion = SearchTarget(SearchTarget.Inclusion.INCLUDE)
	init {
		inclusion.exclude("org.firstinspires.ftc.teamcode")
	}

	fun pinned(path: String) = lock.read { pinnedClasses[path] }
	fun pin(path: String, cls: Class<*>) = lock.write { pinnedClasses[path] = cls }

	override fun loadClass(name: String, resolve: Boolean): Class<*>? =
		pinned(name) ?: run {
			if (!inclusion.determineInclusion(name)) throw ClassNotFoundException("attempted to excluded class $name")
			return super.loadClass(name, false)
		}
}