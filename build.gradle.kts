plugins {
	id("dev.frozenmilk.android-library") version "12.0.0-1.2.1"
	id("dev.frozenmilk.publish") version "0.1.0"
	id("dev.frozenmilk.doc") version "0.1.0"
	id("dev.frozenmilk.build-meta-data") version "0.1.0"
}

android.namespace = "dev.frozenmilk.sinister"

ftc {
	kotlin()

	sdk {
		compileOnly(RobotCore)
		compileOnly(FtcCommon)
		compileOnly(Hardware)
		compileOnly(OnBotJava)
		compileOnly(Blocks)
		testImplementation(FtcCommon)
	}
}

dependencies {
	api("dev.frozenmilk:Sinister:2.3.0")
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
