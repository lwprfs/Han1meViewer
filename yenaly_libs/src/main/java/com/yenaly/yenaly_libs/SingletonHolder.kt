package com.yenaly.yenaly_libs

abstract class SingleArgSingletonHolder<out T, in A>(private var constructor: ((A) -> T)?) {
    @Volatile
    private var instance: T? = null
    fun getInstance(arg: A): T = instance ?: synchronized(this) {
        instance ?: constructor!!(arg).also {
            instance = it
            constructor = null
        }
    }
}
