package dev.frozenmilk.sinister.util

import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.util.log.LoggerInterface

open class OnBotLoggerImpl : LoggerInterface {
	override fun v(tag: String?, msg: String) {
		RobotLog.vv(tag, msg)
	}
	override fun v(tag: String?, msg: String, tr: Throwable) {
		RobotLog.vv(tag, tr, msg)
	}

	override fun d(tag: String?, msg: String) {
		RobotLog.dd(tag, msg)
	}
	override fun d(tag: String?, msg: String, tr: Throwable) {
		RobotLog.dd(tag, tr, msg)
	}

	override fun i(tag: String?, msg: String) {
		RobotLog.ii(tag, msg)
	}
	override fun i(tag: String?, msg: String, tr: Throwable) {
		RobotLog.ii(tag, tr, msg)
	}

	override fun w(tag: String?, msg: String) {
		RobotLog.ww(tag, msg)
	}
	override fun w(tag: String?, msg: String, tr: Throwable) {
		RobotLog.ww(tag, tr, msg)
	}

	override fun e(tag: String?, msg: String) {
		RobotLog.ee(tag, msg)
	}
	override fun e(tag: String?, msg: String, tr: Throwable) {
		RobotLog.ee(tag, tr, msg)
	}
}