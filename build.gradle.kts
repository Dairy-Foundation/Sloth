plugins {
	id("dev.frozenmilk.android-library") version "10.3.0-0.1.4"
	id("dev.frozenmilk.publish") version "0.0.5"
	id("dev.frozenmilk.doc") version "0.0.5"
	id("dev.frozenmilk.build-meta-data") version "0.0.1"
}

repositories {
	maven {
		name = "dairyReleases"
		url = uri("https://repo.dairy.foundation/releases")
	}
}

android.namespace = "dev.frozenmilk.sinister"

ftc {
	kotlin

	sdk {
		RobotCore
		FtcCommon {
			configurationNames += "testImplementation"
		}
		Hardware
		OnBotJava
		Blocks
	}
}

dependencies {
	api("dev.frozenmilk:Sinister:2.2.0")
}

meta {
	packagePath = "dev.frozenmilk.sinister"
	name = "Sloth"
	registerField("name", "String", "\"dev.frozenmilk.sinister.Sloth\"")
	registerField("clean", "Boolean") { "${dairyPublishing.clean}" }
	registerField("gitRef", "String") { "\"${dairyPublishing.gitRef}\"" }
	registerField("snapshot", "Boolean") { "${dairyPublishing.snapshot}" }
	registerField("version", "String") { "\"${dairyPublishing.version}\"" }
}

publishing {
	publications {
		register<MavenPublication>("release") {
			groupId = "dev.frozenmilk.sinister"
			artifactId = "Sloth"

			artifact(dairyDoc.dokkaHtmlJar)
			artifact(dairyDoc.dokkaJavadocJar)

			afterEvaluate {
				from(components["release"])
			}
		}
	}
}
