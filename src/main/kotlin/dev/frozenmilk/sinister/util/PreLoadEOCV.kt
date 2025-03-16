package dev.frozenmilk.sinister.util

import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.util.log.Logger

@Preload
@Suppress("unused")
object PreLoadEOCV {
	init {
		Logger.v(javaClass.simpleName, "preloading EOCV")
		System.loadLibrary("EasyOpenCV")
	}
}