import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

repositories {
    mavenCentral()
    google()
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("java-gradle-plugin")
    id("dev.frozenmilk.publish") version "0.1.0"
}

group = "dev.frozenmilk"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xreturn-value-checker=full")
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    //noinspection AndroidGradlePluginVersion
    implementation("com.android.tools.build:gradle:8.13.2")
}

dairyPublishing {
    // git directory is in the parent
    gitDir = file("..")
}

gradlePlugin {
    plugins {
        create("Load") {
            id = "dev.frozenmilk.sinister.sloth.load"
            implementationClass = "dev.frozenmilk.sinister.sloth.Load"
        }
    }
}
