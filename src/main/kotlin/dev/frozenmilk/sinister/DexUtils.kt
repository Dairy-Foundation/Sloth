package dev.frozenmilk.sinister

import java.nio.ByteBuffer

fun ByteBuffer.extractClassNamesFromDex(): List<String> {
    val classNames = ArrayList<String>()

    this.position(0)

    // Check DEX magic and version
    val magic = ByteArray(8)
    this.get(magic)
    val magicString = String(magic)
    if (!magicString.startsWith("dex\n") || magicString.length < 8) {
        throw IllegalArgumentException("Invalid DEX magic: $magicString")
    }

    // Read header values with proper unsigned handling
    this.position(32)
    val fileSize = this.getInt().toLong() and 0xFFFFFFFFL

    this.position(36)
    val headerSize = this.getInt().toLong() and 0xFFFFFFFFL

    this.position(56)
    val stringIdsSize = this.getInt().toLong() and 0xFFFFFFFFL
    val stringIdsOffset = this.getInt().toLong() and 0xFFFFFFFFL

    this.position(64)
    val typeIdsSize = this.getInt().toLong() and 0xFFFFFFFFL
    val typeIdsOffset = this.getInt().toLong() and 0xFFFFFFFFL

    // Validate sizes and offsets
    if (stringIdsSize <= 0 || typeIdsSize <= 0 ||
        stringIdsOffset < headerSize || typeIdsOffset < headerSize ||
        stringIdsOffset > fileSize || typeIdsOffset > fileSize
    ) {
        throw IllegalArgumentException("Invalid table sizes or offsets: stringIdsSize=$stringIdsSize, typeIdsSize=$typeIdsSize, stringIdsOffset=$stringIdsOffset, typeIdsOffset=$typeIdsOffset")
    }

    // Read string offsets
    this.position(stringIdsOffset.toInt())
    val stringOffsets = LongArray(stringIdsSize.toInt())
    for (i in 0 until stringIdsSize.toInt()) {
        stringOffsets[i] = this.getInt().toLong() and 0xFFFFFFFFL
    }

    // Extract class names
    this.position(typeIdsOffset.toInt())
    for (i in 0 until typeIdsSize.toInt()) {
        val stringIndex = this.getInt().toLong() and 0xFFFFFFFFL
        if (stringIndex >= stringIdsSize) continue

        val stringOffset = stringOffsets[stringIndex.toInt()]
        val originalPosition = this.position()

        try {
            this.position(stringOffset.toInt())
            val length = readUleb128(this)
            if (length > 1024) continue // Sanity check

            val stringBytes = ByteArray(length)
            this.get(stringBytes)
            val classDescriptor = String(stringBytes)

            if (classDescriptor.startsWith("L") && classDescriptor.endsWith(";")) {
                val className = classDescriptor
                    .substring(1, classDescriptor.length - 1)
                    .replace('/', '.')
                classNames.add(className)
            }
        } catch (e: Exception) {
            throw e //TODO: handle errors, panels used it's own logger here
        } finally {
            this.position(originalPosition)
        }
    }

    return classNames
}

private fun readUleb128(buffer: ByteBuffer): Int {
    var result = 0
    var shift = 0
    var byte: Int

    do {
        byte = buffer.get().toInt() and 0xFF
        result = result or ((byte and 0x7F) shl shift)
        shift += 7
    } while (byte and 0x80 != 0 && shift < 35) // Prevent infinite loop

    return result
}