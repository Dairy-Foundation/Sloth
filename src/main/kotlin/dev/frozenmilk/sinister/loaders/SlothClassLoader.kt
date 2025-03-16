@file:Suppress("Deprecation")
package dev.frozenmilk.sinister.loaders

import dalvik.system.PathClassLoader
import dev.frozenmilk.sinister.openDex
import dev.frozenmilk.sinister.targeting.SearchTarget
import java.io.File

class SlothClassLoader(val path: String, librarySearchPath: String, private val inclusion: SearchTarget, private val delegate: RootClassLoader) : PathClassLoader(path, librarySearchPath, delegate) {
	constructor(path: String, librarySearchPath: String, delegate: RootClassLoader) : this(path, librarySearchPath, SearchTarget(SearchTarget.Inclusion.INCLUDE), delegate)
	val classes = run {
		val file = openDex(File(path), 5)
		val res = file.entries()
			.asSequence()
			.filter { inclusion.determineInclusion(it) && delegate.pinned(it) == null }
			.toList()
		file.close()
		res
	}
	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		return delegate.pinned(path) ?: run {
			if (!inclusion.determineInclusion(name)) parent.loadClass(name)
			return super.loadClass(name, resolve)
		}
	}
}