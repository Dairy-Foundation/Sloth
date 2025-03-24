package dev.frozenmilk.sinister.util

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerNotifier
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.LoadEventHandlerInterface
import dev.frozenmilk.sinister.util.log.Logger

open class OnBotLoadEventHandlerImpl : LoadEventHandlerInterface, OpModeManagerNotifier.Notifications {
	@Volatile
	protected var currentOpMode: OpMode? = null
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
		currentOpMode.let { opMode ->
			opMode == null || opMode.javaClass == OpModeManagerImpl.DefaultOpMode::class.java
		}

	private fun tryProcess(): Boolean {
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
		currentOpMode = opMode
		tryProcess()
	}

	override fun onOpModePreStart(opMode: OpMode?) {
		currentOpMode = opMode
	}

	override fun onOpModePostStop(opMode: OpMode?) {
		currentOpMode = opMode
	}
}