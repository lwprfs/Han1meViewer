@file:Suppress("unused")
@file:JvmName("AesUtil")

package com.yenaly.yenaly_libs.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.Key
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_ALGORITHM = "AES"
private const val CIPHER_ALGORITHM_DEFAULT = "AES"
const val AES_CFB_NOPADDING = "AES/CFB/NoPadding"
const val AES_ECB_NOPADDING = "AES/ECB/NoPadding"

fun ByteArray.aesEncrypt(
    key: ByteArray,
    iv: ByteArray = ByteArray(16),
    algorithm: String = AES_CFB_NOPADDING
): ByteArray {
    val cipher = initCipher(Cipher.ENCRYPT_MODE, key, iv, algorithm)
    return cipher.doFinal(this)
}

fun ByteArray.aesDecrypt(
    key: ByteArray,
    iv: ByteArray = ByteArray(16),
    algorithm: String = AES_CFB_NOPADDING
): ByteArray {
    val cipher = initCipher(Cipher.DECRYPT_MODE, key, iv, algorithm)
    return cipher.doFinal(this)
}

fun File.aesEncrypt(
    key: ByteArray,
    iv: ByteArray,
    destFilePath: String,
    algorithm: String = AES_CFB_NOPADDING
): File? {
    return handleFile(Cipher.ENCRYPT_MODE, key, iv, algorithm, path, destFilePath)
}

fun File.aesDecrypt(
    key: ByteArray,
    iv: ByteArray,
    destFilePath: String,
    algorithm: String = AES_CFB_NOPADDING
): File? {
    return handleFile(Cipher.DECRYPT_MODE, key, iv, algorithm, path, destFilePath)
}

fun initAESKey(size: Int = 128): ByteArray {
    val kg = KeyGenerator.getInstance(KEY_ALGORITHM)
    kg.init(size)
    return kg.generateKey().encoded
}

private fun toKey(key: ByteArray): Key = SecretKeySpec(key, KEY_ALGORITHM)

fun initCipher(
    mode: Int,
    key: ByteArray,
    iv: ByteArray = ByteArray(16),
    algorithm: String
): Cipher {
    val k = toKey(key)
    val cipher = Cipher.getInstance(algorithm)
    val cipherAlgorithm = algorithm.uppercase(Locale.getDefault())
    if (cipherAlgorithm.contains("CFB") || cipherAlgorithm.contains("CBC")
        || cipherAlgorithm.contains("CTR")
    )
        cipher.init(mode, k, IvParameterSpec(iv))
    else
        cipher.init(mode, k)
    return cipher
}

private fun handleFile(
    mode: Int,
    key: ByteArray,
    iv: ByteArray,
    cipherAlgorithm: String = AES_CFB_NOPADDING,
    sourceFilePath: String,
    destFilePath: String
): File? {
    val sourceFile = File(sourceFilePath)
    val destFile = File(destFilePath)

    if (sourceFile.exists() && sourceFile.isFile) {
        if (!destFile.parentFile!!.exists()) destFile.parentFile!!.mkdirs()
        destFile.createNewFile()

        val inputStream = FileInputStream(sourceFile)
        val outputStream = FileOutputStream(destFile)
        val cipher = initCipher(mode, key, iv, cipherAlgorithm)
        val cin = CipherInputStream(inputStream, cipher)

        val b = ByteArray(1024)
        var read: Int
        do {
            read = cin.read(b)
            if (read > 0)
                outputStream.write(b, 0, read)
        } while (read > 0)

        outputStream.flush()
        cin.close()
        inputStream.close()
        outputStream.close()

        return destFile
    }
    return null
}
