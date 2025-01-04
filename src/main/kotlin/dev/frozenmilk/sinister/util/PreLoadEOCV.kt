package dev.frozenmilk.sinister.util

import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.loading.Preload

@Preload
@Suppress("unused")
object PreLoadEOCV {
	init {
		RobotLog.vv(javaClass.simpleName, "preloading EOCV")
		System.loadLibrary("EasyOpenCV")
	}
}