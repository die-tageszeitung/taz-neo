package de.taz.app.android.util

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 *
 * Based on https://github.com/anupthegit/WOFFToTTFJava/ under the The MIT License (MIT)
 *
 * Copyright (c) 2015 anupthegit
 *
 */

class WoffConverter {
    private val woffHeaders =
        HashMap<String, Number>()
    private val tableRecordEntries =
        ArrayList<HashMap<String, Number>>()
    private var offset = 0
    private var readOffset = 0

    @Throws(
        InvalidWoffException::class,
        IOException::class,
        DataFormatException::class
    )
    fun convertToTTFByteArray(woffFileStream: InputStream): ByteArray {
        val ttfOutputStream =
            convertToTTFOutputStream(woffFileStream)
        return ttfOutputStream.toByteArray()
    }

    @Throws(
        InvalidWoffException::class,
        IOException::class,
        DataFormatException::class
    )
    fun convertToTTFOutputStream(woffFileStream: InputStream): ByteArrayOutputStream {
        getHeaders(DataInputStream(woffFileStream))
        if (woffHeaders["signature"] as Int? != 0x774F4646) {
            throw InvalidWoffException("Invalid woff file")
        }
        val ttfOutputStream = ByteArrayOutputStream()
        writeOffsetTable(ttfOutputStream)
        getTableRecordEntries(DataInputStream(woffFileStream))
        writeTableRecordEntries(ttfOutputStream)
        writeFontData(woffFileStream, ttfOutputStream)
        return ttfOutputStream
    }

    @Throws(IOException::class)
    private fun getHeaders(woffFileStream: DataInputStream) {
        readTableData(woffFileStream, woffHeaderFormat, woffHeaders)
    }

    @Throws(IOException::class)
    private fun writeOffsetTable(ttfOutputStream: ByteArrayOutputStream) {
        ttfOutputStream.write(getBytes((woffHeaders["flavor"] as Int?)!!))
        val numTables = woffHeaders["numTables"] as Int
        ttfOutputStream.write(getBytes(numTables.toShort()))
        var temp = numTables
        var searchRange = 16
        var entrySelector: Short = 0
        while (temp > 1) {
            temp = temp shr 1
            entrySelector++
            searchRange = searchRange shl 1
        }
        val rangeShift = (numTables * 16 - searchRange).toShort()
        ttfOutputStream.write(getBytes(searchRange.toShort()))
        ttfOutputStream.write(getBytes(entrySelector))
        ttfOutputStream.write(getBytes(rangeShift))
        offset += 12
    }

    @Throws(IOException::class)
    private fun getTableRecordEntries(woffFileStream: DataInputStream) {
        val numTables = woffHeaders["numTables"] as Int
        for (i in 0 until numTables) {
            val tableDirectory =
                HashMap<String, Number>()
            readTableData(
                woffFileStream, tableRecordEntryFormat,
                tableDirectory
            )
            offset += 16
            tableRecordEntries.add(tableDirectory)
        }
    }

    @Throws(IOException::class)
    private fun writeTableRecordEntries(ttfOutputStream: ByteArrayOutputStream) {
        for (tableRecordEntry in tableRecordEntries) {
            ttfOutputStream.write(
                getBytes(
                    (tableRecordEntry["tag"] as Int?)!!
                )
            )
            ttfOutputStream.write(
                getBytes(
                    (tableRecordEntry["origChecksum"] as Int?)!!
                )
            )
            ttfOutputStream.write(getBytes(offset))
            ttfOutputStream.write(
                getBytes(
                    (tableRecordEntry["origLength"] as Int?)!!
                )
            )
            tableRecordEntry["outOffset"] = offset
            offset += (tableRecordEntry["origLength"] as Int?)!!
            if (offset % 4 != 0) {
                offset += 4 - offset % 4
            }
        }
    }

    @Throws(IOException::class, DataFormatException::class)
    private fun writeFontData(
        woffFileStream: InputStream,
        ttfOutputStream: ByteArrayOutputStream
    ) {
        for (tableRecordEntry in tableRecordEntries) {
            val tableRecordEntryOffset = tableRecordEntry["offset"] as Int
            val skipBytes = tableRecordEntryOffset - readOffset
            if (skipBytes > 0) woffFileStream.skip(skipBytes.toLong())
            readOffset += skipBytes
            val compressedLength = tableRecordEntry["compLength"] as Int
            val origLength = tableRecordEntry["origLength"] as Int
            val fontData = ByteArray(compressedLength)
            var inflatedFontData = ByteArray(origLength)
            var readBytes = 0
            while (readBytes < compressedLength) {
                readBytes += woffFileStream.read(
                    fontData, readBytes,
                    compressedLength - readBytes
                )
            }
            readOffset += compressedLength
            inflatedFontData = inflateFontData(
                compressedLength,
                origLength, fontData, inflatedFontData
            )
            ttfOutputStream.write(inflatedFontData)
            offset = ((tableRecordEntry["outOffset"] as Int?)!!
                    + (tableRecordEntry["origLength"] as Int?)!!)
            var padding = 0
            if (offset % 4 != 0) padding = 4 - offset % 4
            ttfOutputStream.write(getBytes(0), 0, padding)
        }
    }

    private fun inflateFontData(
        compressedLength: Int, origLength: Int,
        fontData: ByteArray, inflatedFontData: ByteArray
    ): ByteArray {
        var tmpInflatedFontData = inflatedFontData
        if (compressedLength != origLength) {
            val decompressor = Inflater()
            decompressor.setInput(fontData, 0, compressedLength)
            try {
                decompressor.inflate(tmpInflatedFontData, 0, origLength)
            } catch (e: DataFormatException) {
                throw InvalidWoffException("Malformed woff file")
            }
        } else tmpInflatedFontData = fontData
        return tmpInflatedFontData
    }

    private fun getBytes(i: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(i).array()
    }

    private fun getBytes(h: Short): ByteArray {
        return ByteBuffer.allocate(2).putShort(h).array()
    }

    @Throws(IOException::class)
    private fun readTableData(
        woffFileStream: DataInputStream,
        formatTable: LinkedHashMap<String, Int>,
        table: HashMap<String, Number>
    ) {
        val headerKeys: Iterator<String> = formatTable.keys.iterator()
        while (headerKeys.hasNext()) {
            val key = headerKeys.next()
            val size = formatTable[key]!!
            if (size == 2) {
                table[key] = woffFileStream.readUnsignedShort()
            } else if (size == 4) {
                table[key] = woffFileStream.readInt()
            }
            readOffset += size
        }
    }

    private val woffHeaderFormat: LinkedHashMap<String, Int> =
        LinkedHashMap<String, Int>().apply {
            put("signature", 4)
            put("flavor", 4)
            put("length", 4)
            put("numTables", 2)
            put("reserved", 2)
            put("totalSfntSize", 4)
            put("majorVersion", 2)
            put("minorVersion", 2)
            put("metaOffset", 4)
            put("metaLength", 4)
            put("metaOrigLength", 4)
            put("privOffset", 4)
            put("privOrigLength", 4)
        }

    private val tableRecordEntryFormat: LinkedHashMap<String, Int> =
        LinkedHashMap<String, Int>().apply {
            put("tag", 4)
            put("offset", 4)
            put("compLength", 4)
            put("origLength", 4)
            put("origChecksum", 4)
        }
}

class InvalidWoffException(message: String?) : RuntimeException(message)
