package dev.frozenmilk.sinister.loaders

import dalvik.system.PathClassLoader
import dev.frozenmilk.sinister.SinisterImpl

class SlothClassLoader(val path: String, librarySearchPath: String, private val delegate: RootClassLoader, val classes: List<String>) : PathClassLoader(path, librarySearchPath, delegate) {
	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		return delegate.pinned(path) ?: run {
			if (!SinisterImpl.teamCodeSearch.determineInclusion(name)) parent.loadClass(name)
			return super.loadClass(name, resolve)
		}
	}
}