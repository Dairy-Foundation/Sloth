package dev.frozenmilk.sinister.sloth

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecException
import java.io.ByteArrayOutputStream

abstract class RemoveRemoteSloth : DefaultTask() {
	@InputFile
	abstract fun getAdbExecutable(): RegularFileProperty

	@Input
	abstract fun getDeployLocation(): Property<String>

	@TaskAction
	fun execute() {
		val stdErr = ByteArrayOutputStream()
		project.exec {
			it.commandLine(
				getAdbExecutable().get().asFile.absolutePath,
				"shell",
				"rm -f ${getDeployLocation().get()}/loaded.jar"
			)
			it.isIgnoreExitValue = true
			it.errorOutput = stdErr
		}.also {
			val err = stdErr.toByteArray().toString(Charsets.UTF_8)
			if (it.exitValue != 0) throw ExecException(err)
		}
	}
}