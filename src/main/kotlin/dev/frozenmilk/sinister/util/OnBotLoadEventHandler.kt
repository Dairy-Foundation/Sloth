package dev.frozenmilk.sinister.util

import android.content.Context
import com.qualcomm.ftccommon.FtcEventLoop
import dev.frozenmilk.sinister.configurable.Configuration
import dev.frozenmilk.sinister.loading.LoadEventHandler
import dev.frozenmilk.sinister.sdk.apphooks.OnCreateEventLoop
import dev.frozenmilk.sinister.sdk.apphooks.SinisterOpModeRegistrar
import dev.frozenmilk.sinister.sdk.opmodes.OpModeScanner
import dev.frozenmilk.sinister.sdk.opmodes.SinisterRegisteredOpModes
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta

@Suppress("unused")
object OnBotLoadEventHandler : OnBotLoadEventHandlerImpl(), Configuration<LoadEventHandler> {
	override val configurableClass = LoadEventHandler::class.java
	override fun configure(configurable: LoadEventHandler) {
		configurable.DELEGATE = this
	}
	override val adjacencyRule = Configuration.INDEPENDENT

	@Suppress("unused")
	private object OnCreateEventLoopHook : OnCreateEventLoop {
		override fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
			opModeManagerImpl = ftcEventLoop.opModeManager
			opModeManagerImpl!!.registerListener(OnBotLoadEventHandler)
			currentMeta = SinisterRegisteredOpModes.getOpModeMetadata(opModeManagerImpl!!.activeOpModeName)
		}
	}

	@Suppress("unused")
	private object OpModeRegistrarHook : SinisterOpModeRegistrar {
		override fun registerOpModes(registrationHelper: OpModeScanner.RegistrationHelper) {
			registrationHelper.register(
				OpModeMeta.Builder().apply {
					name = processLoadEventName
					flavor = OpModeMeta.Flavor.SYSTEM
					systemOpModeBaseDisplayName = "Process Load Event"
				}.build()
			) { ProcessLoadEvent() }
		}
	}
}