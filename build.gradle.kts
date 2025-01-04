plugins {
	id("dev.frozenmilk.android-library") version "10.2.0-0.1.3"
	id("dev.frozenmilk.publish") version "0.0.4"
	id("dev.frozenmilk.doc") version "0.0.4"
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
	api("dev.frozenmilk:Sinister:2.1.0")
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
