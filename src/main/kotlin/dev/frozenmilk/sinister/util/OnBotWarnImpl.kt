package dev.frozenmilk.sinister.util

import com.qualcomm.robotcore.util.GlobalWarningSource
import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.util.warn.WarnImpl

open class OnBotWarnImpl : WarnImpl() {
	init {
		RobotLog.registerGlobalWarningSource(
			object : GlobalWarningSource {
				override fun getGlobalWarning() = this@OnBotWarnImpl.warning
				override fun shouldTriggerWarningSound() = false
				override fun suppressGlobalWarning(suppress: Boolean) {}
				override fun setGlobalWarning(warning: String?) {}
				override fun clearGlobalWarning() {}
			}
		)
	}
}