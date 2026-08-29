package dev.frozenmilk.sinister.sloth

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class RemoveRemoteSloth @Inject constructor (private var execOperations: ExecOperations): DefaultTask() {
	@InputFile
	abstract fun getAdbExecutable(): RegularFileProperty

	@Input
	abstract fun getDeployLocation(): Property<String>

	@TaskAction
	fun execute() {
		val stdErr = ByteArrayOutputStream()
		execOperations.exec {
			it.commandLine(
				getAdbExecutable().get().asFile.absolutePath,
				"shell",
				"rm -f ${getDeployLocation().get()}/loaded.jar"
			)
			it.isIgnoreExitValue = true
			it.errorOutput = stdErr
		}.also {
			val err = stdErr.toByteArray().toString(Charsets.UTF_8)
			if (it.exitValue != 0) throw GradleException(err)
		}
	}
}