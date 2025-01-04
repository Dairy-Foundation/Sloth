package dev.frozenmilk.sinister.sloth

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecException
import java.io.ByteArrayOutputStream

/**
 * Uses ADB to copy the merged dex jar to the robot controller.
 */
abstract class DeploySloth : DefaultTask() {
	@InputDirectory
	abstract fun getOutputDir(): DirectoryProperty

	@Input
	abstract fun getBundleBaseName(): Property<String>

	@InputFile
	abstract fun getAdbExecutable(): RegularFileProperty

	@Input
	abstract fun getDeployLocation(): Property<String>

	@TaskAction
	fun execute() {
		var stdErr = ByteArrayOutputStream()
		project.exec {
			it.commandLine(
				getAdbExecutable().get().asFile.absolutePath,
				"shell",
				"test ! -f ${getDeployLocation().get()}/lock",
			)
			it.isIgnoreExitValue = true
			it.errorOutput = stdErr
		}.also {
			val err = stdErr.toByteArray().toString(Charsets.UTF_8)
			if (err.isNotBlank()) throw ExecException(err)
			if (it.exitValue != 0) throw IllegalStateException(
				"Detected lock file for Sloth loads.\n" +
				"This may have been thrown if a Sloth load was still in process.\n" +
				"If you suspect that this shouldn't have happened, please report the issue, and restart your robot.\n" +
				"This will remove the file and allow you to use Sloth."
			)
		}
		println("checked for lock file")

		stdErr = ByteArrayOutputStream()
		project.exec {
			it.commandLine(
				getAdbExecutable().get().asFile.absolutePath,
				"push",
				getOutputDir().file("${getBundleBaseName().get()}.jar").get().asFile.absolutePath,
				getDeployLocation().get(),
			)
			it.isIgnoreExitValue = true
			it.errorOutput = stdErr
		}.also {
			val err = stdErr.toByteArray().toString(Charsets.UTF_8)
			if (it.exitValue != 0) throw ExecException(err)
		}
		println("pushed jar")

		println("waiting for lock file to be removed")
		while (true) {
			stdErr = ByteArrayOutputStream()
			val finished = project.exec {
				it.commandLine(
					getAdbExecutable().get().asFile.absolutePath,
					"shell",
					"test ! -f ${getDeployLocation().get()}/lock",
				)
				it.isIgnoreExitValue = true
				it.errorOutput = stdErr
			}.let {
				val err = stdErr.toByteArray().toString(Charsets.UTF_8)
				if (err.isNotBlank()) throw ExecException(err)
				it.exitValue == 0
			}
			if (finished) break
		}
	}
}