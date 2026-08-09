package com.swordfish.chimeroid.lib.logging

import android.util.Log
import timber.log.Timber
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

class TimberLoggingHandler : Handler() {
    @Throws(SecurityException::class)
    override fun close() {
    }

    override fun flush() {}

    override fun publish(record: LogRecord) {
        val tag = loggerNameToTag(record.loggerName)
        val level = getAndroidLevel(record.level)
        Timber.tag(tag).log(level, record.message)
    }

    private fun getAndroidLevel(level: Level): Int {
        val value = level.intValue()
        return when {
            value >= 1000 -> Log.ERROR
            value >= 900 -> Log.WARN
            value >= 800 -> Log.INFO
            else -> Log.DEBUG
        }
    }

    private fun loggerNameToTag(loggerName: String?): String {
        if (loggerName == null) {
            return "null"
        }
        val length = loggerName.length
        if (length <= 23) {
            return loggerName
        }
        val lastPeriod = loggerName.lastIndexOf(".")
        return if (length - (lastPeriod + 1) <= 23) {
            loggerName.substring(lastPeriod + 1)
        } else {
            loggerName.substring(loggerName.length - 23)
        }
    }
}
