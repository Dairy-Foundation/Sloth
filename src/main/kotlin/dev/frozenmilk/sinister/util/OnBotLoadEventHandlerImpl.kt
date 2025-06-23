package dev.frozenmilk.sinister.util

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerNotifier
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.LoadEventHandlerInterface
import dev.frozenmilk.sinister.sdk.opmodes.SinisterRegisteredOpModes
import dev.frozenmilk.sinister.util.log.Logger
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import org.firstinspires.ftc.robotcore.internal.opmode.RegisteredOpModes.DEFAULT_OP_MODE_METADATA

open class OnBotLoadEventHandlerImpl : LoadEventHandlerInterface, OpModeManagerNotifier.Notifications {
	@Volatile
	protected var currentMeta: OpModeMeta? = null
	protected var opModeManagerImpl: OpModeManagerImpl? = null
	protected val events = mutableListOf<LoadEvent<*>>()
	protected val processLoadEventName = "\$Process\$Load\$Event\$"

	protected inner class ProcessLoadEvent : LinearOpMode() {
		override fun runOpMode() {
			Logger.v(javaClass.simpleName, "Processing Load Events")
			synchronized(this@OnBotLoadEventHandlerImpl) {
				events
					.onEach { it.release() }
					.clear()
			}
			Logger.v(javaClass.simpleName, "Stopping")
			terminateOpModeNow()
		}
	}

	private fun canRun() =
		currentMeta.let { meta ->
			meta == null || (meta.name == DEFAULT_OP_MODE_METADATA.name && meta.flavor == DEFAULT_OP_MODE_METADATA.flavor)
		}

	private fun tryProcess(): Boolean {
		SinisterRegisteredOpModes.getOpModeMetadata(opModeManagerImpl?.activeOpModeName)?.flavor
		val res = canRun() && events.isNotEmpty()
		if (res) {
			Logger.d(javaClass.simpleName, "Processing Load Events")
			opModeManagerImpl?.initOpMode(processLoadEventName, true)
		}
		return res
	}

	// WARNING: LoadEvent.release() cannot be called in any of opmode hooks (sadge)
	// as it will cause a deadlock
	override fun handleEventStaging(loadEvent: LoadEvent<*>) {
		Logger.v(javaClass.simpleName, "Handling staging of load event")
		synchronized(this) {
			if (opModeManagerImpl == null) {
				Logger.v(javaClass.simpleName, "Releasing event from before robot finished starting")
				loadEvent.release()
				return
			}
			events.add(loadEvent)
			if (!tryProcess()) Logger.v(javaClass.simpleName, "Storing event for later")
		}
	}

	override fun onOpModePreInit(opMode: OpMode?) {
		currentMeta = SinisterRegisteredOpModes.getOpModeMetadata(opModeManagerImpl?.activeOpModeName)
		tryProcess()
	}

	override fun onOpModePreStart(opMode: OpMode?) {
		currentMeta = SinisterRegisteredOpModes.getOpModeMetadata(opModeManagerImpl?.activeOpModeName)
	}

	override fun onOpModePostStop(opMode: OpMode?) {
		currentMeta = SinisterRegisteredOpModes.getOpModeMetadata(opModeManagerImpl?.activeOpModeName)
	}
}