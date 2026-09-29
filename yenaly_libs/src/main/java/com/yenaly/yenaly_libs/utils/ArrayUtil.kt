@file:JvmName("ArrayUtil")

package com.yenaly.yenaly_libs.utils

import java.util.stream.Stream

fun IntArray.toStringArray(radix: Int = 10): Array<String> {
    return Array(size) { get(it).toString(radix) }
}

fun LongArray.toStringArray(radix: Int = 10): Array<String> {
    return Array(size) { get(it).toString(radix) }
}

fun FloatArray.toStringArray(): Array<String> {
    return Array(size) { get(it).toString() }
}

fun DoubleArray.toStringArray(): Array<String> {
    return Array(size) { get(it).toString() }
}

inline fun <reified T> Stream<*>.toTypedArray(): Array<T?> =
    toArray { size -> arrayOfNulls<T>(size) }

inline fun <I, reified O> List<I>.mapToArray(transform: (I) -> O): Array<O> {
    return Array(size) { i -> transform(this[i]) }
}
