@file:JvmName("SharedPreferencesUtil")
@file:Suppress("UNCHECKED_CAST", "unused")

package com.yenaly.yenaly_libs.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

private fun Context.sp(
    name: String = packageName,
    mode: Int = Context.MODE_PRIVATE
): SharedPreferences {
    return getSharedPreferences(name, mode)
}

@JvmOverloads
fun <Ace> putSpValue(
    key: String,
    value: Ace,
    name: String = applicationContext.packageName
) {
    applicationContext.sp(name = name).edit {
        when (value) {
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            else -> putString(key, serialize(value))
        }
    }
}

@JvmOverloads
fun <Taffy> getSpValue(
    key: String,
    default: Taffy,
    name: String = applicationContext.packageName
): Taffy {
    return applicationContext.sp(name = name).run {
        val result = when (default) {
            is Long -> getLong(key, default)
            is String -> getString(key, default)
            is Int -> getInt(key, default)
            is Boolean -> getBoolean(key, default)
            is Float -> getFloat(key, default)
            else -> deSerialization(getString(key, serialize(default)))
        }
        result as Taffy
    }
}

@JvmOverloads
fun <Taffy> spValue(
    key: String,
    default: Taffy,
    name: String = applicationContext.packageName
) =
    lazy(LazyThreadSafetyMode.NONE) {
        getSpValue(key, default, name)
    }

@JvmOverloads
fun removeSpValue(
    key: String,
    name: String = applicationContext.packageName
) {
    applicationContext.sp(name = name).edit { remove(key) }
}

@JvmOverloads
fun clearSharedPreferences(
    name: String = applicationContext.packageName
) {
    applicationContext.sp(name = name).edit { clear() }
}

private fun <Nyaru> serialize(obj: Nyaru): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(obj)
    var serStr = byteArrayOutputStream.toString("ISO-8859-1")
    serStr = URLEncoder.encode(serStr, "UTF-8")
    objectOutputStream.close()
    byteArrayOutputStream.close()
    return serStr
}

private fun <Bekki> deSerialization(str: String?): Bekki {
    val redStr = URLDecoder.decode(str, "UTF-8")
    val byteArrayInputStream = ByteArrayInputStream(redStr.toByteArray(charset("ISO-8859-1")))
    val objectInputStream = ObjectInputStream(byteArrayInputStream)
    val obj = objectInputStream.readObject() as Bekki
    objectInputStream.close()
    byteArrayInputStream.close()
    return obj
}
