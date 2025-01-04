package dev.frozenmilk.sinister.sloth

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.tasks.Jar

abstract class DexSloth : Jar() {
	@OutputDirectory
	abstract fun getOutputDir(): DirectoryProperty

	@Input
	abstract fun getDexBaseName(): Property<String>

	init {
		this.dependsOn("dexBuilderDebug")
		this.destinationDirectory.set(this.getOutputDir())
		this.archiveBaseName.set(this.getDexBaseName())
		val dexBuilderDebug = project.tasks.findByName("dexBuilderDebug")!!
		this.from(dexBuilderDebug.outputs) {
			it.exclude("*.jar")
		}
	}
}