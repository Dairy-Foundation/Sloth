package dev.frozenmilk.sinister.util

import dev.frozenmilk.sinister.configurable.Configuration
import dev.frozenmilk.sinister.util.notify.Notifier
import dev.frozenmilk.sinister.util.notify.NotifierInterface
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.firstinspires.ftc.robotcore.internal.ui.UILocation

// displays the notification in toast message
@Suppress("unused")
object OnBotNotifier : NotifierInterface, Configuration<Notifier> {
	override fun notify(message: String) {
		AppUtil.getInstance().showToast(UILocation.BOTH, message)
	}

	override val configurableClass = Notifier::class.java
	override val adjacencyRule = Configuration.INDEPENDENT
	override fun configure(configurable: Notifier) {
		configurable.DELEGATE = this
	}
}