package dev.frozenmilk.sinister.sdk.opmodes

import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.Disabled
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.targeting.WideSearch
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.opmode.OnBotJavaDeterminer
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import java.lang.reflect.Modifier

object TeleopAutonomousOpModeScanner : OpModeScanner() {
    override val targets = WideSearch()

    override fun scan(loader: ClassLoader, cls: Class<*>, registrationHelper: RegistrationHelper) {
        if (OpMode::class.java.isAssignableFrom(cls)) {
            val (meta, error) = metaForClass(cls) // no meta extractable, we are going to ignore these errors
            if (error != null) {
                Logger.e(javaClass.simpleName, "OpMode Configuration Error:\n$error")
                RobotLog.setGlobalErrorMsg(error)
                return
            }
            if (meta == null) return
            @Suppress("UNCHECKED_CAST")
            registrationHelper.register(meta, cls as Class<out OpMode>)
        }
    }

    /**
     * either returns an `OpModeMeta` or a `String?`
     *
     * if the `OpModeMeta` is returned, will not return a String
     *
     * if no `OpModeMeta` is returned, and a String is returned, then the string will be an error message for why not
     *
     * if no `OpModeMeta` is returned, and a String is not returned, then the error is an expected failure (`@Disabled` for example)
     */
    fun metaForClass(cls: Class<*>): Pair<OpModeMeta?, String?> {
        if (cls.isAnnotationPresent(Disabled::class.java)) return null to null

        val builder = if (cls.isAnnotationPresent(TeleOp::class.java)) {
            checkOpModeClass(cls)?.let {
                return null to it
            }
            if (cls.isAnnotationPresent(Autonomous::class.java)) return null to "class $cls is annotated with both '@TeleOp' and '@Autonomous'; please choose one at most"
            val annotation = cls.getAnnotation(TeleOp::class.java)!!
            OpModeMeta.Builder()
                .setName(annotation.name.ifBlank { cls.simpleName }.apply {
                    if (!OpModeMeta.nameIsLegalForOpMode(this, false))
                        return null to "\"$this\" is not a legal OpMode name"
                })
                .setFlavor(OpModeMeta.Flavor.TELEOP)
                .setGroup(annotation.group.ifEmpty { OpModeMeta.DefaultGroup })
        }
        else if (cls.isAnnotationPresent(Autonomous::class.java)) {
            checkOpModeClass(cls)?.let {
                return null to it
            }
            if (cls.isAnnotationPresent(TeleOp::class.java)) return null to "class $cls is annotated both as '@TeleOp' and '@Autonomous'; please choose at most one"
            val annotation = cls.getAnnotation(Autonomous::class.java)!!
            OpModeMeta.Builder()
                .setName(annotation.name.ifBlank { cls.simpleName }.apply {
                    if (!OpModeMeta.nameIsLegalForOpMode(this, false))
                        return null to "\"$this\" is not a legal OpMode name"
                })
                .setFlavor(OpModeMeta.Flavor.AUTONOMOUS)
                .setGroup(annotation.group.ifEmpty { OpModeMeta.DefaultGroup })
                .setTransitionTarget(annotation.preselectTeleOp.ifEmpty { null })
        }
        else null

        return builder
            ?.setSource(when {
                OnBotJavaDeterminer.isOnBotJava(cls) -> OpModeMeta.Source.ONBOTJAVA
                OnBotJavaDeterminer.isExternalLibraries(cls) -> OpModeMeta.Source.EXTERNAL_LIBRARY
                else -> OpModeMeta.Source.ANDROID_STUDIO
            })
            ?.build() to null
    }
    fun checkOpModeClass(cls: Class<*>): String? {
        if (!OpMode::class.java.isAssignableFrom(cls)) return "class $cls doesn't inherit from the class 'OpMode'"
        if (cls.enclosingClass != null && !Modifier.isStatic(cls.modifiers)) return "class $cls is an inner class. Inner classes can not be run as OpModes "
        if (!Modifier.isPublic(cls.modifiers)) return "class $cls is not declared 'public'"
        return null
    }
}
