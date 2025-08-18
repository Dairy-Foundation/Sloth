@file:Suppress("Deprecation")

package dev.frozenmilk.sinister.sdk

import android.content.Context
import com.qualcomm.ftccommon.FtcEventLoop
import com.qualcomm.ftccommon.FtcEventLoopBase
import com.qualcomm.ftccommon.FtcEventLoopHandler
import com.qualcomm.ftccommon.configuration.RobotConfigFile
import com.qualcomm.ftccommon.configuration.RobotConfigFileManager
import com.qualcomm.hardware.HardwareFactory
import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.configuration.ConfigurationType
import com.qualcomm.robotcore.hardware.configuration.ConfigurationTypeManager
import com.qualcomm.robotcore.hardware.configuration.ConfigurationTypeManager.ClassSource
import com.qualcomm.robotcore.hardware.configuration.DistributorInfo
import com.qualcomm.robotcore.hardware.configuration.ExpansionHubMotorControllerPositionParams
import com.qualcomm.robotcore.hardware.configuration.ExpansionHubMotorControllerVelocityParams
import com.qualcomm.robotcore.hardware.configuration.I2cSensor
import com.qualcomm.robotcore.hardware.configuration.MotorType
import com.qualcomm.robotcore.hardware.configuration.annotations.AnalogSensorType
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.DevicesProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.DigitalIoDeviceType
import com.qualcomm.robotcore.hardware.configuration.annotations.ExpansionHubPIDFPositionParams
import com.qualcomm.robotcore.hardware.configuration.annotations.ExpansionHubPIDFVelocityParams
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.qualcomm.robotcore.hardware.configuration.annotations.ServoType
import com.qualcomm.robotcore.hardware.configuration.annotations.ServoTypes
import com.qualcomm.robotcore.hardware.configuration.typecontainers.AnalogSensorConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.DigitalIoDeviceConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.I2cDeviceConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.InstantiableUserConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.ServoConfigurationType
import com.qualcomm.robotcore.hardware.configuration.typecontainers.UserConfigurationType
import com.qualcomm.robotcore.util.ClassUtil
import com.qualcomm.robotcore.util.RobotLog
import com.qualcomm.robotcore.util.Util
import dev.frozenmilk.sinister.Scanner
import dev.frozenmilk.sinister.sdk.apphooks.OnCreateEventLoop
import dev.frozenmilk.sinister.targeting.WideSearch
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.cell.MirroredCell
import org.firstinspires.ftc.robotcore.internal.opmode.OnBotJavaDeterminer
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.lang.reflect.Modifier

/**
 * performs [ConfigurationTypeManager]'s scanning for it
 */
@Suppress("unused")
object ConfigurationTypeScanner : Scanner, OnCreateEventLoop {
	private val TAG = javaClass.simpleName

	override val loadAdjacencyRule = Scanner.INDEPENDENT
	override val unloadAdjacencyRule = Scanner.INDEPENDENT

	private val configurationTypes = mutableMapOf<ClassLoader, MutableList<UserConfigurationType>>()
	private val mapTagToConfigurationType by MirroredCell<Map<String, ConfigurationType>>(
		ConfigurationTypeManager.getInstance(), "mapTagToConfigurationType"
	)
	private val displayNamesMap by MirroredCell<Map<ConfigurationType.DeviceFlavor, Map<String, String>>>(
		ConfigurationTypeManager.getInstance(), "displayNamesMap"
	)
	private val typeAnnotationsList by MirroredCell<List<Class<*>>>(
		ConfigurationTypeManager.getInstance(), "typeAnnotationsList"
	)

	private var resetHardwareMap: Runnable? = null

	override val targets = WideSearch()

	private val clearAll =
		ConfigurationTypeManager::class.java.getDeclaredMethod("clearAllUserTypes").run {
			isAccessible = true
			{
				invoke(ConfigurationTypeManager.getInstance())
				Unit
			}
		}

	private val add = ConfigurationTypeManager::class.java.getDeclaredMethod(
		"add", UserConfigurationType::class.java
	).run {
		isAccessible = true
		{ deviceType: UserConfigurationType ->
			invoke(ConfigurationTypeManager.getInstance(), deviceType)
			Unit
		}
	}

	override fun beforeScan(loader: ClassLoader) {
		configurationTypes[loader] = mutableListOf()
		clearAll()
	}

	override fun scan(loader: ClassLoader, cls: Class<*>) {
		// Unlike filterOnBotJavaClass() and filterExternalLibrariesClass(), filterClass() can be
		// called for a class from any source.
		val source = when {
			OnBotJavaDeterminer.isOnBotJava(cls) -> ClassSource.ONBOTJAVA
			OnBotJavaDeterminer.isExternalLibraries(cls) -> ClassSource.EXTERNAL_LIB
			else -> ClassSource.APK
		}
		scan(loader, cls, source)
	}

	override fun afterScan(loader: ClassLoader) {
		reregister()
	}

	override fun beforeUnload(loader: ClassLoader) {
		configurationTypes.remove(loader)
	}

	override fun unload(loader: ClassLoader, cls: Class<*>) {}
	override fun afterUnload(loader: ClassLoader) {
		reregister()
	}

	private fun reregister() {
		clearAll()
		configurationTypes.values.forEach { list ->
			list.forEach {
				Logger.v(TAG, "registering $it as ${it.name}")
				add(it)
			}
		}
		ConfigurationTypeManager.getInstance().sendUserDeviceTypes()
		resetHardwareMap?.run()
	}

	override fun onCreateEventLoop(context: Context, ftcEventLoop: FtcEventLoop) {
		val eventLoopHandler =
			FtcEventLoopBase::class.java.getDeclaredField("ftcEventLoopHandler").apply {
				isAccessible = true
			}.get(ftcEventLoop)!! as FtcEventLoopHandler
		val hardwareFactory =
			FtcEventLoopHandler::class.java.getDeclaredField("hardwareFactory").apply {
				isAccessible = true
			}.get(eventLoopHandler)!! as HardwareFactory
		val hwMapEventLoopHandler =
			FtcEventLoopHandler::class.java.getDeclaredField("hardwareMap").apply {
				isAccessible = true
			}
		val robotConfigFileManager =
			FtcEventLoopBase::class.java.getDeclaredField("robotCfgFileMgr").apply {
				isAccessible = true
			}.get(ftcEventLoop)!! as RobotConfigFileManager

		resetHardwareMap = Runnable {
			Logger.v(TAG, "Rebuilding HardwareMap...")

			var file: RobotConfigFile = robotConfigFileManager.getActiveConfigAndUpdateUI()

			try {
				hardwareFactory.xmlPullParser = file.xml
			} catch (e: FileNotFoundException) {
				Logger.w(
					TAG,
					"Unable to set configuration file ${file.name}. Falling back on noConfig.",
					e
				)
				file = RobotConfigFile.noConfig(robotConfigFileManager)
				try {
					hardwareFactory.xmlPullParser = file.xml
					robotConfigFileManager.setActiveConfigAndUpdateUI(false, file)
				} catch (e1: FileNotFoundException) {
					Logger.e(TAG, "Failed to fall back on noConfig", e1)
				} catch (e1: XmlPullParserException) {
					Logger.e(TAG, "Failed to fall back on noConfig", e1)
				}
			} catch (e: XmlPullParserException) {
				Logger.w(
					TAG,
					"Unable to set configuration file ${file.name}. Falling back on noConfig.",
					e
				)
				file = RobotConfigFile.noConfig(robotConfigFileManager)
				try {
					hardwareFactory.xmlPullParser = file.xml
					robotConfigFileManager.setActiveConfigAndUpdateUI(false, file)
				} catch (e1: FileNotFoundException) {
					Logger.e(TAG, "Failed to fall back on noConfig", e1)
				} catch (e1: XmlPullParserException) {
					Logger.e(TAG, "Failed to fall back on noConfig", e1)
				}
			}

			hwMapEventLoopHandler.set(eventLoopHandler, null)
			ftcEventLoop.opModeManager.hardwareMap =
				eventLoopHandler.getHardwareMap(ftcEventLoop.opModeManager)

			Logger.d(TAG, "new HardwareMap devices:")
			ftcEventLoop.opModeManager.hardwareMap.unsafeIterable().forEach {
				Logger.d(
					TAG, "${
						ftcEventLoop.opModeManager.hardwareMap.getNamesOf(it).joinToString()
					}: ${it.deviceName}"
				)
			}
			Logger.d(TAG, "...Finished rebuilding HardwareMap")
		}
	}

	private fun getXmlTag(motorType: MotorType) = ClassUtil.decodeStringRes(motorType.xmlTag.trim())

	private fun getXmlTag(i2cSensor: I2cSensor) = ClassUtil.decodeStringRes(i2cSensor.xmlTag.trim())

	private fun getXmlTag(deviceProperties: DeviceProperties) =
		ClassUtil.decodeStringRes(deviceProperties.xmlTag.trim())

	private val nameStartChar = "\\p{Alpha}_:"
	private val nameChar = nameStartChar + "0-9\\-\\."
	private val xmlRegex = ("^[$nameStartChar][$nameChar]*$").toRegex()
	private fun isLegalXmlTag(xmlTag: String): Boolean {
		if (!Util.isGoodString(xmlTag)) return false

		// For simplicity, we only allow a restricted subset of what XML allows
		//  https://www.w3.org/TR/REC-xml/#NT-NameStartChar

		return xmlTag.matches(xmlRegex)
	}

	private fun isHardwareDevice(clazz: Class<*>) =
		ClassUtil.inheritsFrom(clazz, HardwareDevice::class.java)

	private fun reportConfigurationError(format: String, vararg args: Any) {
		val message = String.format(format, *args)
		Logger.e(TAG, "configuration error: $message")
		RobotLog.setGlobalErrorMsg(message)
	}

	private fun checkAnnotationParameterConstraints(deviceType: UserConfigurationType): Boolean {
		val xmlTag = deviceType.xmlTag
		val deviceFlavor = deviceType.deviceFlavor

		// Check the user-visible form of the sensor name
		if (!isLegalDeviceTypeName(deviceType.name)) {
			reportConfigurationError("\"%s\" is not a legal device type name", deviceType.name)
			return false
		}

		// Check the XML tag
		if (!isLegalXmlTag(xmlTag)) {
			reportConfigurationError(
				"\"%s\" is not a legal XML tag for the device type \"%s\"", xmlTag, deviceType.name
			)
			return false
		}

		if (deviceType.annotatedClassIsInstantiable()) {
			// Instantiable configuration types can have duplicate XML tags and display names,
			// but there are rules.

			// For XML tags, any tags that this device type duplicates must also be for instantiable
			// types and have the same DeviceFlavor. Having duplicated XML tags means that for a
			// configuration that contains that XML tag, multiple different classes will be
			// instantiated. The user will be able to retrieve an instance of their desired class
			// from the HardwareMap. This allows for the user to switch which device driver they use
			// to access a device without having to select a different type in the configuration.

			val duplicatingType = mapTagToConfigurationType[xmlTag]
			if (duplicatingType != null) {
				if (!duplicatingType.annotatedClassIsInstantiable()) {
					// XML tags for instantiable types can only duplicate other instantiable types
					reportConfigurationError(
						"the XML tag \"%s\" is already defined by a non-instantiable type", xmlTag
					)
					return false
				}

				if (!duplicatingType.isDeviceFlavor(deviceFlavor)) {
					// XML tags for instantiable types can only duplicate types with the same device flavor
					reportConfigurationError(
						"the XML tag \"%s\" cannot be registered as a %s device, because it is already registered as a %s device.",
						xmlTag,
						deviceFlavor,
						duplicatingType.deviceFlavor
					)
					return false
				}
			}

			// For display names, we must never have ambiguity about which XML tag a given display
			// name corresponds to. Therefore, any already-processed device type that shares this
			// display name must also have the same XML tag.
			val tagAssociatedWithDuplicateDisplayName: String? =
				displayNamesMap[deviceFlavor]?.get(deviceType.name)
			if (tagAssociatedWithDuplicateDisplayName != null && xmlTag != tagAssociatedWithDuplicateDisplayName) {
				reportConfigurationError(
					"the display name \"%s\" is already registered with the XML " + "tag \"%s\", and cannot be registered with the XML tag \"%s\""
				)
			}
		} else {
			// Non-instantiable device types must have fully unique names and XML tags.
			if (displayNamesMap[deviceFlavor]?.containsKey(deviceType.name) == true) {
				reportConfigurationError(
					"the device type \"%s\" is already defined", deviceType.name
				)
				return false
			}
			if (mapTagToConfigurationType.containsKey(xmlTag)) {
				reportConfigurationError("the XML tag \"%s\" is already defined", xmlTag)
				return false
			}
		}

		return true
	}

	private fun checkInstantiableTypeConstraints(deviceType: InstantiableUserConfigurationType): Boolean {
		if (!checkAnnotationParameterConstraints(deviceType)) {
			return false
		}
		// If the class doesn't extend HardwareDevice, that's an error, we'll ignore it
		if (!isHardwareDevice(deviceType.clazz)) {
			reportConfigurationError(
				"'%s' class doesn't inherit from the class 'HardwareDevice'",
				deviceType.clazz.simpleName
			)
			return false
		}

		// If it's not 'public', it can't be loaded by the system and won't work. We report
		// the error and ignore
		if (!Modifier.isPublic(deviceType.clazz.modifiers)) {
			reportConfigurationError(
				"'%s' class is not declared 'public'", deviceType.clazz.simpleName
			)
			return false
		}

		// Can we instantiate?
		if (!deviceType.hasConstructors()) {
			reportConfigurationError(
				"'%s' class lacks necessary constructor", deviceType.clazz.simpleName
			)
			return false
		}
		return true
	}

	private fun isLegalDeviceTypeName(name: String): Boolean {
		return Util.isGoodString(name)
	}

	/** Allow annotations to be inherited if we want them to.  */
	private fun <A : Annotation?> findAnnotation(clazz: Class<*>, annotationType: Class<A>): A? {
		val result = ArrayList<A?>(1)
		result.add(null)

		ClassUtil.searchInheritance(
			clazz
		) { aClass ->
			val annotation = aClass.getAnnotation(annotationType)
			if (annotation != null) {
				result[0] = annotation
				true
			} else false
		}

		return result[0]
	}

	private fun <NewType : Annotation, OldType : Annotation> processNewOldAnnotations(
		motorConfigurationType: MotorConfigurationType,
		clazz: Class<*>,
		newType: Class<NewType>,
		oldType: Class<OldType>
	) {
		// newType is logical superset of oldType. Thus, there's no reason to ever have an oldType
		// annotation if you've already got a newType one on the same class. Thus, we prohibit same.
		// However, you might want to override an inherited value with either.
		if (!ClassUtil.searchInheritance(
				clazz
			) {
				processAnnotationIfPresent(
					motorConfigurationType, clazz, newType
				)
			}
		) {
			ClassUtil.searchInheritance(
				clazz
			) {
				processAnnotationIfPresent(
					motorConfigurationType, clazz, oldType
				)
			}
		}
	}

	private fun <A : Annotation> processAnnotationIfPresent(
		motorConfigurationType: MotorConfigurationType, clazz: Class<*>, annotationType: Class<A>
	): Boolean {
		val annotation = clazz.getAnnotation(annotationType)
		if (annotation != null) {
			motorConfigurationType.processAnnotation(annotation)
			return true
		}
		return false
	}

	private fun getTypeAnnotation(cls: Class<*>) =
		cls.annotations.firstOrNull { typeAnnotationsList.contains(it.annotationClass.java) }

	private fun motorTypeFromDeprecatedAnnotation(
		cls: Class<*>, source: ClassSource
	): UserConfigurationType? {
		val motorTypeAnnotation: MotorType = cls.getAnnotation(MotorType::class.java) ?: return null
		val motorType = MotorConfigurationType(
			cls, getXmlTag(motorTypeAnnotation), source
		)
		motorType.processAnnotation(motorTypeAnnotation)
		processMotorSupportAnnotations(cls, motorType)
		motorType.finishedAnnotations(cls)
		// There's some things we need to check about the actual class
		if (!checkAnnotationParameterConstraints(motorType)) return null

		return motorType
	}

	private fun processMotorSupportAnnotations(clazz: Class<*>, motorType: MotorConfigurationType) {
		motorType.processAnnotation(findAnnotation(clazz, DistributorInfo::class.java))

		// Can't have both old and new local declarations (pick your horse!), but local definitions
		// override inherited ones
		processNewOldAnnotations(
			motorType,
			clazz,
			ExpansionHubPIDFVelocityParams::class.java,
			ExpansionHubMotorControllerVelocityParams::class.java
		)
		processNewOldAnnotations(
			motorType,
			clazz,
			ExpansionHubPIDFPositionParams::class.java,
			ExpansionHubMotorControllerPositionParams::class.java
		)
	}

	@Suppress("deprecation")
	private fun i2cTypeFromDeprecatedAnnotation(
		cls: Class<*>, source: ClassSource
	): UserConfigurationType? {
		if (!isHardwareDevice(cls)) return null
		val i2cSensorAnnotation: I2cSensor = cls.getAnnotation(I2cSensor::class.java) ?: return null

		@Suppress("UNCHECKED_CAST") val sensorType = I2cDeviceConfigurationType(
			cls as Class<out HardwareDevice>, getXmlTag(i2cSensorAnnotation), source
		)
		sensorType.processAnnotation(i2cSensorAnnotation)
		sensorType.finishedAnnotations(cls)

		if (!checkInstantiableTypeConstraints(sensorType)) return null
		return sensorType
	}

	private fun createAppropriateConfigurationType(
		specificTypeAnnotation: Annotation,
		devicePropertiesAnnotation: DeviceProperties,
		cls: Class<*>,
		source: ClassSource
	): UserConfigurationType {
		var configurationType: UserConfigurationType? = null

		@Suppress("name_shadowing", "UNCHECKED_CAST") val cls = cls as Class<out HardwareDevice>
		when (specificTypeAnnotation) {
			is ServoType -> {
				configurationType = ServoConfigurationType(
					cls, getXmlTag(devicePropertiesAnnotation), source
				)
				configurationType.processAnnotation(specificTypeAnnotation)
			}

			is com.qualcomm.robotcore.hardware.configuration.annotations.MotorType -> {
				configurationType = MotorConfigurationType(
					cls, getXmlTag(devicePropertiesAnnotation), source
				)
				processMotorSupportAnnotations(cls, configurationType)
				configurationType.processAnnotation(specificTypeAnnotation)
			}

			is AnalogSensorType -> {
				configurationType = AnalogSensorConfigurationType(
					cls, getXmlTag(devicePropertiesAnnotation), source
				)
			}

			is DigitalIoDeviceType -> {
				configurationType = DigitalIoDeviceConfigurationType(
					cls, getXmlTag(devicePropertiesAnnotation), source
				)
			}

			is I2cDeviceType -> {
				configurationType = I2cDeviceConfigurationType(
					cls, getXmlTag(devicePropertiesAnnotation), source
				)
			}
		}
		return configurationType!!
	}

	/**
	 * we start guessing that it is available, and we'll turn this off if it isn't
	 */
	@Suppress("ObjectPrivatePropertyName")
	private var `SDK-10-3-0-AVAILABLE` = true
	private fun scan(loader: ClassLoader, cls: Class<*>, source: ClassSource) {
		motorTypeFromDeprecatedAnnotation(cls, source)?.let {
			configurationTypes[loader]!!.add(it)
			return
		}
		i2cTypeFromDeprecatedAnnotation(cls, source)?.let {
			configurationTypes[loader]!!.add(it)
			return
		}

		val specificTypeAnnotation = getTypeAnnotation(cls) ?: return

		if (`SDK-10-3-0-AVAILABLE`) {
			try {
				// NOTE: this is made into a lambda in order to make it a separate class
				@Suppress("LocalVariableName")
				val `SDK-10-3-0` = Outer@{
					cls.getAnnotation(DevicesProperties::class.java)?.let { devicesProperties ->
						if (specificTypeAnnotation is ServoTypes) devicesProperties.value.forEach { deviceProperties ->
							val xmlTag = getXmlTag(deviceProperties)
							val servoType =
								specificTypeAnnotation.value.firstOrNull { it.xmlTag == xmlTag } ?: run {
									Logger.e(
										TAG,
										"Could not find paired @ServoType annotation for repeated annotation type with tag: $xmlTag"
									)
									return@Outer true
								}
							addConfigurationType(servoType, deviceProperties, cls, source, loader)
							return@Outer true
						}
						else {
							Logger.e(TAG, "Unsupported repeated annotation type: $specificTypeAnnotation")
							return@Outer true
						}
					}
					return@Outer false
				}
				if (`SDK-10-3-0`()) return
			}
			catch (e: ClassNotFoundException) {
				`SDK-10-3-0-AVAILABLE` = false
				Logger.v(TAG, "SDK version < 10.3.0, fallback behaviour enabled safely")
			}
			catch (e: NoClassDefFoundError) {
				`SDK-10-3-0-AVAILABLE` = false
				Logger.v(TAG, "SDK version < 10.3.0, fallback behaviour enabled safely")
			}
		}

		val devicePropertiesAnnotation = cls.getAnnotation(DeviceProperties::class.java) ?: run {
			reportConfigurationError("Class ${cls.simpleName} annotated with $specificTypeAnnotation is missing @DeviceProperties annotation.")
			return
		}

		addConfigurationType(specificTypeAnnotation, devicePropertiesAnnotation, cls, source, loader)
	}

	private fun addConfigurationType(specificTypeAnnotation: Annotation, devicePropertiesAnnotation: DeviceProperties, cls: Class<*>, source: ClassSource, loader: ClassLoader) {
		val configurationType = createAppropriateConfigurationType(
			specificTypeAnnotation, devicePropertiesAnnotation, cls, source
		)
		configurationType.processAnnotation(devicePropertiesAnnotation)
		configurationType.finishedAnnotations(cls)
		if (configurationType.annotatedClassIsInstantiable()) {
			if (checkInstantiableTypeConstraints(configurationType as InstantiableUserConfigurationType)) {
				configurationTypes[loader]!!.add(configurationType)
			}
		} else {
			if (checkAnnotationParameterConstraints(configurationType)) {
				configurationTypes[loader]!!.add(configurationType)
			}
		}
	}
}