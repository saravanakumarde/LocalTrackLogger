package com.saravana.localtracklogger

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Saves the stack trace of an uncaught exception to a file, so the next launch can show it.
 * Without this a crash leaves no trace unless the phone is connected to a computer.
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE_NAME).writeText(report(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun report(thread: Thread, error: Throwable): String = buildString {
        appendLine("Local Track Logger ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        appendLine("Time: ${Date()}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        appendLine()
        append(Log.getStackTraceString(error))
    }
}
