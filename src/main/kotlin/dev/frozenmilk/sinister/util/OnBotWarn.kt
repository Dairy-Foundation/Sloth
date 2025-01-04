package dev.frozenmilk.sinister.util

import dev.frozenmilk.sinister.configurable.Configuration
import dev.frozenmilk.sinister.util.warn.Warn

@Suppress("unused")
object OnBotWarn : OnBotWarnImpl(), Configuration<Warn> {
	override val configurableClass = Warn::class.java
	override val adjacencyRule = Configuration.INDEPENDENT
	override fun configure(configurable: Warn) {
		configurable.DELEGATE = this
	}
}
