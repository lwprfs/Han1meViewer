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

/**
 * 标准 bsdiff 4.x（`BSDIFF40`）补丁的纯 Kotlin 实现，对应命令行的 `bspatch old new patch`。
 *
 * 补丁格式：
 * - 32 字节头：`"BSDIFF40"`、控制块压缩后长度、diff 块压缩后长度、新文件大小（均为 8 字节）
 * - 三个 bzip2 流：control / diff / extra
 *
 * 旧文件通过 [RandomAccessFile] 随机读取，新文件流式写出，因此不会把整个 APK 读进内存。
 */
object Bspatch {

    class PatchException(message: String) : IOException(message)

    private const val HEADER_SIZE = 32
    private const val CHUNK_SIZE = 64 * 1024
    private const val MAX_NEW_SIZE = 1L shl 30 // 1 GiB，防止损坏的补丁导致无限写入
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
        // 个别实现遇到空 extra 块时不写任何字节，这里容错
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

                        // 1) diff：new[i] = diff[i] + old[oldPos + i]
                        var remaining = diffCount
                        while (remaining > 0) {
                            val n = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                            diffIn.readFully(buf, 0, n)

                            // 本块中与旧文件 [0, oldSize) 有重叠的区间 [lo, hi)
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

                        // 2) extra：原样复制
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

                        // 3) 旧文件指针偏移
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

    /** bsdiff 的 8 字节小端「符号 + 绝对值」整数。 */
    private fun offtin(b: ByteArray, off: Int): Long {
        var y = (b[off + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) {
            y = (y shl 8) or (b[off + i].toLong() and 0xFF)
        }
        return if ((b[off + 7].toInt() and 0x80) != 0) -y else y
    }
}
