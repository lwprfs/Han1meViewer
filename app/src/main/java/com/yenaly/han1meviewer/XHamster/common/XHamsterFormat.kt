package com.yenaly.han1meviewer.XHamster.common

object XHamsterFormat {

    fun duration(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun count(value: Long?): String? {
        if (value == null || value < 0) return null
        return when {
            value >= 1_000_000_000L -> trim(value / 1_000_000_000.0) + "B"
            value >= 1_000_000L -> trim(value / 1_000_000.0) + "M"
            value >= 1_000L -> trim(value / 1_000.0) + "K"
            else -> value.toString()
        }
    }

    private fun trim(d: Double): String {
        val s = "%.1f".format(d)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
