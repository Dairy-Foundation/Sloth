package dev.frozenmilk.sinister.sdk.opmodes

import com.qualcomm.ftccommon.CommandList
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl
import com.qualcomm.robotcore.eventloop.opmode.OpModeRegister
import com.qualcomm.robotcore.exception.DuplicateNameException
import com.qualcomm.robotcore.robocol.Command
import dev.frozenmilk.sinister.ExternalLibrariesLoader
import dev.frozenmilk.sinister.OnBotJavaLoader
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.collections.SimpleGson
import org.firstinspires.ftc.robotcore.internal.network.NetworkConnectionHandler
import org.firstinspires.ftc.robotcore.internal.opmode.InstanceOpModeRegistrar
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMetaAndClass
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMetaAndInstance
import org.firstinspires.ftc.robotcore.internal.opmode.RegisteredOpModes
import org.firstinspires.ftc.robotcore.internal.system.Assert
import java.util.function.Supplier

/**
 * shims [RegisteredOpModes.getInstance] to be this, and exposes the underlying data structures and methods for more customisation
 */
@Preload
object SinisterRegisteredOpModes : RegisteredOpModes() {
    private val opModeSuppliers = mutableMapOf<String, Pair<OpModeMeta, Supplier<out OpMode>>>()
    @Volatile
    private var handledUserOpModeRegister = false
    private val TAG = javaClass.simpleName
    init {
        val tag = getInstance().javaClass.getDeclaredField("TAG")
        tag.isAccessible = true
        Logger.v(tag.get(null)!!.toString(), "replacing RegisteredOpModes instance with dynamically capable shim instance, migrating TAG from \"${tag.get(null)}\" to \"${javaClass.simpleName}\"")
        tag.set(null, TAG)

        val instanceHolder = RegisteredOpModes::class.java.declaredClasses.first()
        val theInstanceField = instanceHolder.getDeclaredField("theInstance")
        theInstanceField.isAccessible = true
        theInstanceField.set(null, this)

        opmodesAreRegistered = true
        // SDK Comment:
        // register our default OpMode first, that way the user can override it (eh?)
        register(
            DEFAULT_OP_MODE_METADATA,
            OpModeManagerImpl.DefaultOpMode::class.java
        )
        // we also only call this once, the sdk will handle dropping these and replacing them
        callInstanceOpModeRegistrars()
    }

    override fun register(meta: OpModeMeta, opMode: Class<*>) {
        lockOpModesWhile {
            if (reportIfOpModeAlreadyRegistered(meta)) {
                @Suppress("UNCHECKED_CAST")
                opModeClasses[meta.name] =
                    OpModeMetaAndClass(
                        meta,
                        opMode as Class<OpMode>
                    )
                Logger.v(
                    TAG,
                    "registered {${opMode.simpleName}} as {${meta.name}}"
                )
            } else {
                throw DuplicateNameException("Duplicate for " + meta.name)
            }
            sendUpdatedOpModes()
        }
    }

    override fun register(
        meta: OpModeMeta,
        instance: OpMode,
        instanceOpModeRegistrar: InstanceOpModeRegistrar?
    ) {
        lockOpModesWhile {
            if (reportIfOpModeAlreadyRegistered(meta)) {
                opModeInstances[meta.name] =
                    OpModeMetaAndInstance(meta, instance, instanceOpModeRegistrar)
                Logger.v(
                    TAG,
                    String.format("registered instance as {%s}", meta)
                )
            } else {
                throw DuplicateNameException("Duplicate for " + meta.name)
            }
            sendUpdatedOpModes()
        }
    }

    fun register(
        meta: OpModeMeta,
        supplier: Supplier<out OpMode>
    ) {
        lockOpModesWhile {
            if (reportIfOpModeAlreadyRegistered(meta)) {
                opModeSuppliers[meta.name] = meta to supplier
                Logger.v(
                    TAG,
                    String.format("registered supplier as {%s}", meta)
                )
            } else {
                throw DuplicateNameException("Duplicate for " + meta.name)
            }
            sendUpdatedOpModes()
        }
    }

    override fun isOpmodeRegistered(meta: OpModeMeta) = isOpmodeRegistered(meta.name)

    private fun isOpmodeRegistered(name: String): Boolean {
        Assert.assertTrue(opModesAreLocked)
        return opModeClasses.containsKey(name) || opModeInstances.containsKey(name) || opModeSuppliers.containsKey(name)
    }

    // this method is strange in the sdk, we basically don't need to have this 'reset everything method'
    override fun registerAllOpModes(userOpmodeRegister: OpModeRegister) {
        // we've already handled the userOpModeRegister. Its not going to change. Blow that thing up.
        if (handledUserOpModeRegister) return
        lockOpModesWhile {
            userOpmodeRegister.register(this)

            // NOTE: we are removing this line, no more silly sdk opmode registration, its all mine now
            // AnnotatedOpModeClassFilter.getInstance().registerAllClasses(this)

            handledUserOpModeRegister = true
        }
    }

    public override fun unregister(meta: OpModeMeta) = unregister(meta.name)

    fun unregister(name: String) {
        lockOpModesWhile {
            Logger.v(TAG, "unregistered {${name}}")
            opModeClasses.remove(name)
            opModeInstances.remove(name)
            opModeSuppliers.remove(name)
            Assert.assertFalse(isOpmodeRegistered(name))
            sendUpdatedOpModes()
        }
    }

    override fun getOpModes(): List<OpModeMeta> {
        return lockOpModesWhile<List<OpModeMeta>> {
            opModeClasses.values.map { it.meta }
                .plus(opModeInstances.values.map { it.meta })
                .plus(opModeSuppliers.values.map { it.first })
        }
    }

    override fun getOpMode(opModeName: String?): OpMode {
        return lockOpModesWhile<OpMode> {
            try {
                opModeInstances[opModeName]?.run { instance }
                    ?: opModeClasses[opModeName]?.run { clazz.newInstance() }
                    ?: opModeSuppliers[opModeName]?.run { second.get() }
            }
            catch (e: Throwable) {
                when (e) {
                    is InstantiationException, is IllegalAccessException -> {
                        Logger.e(TAG, "encountered error while attempting to evaluate an opmode with name $opModeName.\n" +
                                "this error has been suppressed and instead null has been returned", e)
                        null
                    }
                    else -> throw e
                }
            }
        }
    }

    override fun getOpModeMetadata(opModeName: String?): OpModeMeta? {
        return lockOpModesWhile<OpModeMeta> {
            opModeInstances[opModeName]?.run { meta }
                ?: opModeClasses[opModeName]?.run { meta }
                ?: opModeSuppliers[opModeName]?.run { first }
        }
    }

    override fun registerOnBotJavaOpModes() {
        // might seem like an odd place to have this, but technically it does what the sdk does!
        OnBotJavaLoader.switchLoader()
    }

    override fun registerExternalLibrariesOpModes() {
        // might seem like an odd place to have this, but technically it does what the sdk does!
        ExternalLibrariesLoader.switchLoader()
    }

    override fun registerInstanceOpModes() {
        // does nothing atm
    }

    private fun sendUpdatedOpModes() {
        val opModeList = SimpleGson.getInstance().toJson(opModes)
        NetworkConnectionHandler.getInstance().sendCommand(
            Command(
                CommandList.CMD_NOTIFY_OP_MODE_LIST,
                opModeList
            )
        )
    }
}