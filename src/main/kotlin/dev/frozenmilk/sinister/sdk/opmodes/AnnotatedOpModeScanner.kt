package dev.frozenmilk.sinister.sdk.opmodes

import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.Disabled
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.eventloop.opmode.Utility
import com.qualcomm.robotcore.util.RobotLog
import dev.frozenmilk.sinister.targeting.WideSearch
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.opmode.AnnotatedOpModeClassFilter
import org.firstinspires.ftc.robotcore.internal.opmode.OnBotJavaDeterminer
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import java.lang.reflect.Modifier

object AnnotatedOpModeScanner : OpModeScanner() {
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

        val teleOp = cls.getAnnotation(TeleOp::class.java)
        val autonomous = cls.getAnnotation(Autonomous::class.java)
        val utility = cls.getAnnotation(Utility::class.java)

        if (teleOp == null && autonomous == null && utility == null) return null to null

        // the sdk rejects any combination of the three, report the same way it does
        conflictingAnnotations(cls, teleOp, autonomous, utility)?.let { return null to it }

        checkOpModeClass(cls)?.let { return null to it }

        val builder = when {
            teleOp != null -> OpModeMeta.Builder()
                .setName(cls.opModeName(teleOp.name).apply {
                    if (!OpModeMeta.nameIsLegalForOpMode(this, false))
                        return null to "\"$this\" is not a legal OpMode name"
                })
                .setFlavor(OpModeMeta.Flavor.TELEOP)
                .setGroup(teleOp.group.ifEmpty { OpModeMeta.DefaultGroup })

            autonomous != null -> OpModeMeta.Builder()
                .setName(cls.opModeName(autonomous.name).apply {
                    if (!OpModeMeta.nameIsLegalForOpMode(this, false))
                        return null to "\"$this\" is not a legal OpMode name"
                })
                .setFlavor(OpModeMeta.Flavor.AUTONOMOUS)
                .setGroup(autonomous.group.ifEmpty { OpModeMeta.DefaultGroup })
                .setTransitionTarget(autonomous.preselectTeleOp.ifEmpty { null })

            // @Utility is new as of sdk 11.2.0.
            // the sdk registers these with an empty group, which lands them in the default group,
            // and carries the human readable blurb shown in the Utility menu on the meta
            else -> OpModeMeta.Builder()
                .setName(cls.opModeName(utility!!.name).apply {
                    if (!OpModeMeta.nameIsLegalForOpMode(this, false))
                        return null to "\"$this\" is not a legal OpMode name"
                })
                .setFlavor(OpModeMeta.Flavor.UTILITY)
                .setGroup(OpModeMeta.DefaultGroup)
                .setDescription(utility.description)
        }

        return builder
            .setSource(when {
                OnBotJavaDeterminer.isOnBotJava(cls) -> OpModeMeta.Source.ONBOTJAVA
                OnBotJavaDeterminer.isExternalLibraries(cls) -> OpModeMeta.Source.EXTERNAL_LIBRARY
                else -> OpModeMeta.Source.ANDROID_STUDIO
            })
            .build() to null
    }

    /**
     * the display name the sdk would give [this] for an annotation whose `name` is [annotationName]
     *
     * OpModes that ship inside the robot controller app itself get a " (Built-in)" suffix,
     * so that they are distinguishable from identically named team code
     */
    private fun Class<*>.opModeName(annotationName: String): String {
        val name = annotationName.ifBlank { simpleName }
        return if (`package`?.name?.startsWith(AnnotatedOpModeClassFilter.BUILTIN_PACKAGE_PREFIX) == true) "$name (Built-in)"
        else name
    }

    private fun conflictingAnnotations(cls: Class<*>, teleOp: TeleOp?, autonomous: Autonomous?, utility: Utility?): String? = when {
        teleOp != null && autonomous != null && utility != null ->
            "class $cls is annotated as '@TeleOp', '@Autonomous' and '@Utility'; please choose at most one"
        teleOp != null && autonomous != null ->
            "class $cls is annotated as both '@TeleOp' and '@Autonomous'; please choose at most one"
        teleOp != null && utility != null ->
            "class $cls is annotated as both '@TeleOp' and '@Utility'; please choose at most one"
        autonomous != null && utility != null ->
            "class $cls is annotated as both '@Autonomous' and '@Utility'; please choose at most one"
        else -> null
    }

    fun checkOpModeClass(cls: Class<*>): String? {
        if (!OpMode::class.java.isAssignableFrom(cls)) return "class $cls doesn't inherit from the class 'OpMode'"
        if (cls.enclosingClass != null && !Modifier.isStatic(cls.modifiers)) return "class $cls is an inner class. Inner classes can not be run as OpModes "
        if (!Modifier.isPublic(cls.modifiers)) return "class $cls is not declared 'public'"
        return null
    }
}
