package app.hydra.manager

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDiagnostics {

    private const val FILE_NAME = "diagnostics.log"
    private const val MAX_LINES = 200
    private val urlPattern = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    fun log(context: Context, category: String, message: String, throwable: Throwable? = null) {
        val safeMessage = redact(message.trim().ifBlank { "No details" })
        val exceptionPart = throwable?.let {
            " | ${it.javaClass.simpleName}: ${redact(it.message.orEmpty())}"
        }.orEmpty()
        val line = "${timestamp()} [$category] $safeMessage$exceptionPart"

        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            val existing = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
            file.writeText((existing + line).joinToString(separator = "\n", postfix = "\n"))
        }
    }

    fun read(context: Context): String {
        return runCatching {
            File(context.applicationContext.filesDir, FILE_NAME)
                .takeIf { it.exists() }
                ?.readText()
                ?.let(::redact)
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
    }

    private fun redact(text: String): String {
        return urlPattern.replace(text, "[redacted-url]")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
