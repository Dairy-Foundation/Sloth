package dev.frozenmilk.sinister.sdk

import dev.frozenmilk.sinister.loading.Preload
import org.firstinspires.ftc.robotcore.internal.opmode.ClassFilter
import org.firstinspires.ftc.robotcore.internal.opmode.ClassManager

@Suppress("unused")
@Preload
object SDKClassFilterRemover {
    init {
        ClassManager::class.java.getDeclaredField("filters").apply {
            isAccessible = true
        }.set(ClassManager.getInstance(), FalseEmptySet<ClassFilter>())
    }
}