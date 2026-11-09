package com.yenaly.han1meviewer.util

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

object Bspatch {

    class PatchException(message: String) : IOException(message)

    private const val HEADER_SIZE = 32
    private const val CHUNK_SIZE = 64 * 1024
    private const val MAX_NEW_SIZE = 1L shl 30
    private const val MAGIC = "BSDIFF40"

    @Throws(IOException::class)
    fun apply(oldFile: File, patchFile: File, newFile: File) {
        val patchLength = patchFile.length()
        if (patchLength < HEADER_SIZE) throw PatchException("patch too short: $patchLength")

        val header = ByteArray(HEADER_SIZE)
        FileInputStream(patchFile).use { DataInputStream(it).readFully(header) }

        val magic = String(header, 0, 8, Charsets.US_ASCII)
        if (magic != MAGIC) throw PatchException("bad patch magic: $magic")

        val ctrlLen = offtin(header, 8)
        val diffLen = offtin(header, 16)
        val newSize = offtin(header, 24)
        if (ctrlLen < 0 || diffLen < 0 || newSize < 0 || newSize > MAX_NEW_SIZE) {
            throw PatchException("corrupt patch header")
        }
        val ctrlStart = HEADER_SIZE.toLong()
        val diffStart = ctrlStart + ctrlLen
        val extraStart = diffStart + diffLen
        if (extraStart > patchLength) throw PatchException("patch truncated")

        val ctrlIn = DataInputStream(openBz2(patchFile, ctrlStart))
        val diffIn = DataInputStream(openBz2(patchFile, diffStart))

        val extraIn = DataInputStream(
            if (extraStart < patchLength) openBz2(patchFile, extraStart)
            else ByteArrayInputStream(ByteArray(0))
        )

        try {
            RandomAccessFile(oldFile, "r").use { old ->
                val oldSize = old.length()
                BufferedOutputStream(FileOutputStream(newFile), CHUNK_SIZE).use { out ->
                    val ctrl = ByteArray(24)
                    val buf = ByteArray(CHUNK_SIZE)
                    val oldBuf = ByteArray(CHUNK_SIZE)
                    var oldPos = 0L
                    var newPos = 0L

                    while (newPos < newSize) {
                        ctrlIn.readFully(ctrl)
                        val diffCount = offtin(ctrl, 0)
                        val extraCount = offtin(ctrl, 8)
                        val seek = offtin(ctrl, 16)

                        if (diffCount < 0 || diffCount > newSize - newPos) {
                            throw PatchException("corrupt patch (diff length)")
                        }

                        var remaining = diffCount
                        while (remaining > 0) {
                            val n = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                            diffIn.readFully(buf, 0, n)

                            val lo = if (oldPos < 0) minOf(n.toLong(), -oldPos).toInt() else 0
                            val hi = if (oldPos >= oldSize) 0
                            else minOf(n.toLong(), oldSize - oldPos).toInt()
                            if (hi > lo) {
                                old.seek(oldPos + lo)
                                old.readFully(oldBuf, 0, hi - lo)
                                for (i in lo until hi) {
                                    buf[i] = (buf[i] + oldBuf[i - lo]).toByte()
                                }
                            }

                            out.write(buf, 0, n)
                            newPos += n
                            oldPos += n
                            remaining -= n
                        }

                        if (extraCount < 0 || extraCount > newSize - newPos) {
                            throw PatchException("corrupt patch (extra length)")
                        }
                        remaining = extraCount
                        while (remaining > 0) {
                            val n = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                            extraIn.readFully(buf, 0, n)
                            out.write(buf, 0, n)
                            newPos += n
                            remaining -= n
                        }

                        oldPos += seek
                    }
                }
            }
        } finally {
            runCatching { ctrlIn.close() }
            runCatching { diffIn.close() }
            runCatching { extraIn.close() }
        }

        if (newFile.length() != newSize) {
            throw PatchException("size mismatch: ${newFile.length()} != $newSize")
        }
    }

    private fun openBz2(file: File, offset: Long): InputStream {
        val fis = FileInputStream(file)
        try {
            fis.channel.position(offset)
            return BZip2CompressorInputStream(BufferedInputStream(fis, CHUNK_SIZE))
        } catch (t: Throwable) {
            runCatching { fis.close() }
            throw t
        }
    }

    private fun offtin(b: ByteArray, off: Int): Long {
        var y = (b[off + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) {
            y = (y shl 8) or (b[off + i].toLong() and 0xFF)
        }
        return if ((b[off + 7].toInt() and 0x80) != 0) -y else y
    }
}
