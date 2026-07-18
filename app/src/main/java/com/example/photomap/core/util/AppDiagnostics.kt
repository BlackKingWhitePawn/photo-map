package com.example.photomap.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppDiagnostics {
    private const val MaxEvents = 800
    private const val LogcatLineCount = 500
    private val lock = Any()
    private val events = ArrayDeque<String>()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun record(tag: String, message: String, throwable: Throwable? = null) {
        val line = buildString {
            append(timestampFormat.format(Date()))
            append(' ')
            append(tag)
            append(": ")
            append(message)
            if (throwable != null) {
                append(" | ")
                append(throwable::class.java.simpleName)
                throwable.message?.let { text ->
                    append(": ")
                    append(text)
                }
            }
        }
        synchronized(lock) {
            events.addLast(line)
            while (events.size > MaxEvents) {
                events.removeFirst()
            }
        }
    }

    fun createReport(context: Context, header: String): Uri {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "photo-map-${fileTimestampFormat.format(Date())}.log")
        file.writeText(
            buildString {
                appendLine("Traverse diagnostics")
                appendLine("Generated: ${timestampFormat.format(Date())}")
                appendLine()
                appendLine(header)
                appendLine()
                appendLine("Recent app events")
                snapshot().forEach { event -> appendLine(event) }
                appendLine()
                appendLine("Logcat tail")
                appendLine(readLogcatTail())
            }
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun snapshot(): List<String> {
        return synchronized(lock) { events.toList() }
    }

    private fun readLogcatTail(): String {
        return runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",
                    "-t",
                    LogcatLineCount.toString(),
                    "PhotoMapMap:D",
                    "PhotoMapMedia:D",
                    "*:S"
                )
            )
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
            if (!finished) {
                process.destroy()
                "$output\nlogcat export timed out"
            } else {
                output.ifBlank { "logcat returned no app-visible lines" }
            }
        }.getOrElse { error ->
            "logcat unavailable: ${error::class.java.simpleName}: ${error.message}"
        }
    }
}
