package dev.frozenmilk.sinister

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

object DexUsageExample {
    fun load(context: Context) {
        val apkPath =  context.packageCodePath
        try {
            ZipFile(apkPath).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) { //app has multiple dex files
                        zipFile.getInputStream(entry).use { inputStream ->
                            val dexBytes = inputStream.readBytes()
                            val dexBuffer =
                                ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)
                            val classNames = dexBuffer.extractClassNamesFromDex()

                            //TODO do something with classes
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // handle errors
        }
    }
}