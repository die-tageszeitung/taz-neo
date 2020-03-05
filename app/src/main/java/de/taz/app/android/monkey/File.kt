package de.taz.app.android.monkey

import java.io.BufferedReader
import java.io.File
import java.io.IOException

@Throws(IOException::class)
fun File.sameContentAs(otherFile: File): Boolean {
    val otherBufferedReader = otherFile.bufferedReader()
    return sameContentAs(otherBufferedReader)
}

@Throws(IOException::class)
fun File.sameContentAs(otherBufferedReader: BufferedReader): Boolean {
    val bufferedReader = this.bufferedReader()

    var areEqual = true

    try {
        var line = bufferedReader.readLine()
        var otherLine = otherBufferedReader.readLine()

        while (line != null || otherLine != null) {
            if (line == null || otherLine == null) {
                areEqual = false
                break
            } else if (line != otherLine) {
                areEqual = false;
                break
            }

            line = bufferedReader.readLine()
            otherLine = otherBufferedReader.readLine()
        }
    } finally {
        bufferedReader.close()
        otherBufferedReader.close()
    }

    return areEqual
}