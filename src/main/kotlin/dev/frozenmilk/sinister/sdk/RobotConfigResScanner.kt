package dev.frozenmilk.sinister.sdk

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.qualcomm.ftccommon.configuration.RobotConfigFileManager
import dev.frozenmilk.sinister.Scanner
import dev.frozenmilk.sinister.targeting.WideSearch
import org.firstinspires.ftc.robotcore.internal.opmode.OnBotJavaDeterminer
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

open class RobotConfigResScanner(protected val resources: Resources, protected val typeAttributeValue: String) : Scanner {
    constructor(context: Context, typeAttributeValue: String) : this(context.resources, typeAttributeValue)
    constructor(typeAttributeValue: String) : this(AppUtil.getDefContext(), typeAttributeValue)

    protected val xmlIdCollection = mutableMapOf<ClassLoader, MutableList<Int>>()
    private var currentXmlIdLoaderList: MutableList<Int>? = null

    val xmlIds: List<Int>
        get() = xmlIdCollection.values.flatten()

    //
    // Scanner
    //

    @Suppress("LeakingThis")
    override val loadAdjacencyRule = afterConfiguration()
    @Suppress("LeakingThis")
    override val unloadAdjacencyRule = beforeConfiguration()
    override val targets = WideSearch()

    override fun beforeScan(loader: ClassLoader) {
        currentXmlIdLoaderList = xmlIdCollection.getOrPut(loader) { mutableListOf() }.apply {
            clear()
        }
    }

    override fun scan(loader: ClassLoader, cls: Class<*>) {
        // We don't support robot configurations in external libraries.
        if (OnBotJavaDeterminer.isExternalLibraries(cls)) return

        if (cls.getName().endsWith("R\$xml")) {
            /*
             * Pull out all the R.xml classes and then filter all the xml files for robot configurations.
             * Create a list of robot configurations to be used elsewhere.
             */
            cls.fields.forEach {
                try {
                    if (it.type.equals(Integer.TYPE)) {
                        val id = it.getInt(cls)
                        if (isRobotConfiguration(resources.getXml(id))) {
                            currentXmlIdLoaderList!!.add(id)
                        }
                    }
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun afterScan(loader: ClassLoader) {
        currentXmlIdLoaderList = null
    }

    override fun unload(loader: ClassLoader, cls: Class<*>) {}

    override fun afterUnload(loader: ClassLoader) {
        xmlIdCollection.remove(loader)
    }

    /**
     * If the root element of the XML is the indicated tag, then returns the value of the
     * attributeName'd attribute thereof, or defaultValue if the attribute does not exist. If
     * the root element is not of the indicated tag, then null is returned.
     */
    fun getRootAttribute(
		xpp: XmlResourceParser,
		rootElement: String,
		attributeName: String?,
		defaultValue: String?
    ): String? {
        try {
            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG) {
                    if (xpp.name != rootElement) {
                        return null
                    }
                    // Attributes in XML are by definition unordered within their element;
                    // getAttributeValue() finds the attribute we're looking for (if it's there)
                    // no matter what order the attributes were declared in.
                    val result = xpp.getAttributeValue(null, attributeName)
                    return result ?: defaultValue
                }
                xpp.next()
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    /*
     * A simple method of checking to see if this xml file is a robot configuration file.
     *
     * Expects (e.g.) <Robot type="FirstInspires-FTC"> as the root XML element
     */
    private fun isRobotConfiguration(xpp: XmlResourceParser): Boolean {
        return typeAttributeValue == getRootAttribute(
            xpp,
            robotConfigRootTag,
            robotConfigRootTypeAttribute,
            null
        )
    }


    private companion object {
        const val robotConfigRootTag = "Robot"
        const val robotConfigRootTypeAttribute = "type"
    }
}

// these override sdk systems

@Suppress("unused")
private object IdResFilter : RobotConfigResScanner(RobotConfigFileManager.getRobotConfigTypeAttribute()) {
    init {
        RobotConfigFileManager.setXmlResourceIdSupplier(this::xmlIds)
    }
}

@Suppress("unused")
private object IdTemplateResFilter : RobotConfigResScanner(RobotConfigFileManager.getRobotConfigTemplateAttribute()) {
    init {
        RobotConfigFileManager.setXmlResourceTemplateIdSupplier(this::xmlIds)
    }
}
