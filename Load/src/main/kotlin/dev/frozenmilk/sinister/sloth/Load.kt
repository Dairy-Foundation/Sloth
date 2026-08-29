package dev.frozenmilk.sinister.sloth

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

private const val DEX_BASE_NAME_CONVENTION = "sloth_intermediate"
private const val BUNDLE_BASE_NAME_CONVENTION = "to_load"
private const val DEPLOY_LOCATION_CONVENTION = "/storage/emulated/0/FIRST/dairy/sloth"
private const val DEX_BUILDER_TASK = "dexBuilderDebug"

class Load : Plugin<Project> {
	override fun apply(project: Project) {
		val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

		val outputDir = project.layout.buildDirectory.dir("libs")
		val adb = androidComponents.sdkComponents.adb

		val dexSloth = project.tasks.register("dexSloth", DexSloth::class.java) { task ->
			task.group = "build"

			task.destinationDirectory.set(outputDir)
			task.getDexBaseName().set(DEX_BASE_NAME_CONVENTION)

			// looked up here rather than at apply time, because agp only registers its
			// variant tasks during afterEvaluate. the provider carries the task dependency.
			task.from(project.tasks.named(DEX_BUILDER_TASK).map { it.outputs.files }) {
				it.exclude("*.jar")
			}
			task.dependsOn(DEX_BUILDER_TASK)
		}
		val assembleSloth = project.tasks.register("assembleSloth", AssembleSloth::class.java) { task ->
			task.group = "build"

			task.getOutputDir().set(outputDir)
			task.getDexBaseName().set(DEX_BASE_NAME_CONVENTION)
			task.getBundleBaseName().set(BUNDLE_BASE_NAME_CONVENTION)

			task.dependsOn(dexSloth)
		}
		project.tasks.register("deploySloth", DeploySloth::class.java) { task ->
			task.group = "install"

			task.getAdbExecutable().set(adb)
			task.getOutputDir().set(outputDir)
			task.getBundleBaseName().set(BUNDLE_BASE_NAME_CONVENTION)
			task.getDeployLocation().set(DEPLOY_LOCATION_CONVENTION)

			task.dependsOn(assembleSloth)
		}

		val removeSlothRemote = project.tasks.register("removeSlothRemote", RemoveRemoteSloth::class.java) { task ->
			task.group = "install"

			task.getAdbExecutable().set(adb)
			task.getDeployLocation().set(DEPLOY_LOCATION_CONVENTION)
		}

		// a stale sloth load on the device would shadow whatever was just installed,
		// so clear it out before either install task runs
		project.tasks
			.matching { it.name == "installDebug" || it.name == "installRelease" }
			.configureEach { it.dependsOn(removeSlothRemote) }
	}
}
