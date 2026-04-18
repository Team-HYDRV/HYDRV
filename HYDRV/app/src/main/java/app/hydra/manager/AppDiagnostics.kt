package app.hydra.manager

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppDiagnostics {

    private const val FILE_NAME = "diagnostics.log"
    private const val TRACE_FILE_NAME = "performance_trace.log"
    private const val MAX_LINES = 200
    private const val MAX_TRACE_LINES = 240
    private val urlPattern = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val recentTraceKeys = ConcurrentHashMap<String, Long>()

    fun log(context: Context, category: String, message: String, throwable: Throwable? = null) {
        val safeMessage = redact(message.trim().ifBlank { "No details" })
        val exceptionPart = throwable?.let {
            " | ${it.javaClass.simpleName}: ${redact(it.message.orEmpty())}"
        }.orEmpty()
        val line = "${timestamp()} [$category] $safeMessage$exceptionPart"

        runCatching {
            appendLine(
                context = context,
                fileName = FILE_NAME,
                maxLines = MAX_LINES,
                line = line
            )
        }
    }

    fun trace(context: Context, category: String, stage: String, detail: String = "") {
        val safeStage = redact(stage.trim().ifBlank { "unknown" })
        val safeDetail = redact(detail.trim())
        val detailPart = if (safeDetail.isBlank()) "" else " | $safeDetail"
        val line = "${timestampWithMillis()} [$category] $safeStage | uptime=${SystemClock.elapsedRealtime()}ms$detailPart"

        runCatching {
            appendLine(
                context = context,
                fileName = TRACE_FILE_NAME,
                maxLines = MAX_TRACE_LINES,
                line = line
            )
        }
    }

    fun traceLimited(
        context: Context,
        category: String,
        stage: String,
        detail: String = "",
        dedupeKey: String,
        cooldownMs: Long = 250L
    ) {
        val now = SystemClock.elapsedRealtime()
        val previous = recentTraceKeys[dedupeKey]
        if (previous != null && now - previous < cooldownMs) return
        recentTraceKeys[dedupeKey] = now
        trace(context, category, stage, detail)
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

    fun readTrace(context: Context): String {
        return runCatching {
            File(context.applicationContext.filesDir, TRACE_FILE_NAME)
                .takeIf { it.exists() }
                ?.readText()
                ?.let(::redact)
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
    }

    private fun appendLine(context: Context, fileName: String, maxLines: Int, line: String) {
        val file = File(context.applicationContext.filesDir, fileName)
        val existing = if (file.exists()) file.readLines().takeLast(maxLines - 1) else emptyList()
        file.writeText((existing + line).joinToString(separator = "\n", postfix = "\n"))
    }

    private fun redact(text: String): String {
        return urlPattern.replace(text, "[redacted-url]")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun timestampWithMillis(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
}
