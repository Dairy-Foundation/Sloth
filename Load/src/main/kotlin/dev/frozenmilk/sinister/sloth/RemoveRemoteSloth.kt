package dev.frozenmilk.sinister.sloth

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class RemoveRemoteSloth @Inject constructor(private var execOperations: ExecOperations) :
    DefaultTask() {
    @InputFile
    abstract fun getAdbExecutable(): RegularFileProperty

    @Input
    abstract fun getDeployLocation(): Property<String>

    @Input
    abstract fun getSettings(): Property<LoadSettings>

    @TaskAction
    fun execute() {
        execOperations.exec {
            it.commandLine(
                getAdbExecutable().get().asFile.absolutePath,
                "shell",
                "rm -rf ${getDeployLocation().get()}/*"
            )
            it.isIgnoreExitValue = true
        }
    }
}