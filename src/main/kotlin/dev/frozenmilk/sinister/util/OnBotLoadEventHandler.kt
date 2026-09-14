package dev.frozenmilk.sinister.util

import android.content.Context
import com.qualcomm.ftccommon.FtcEventLoop
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl
import dev.frozenmilk.sinister.configurable.Configuration
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.LoadEventHandler
import dev.frozenmilk.sinister.loading.LoadEventHandlerInterface
import dev.frozenmilk.sinister.sdk.apphooks.OnCreateEventLoop
import dev.frozenmilk.sinister.sdk.opmodes.SinisterRegisteredOpModes
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.opmode.RegisteredOpModes.DEFAULT_OP_MODE_METADATA
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
object OnBotLoadEventHandler : LoadEventHandlerInterface, Configuration<LoadEventHandler> {
    override val configurableClass = LoadEventHandler::class.java
    override fun configure(configurable: LoadEventHandler) {
        configurable.DELEGATE = this
    }

    override val adjacencyRule = Configuration.INDEPENDENT

    private val robotStarted = AtomicBoolean(false)

    @Suppress("unused")
    private object OnCreateEventLoopHook : OnCreateEventLoop {
        override fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
            if (LoadEventHandler.DELEGATE !== this@OnBotLoadEventHandler) return
            robotStarted.set(true)
            Logger.d(
                OnBotLoadEventHandler.javaClass.simpleName,
                "Replacing default OpMode with Sloth default OpMode"
            )
            SinisterRegisteredOpModes.unregister(DEFAULT_OP_MODE_METADATA)
            SinisterRegisteredOpModes.register(DEFAULT_OP_MODE_METADATA) { OpMode() }
            ftcEventLoop.opModeManager.stopActiveOpMode()
        }
    }

    private val events = ConcurrentLinkedQueue<LoadEvent<*>>()

    class OpMode : OpModeManagerImpl.DefaultOpMode() {
        override fun init_loop() {
            super.init_loop()
            tryRelease()
        }

        override fun loop() {
            super.loop()
            tryRelease()
        }

        private fun tryRelease() {
            if (events.isEmpty()) return
            Logger.v(javaClass.simpleName, "Processing Load Events")
            while (events.isNotEmpty()) events.remove().release()
            Logger.v(javaClass.simpleName, "Stopping")
            terminateOpModeNow()
        }
    }

    override fun handleEventStaging(loadEvent: LoadEvent<*>) {
        Logger.v(javaClass.simpleName, "Handling staging of load event")
        if (robotStarted.get()) {
            Logger.v(javaClass.simpleName, "Event staged for processing after user OpMode")
            events.add(loadEvent)
        } else {
            Logger.v(javaClass.simpleName, "Releasing event before robot finished starting")
            loadEvent.release()
        }
    }
}
