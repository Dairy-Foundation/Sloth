package dev.frozenmilk.sinister.util

import dev.frozenmilk.sinister.configurable.Configuration
import dev.frozenmilk.sinister.util.log.Logger

@Suppress("unused")
object OnBotLogger : OnBotLoggerImpl(), Configuration<Logger> {
	override val configurableClass = Logger::class.java
	override val adjacencyRule = Configuration.INDEPENDENT
	override fun configure(configurable: Logger) {
		configurable.DELEGATE = this
	}
}