package dev.frozenmilk.sinister.sloth

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.jvm.tasks.Jar

/**
 * bundles the dex output of the android build into a jar for [AssembleSloth] to merge
 *
 * the inputs are wired lazily by [Load], so that applying this plugin does not force
 * every task in the project to be realised at configuration time
 */
abstract class DexSloth : Jar() {
	@Input
	abstract fun getDexBaseName(): Property<String>

	init {
		this.archiveBaseName.set(this.getDexBaseName())
	}
}
