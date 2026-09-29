package com.yenaly.han1meviewer.worker

import androidx.work.ListenableWorker
import com.yenaly.yenaly_libs.utils.unsafeLazy

@JvmDefaultWithoutCompatibility
interface WorkerMixin {
    @Suppress("UNCHECKED_CAST", "SameParameterValue")
    fun <T : Any> ListenableWorker.inputData(key: String, def: T): Lazy<T> = unsafeLazy {
        when (def) {
            is String -> (inputData.getString(key) ?: def) as T
            is Int -> inputData.getInt(key, def) as T
            is Long -> inputData.getLong(key, def) as T
            is Boolean -> inputData.getBoolean(key, def) as T
            is Float -> inputData.getFloat(key, def) as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }
}
