package app.gamenative.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.Properties
import java.util.UUID

enum class DiagnosticSeverity(val androidPriority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
}

data class DiagnosticEvent(
    val sessionId: String,
    val launchId: String?,
    val wallClockMs: Long,
    val monotonicMs: Long,
    val processName: String,
    val pid: Int,
    val threadName: String,
    val severity: DiagnosticSeverity,
    val subsystem: String,
    val eventName: String,
    val message: String,
    val fields: Map<String, String>,
    val durationMs: Long? = null,
    val parentOperationId: String? = null,
) {
    fun toSummaryLine(): String {
        val launch = launchId?.let { " launch=$it" }.orEmpty()
        val details = if (fields.isEmpty()) "" else " fields=${fields.entries.joinToString { "${it.key}=${it.value}" }}"
        return "$wallClockMs ${severity.name} [$subsystem/$eventName]$launch $message$details"
    }

    fun toJsonLine(): String = buildString {
        append('{')
        appendJson("sessionId", sessionId)
        append(',')
        appendJson("launchId", launchId)
        append(',')
        appendJson("wallClockMs", wallClockMs.toString(), quoted = false)
        append(',')
        appendJson("monotonicMs", monotonicMs.toString(), quoted = false)
        append(',')
        appendJson("processName", processName)
        append(',')
        appendJson("pid", pid.toString(), quoted = false)
        append(',')
        appendJson("threadName", threadName)
        append(',')
        appendJson("severity", severity.name)
        append(',')
        appendJson("subsystem", subsystem)
        append(',')
        appendJson("event", eventName)
        append(',')
        appendJson("message", message)
        if (durationMs != null) {
            append(',')
            appendJson("durationMs", durationMs.toString(), quoted = false)
        }
        if (parentOperationId != null) {
            append(',')
            appendJson("parentOperationId", parentOperationId)
        }
        append(",\"fields\":{")
        fields.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendJson(key, value)
        }
        append("}}")
    }

    private fun StringBuilder.appendJson(key: String, value: String?, quoted: Boolean = true) {
        append('"').append(key.jsonEscape()).append("\":")
        if (value == null) {
            append("null")
        } else if (quoted) {
            append('"').append(value.jsonEscape()).append('"')
        } else {
            append(value)
        }
    }
}

/**
 * Process-safe diagnostic session owner. Structured event files are stored in app-specific
 * external storage, with one JSONL file per Android process to avoid cross-process append races.
 */
object DiagnosticSession {
    private const val LOG_TAG = "GNXRDiag"
    private const val MAX_RECENT_EVENTS = 256
    private const val MAX_SESSION_DIRECTORIES = 8
    private const val MAX_MESSAGE_LENGTH = 8_192

    private val lock = Any()
    private val recentEvents = ArrayDeque<String>(MAX_RECENT_EVENTS)

    @Volatile
    private var initialized = false
    @Volatile
    private var activeLaunchId: String? = null

    private var appContext: Context? = null
    private var sessionDirectory: File? = null
    private var sessionId: String = "uninitialized"
    private var processName: String = "unknown"
    private var processFileName: String = "unknown"

    val currentSessionId: String get() = sessionId
    val currentLaunchId: String? get() = activeLaunchId

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return

            appContext = context.applicationContext
            processName = resolveProcessName(context.applicationContext)
            processFileName = processName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val root = resolveDiagnosticsRoot(context.applicationContext)
            root.mkdirs()
            sessionId = resolveSessionId(root, context.applicationContext, processName)
            sessionDirectory = File(root, "session-$sessionId").apply { mkdirs() }
            pruneOldSessions(root, sessionDirectory)
            initialized = true
        }

        record(
            severity = DiagnosticSeverity.INFO,
            subsystem = "boot",
            eventName = "diagnostic_session_started",
            fields = mapOf(
                "androidSdk" to Build.VERSION.SDK_INT,
                "androidRelease" to Build.VERSION.RELEASE.orEmpty(),
                "supportedAbis" to Build.SUPPORTED_ABIS.joinToString(","),
            ),
        )
    }

    fun beginLaunch(appId: String, fields: Map<String, Any?> = emptyMap()): String {
        val launchId = "launch-${UUID.randomUUID()}"
        activeLaunchId = launchId
        record(
            subsystem = "launch",
            eventName = "launch_started",
            fields = fields + ("appId" to appId),
            launchId = launchId,
        )
        return launchId
    }

    fun attachLaunch(launchId: String) {
        activeLaunchId = launchId
        record(
            subsystem = "launch",
            eventName = "launch_attached",
            fields = mapOf("attachedLaunchId" to launchId),
            launchId = launchId,
        )
    }

    fun endLaunch(outcome: String, fields: Map<String, Any?> = emptyMap()) {
        val launchId = activeLaunchId
        record(
            severity = DiagnosticSeverity.INFO,
            subsystem = "launch",
            eventName = "launch_terminal",
            fields = fields + ("outcome" to outcome),
            launchId = launchId,
        )
        if (activeLaunchId == launchId) activeLaunchId = null
    }

    fun record(
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        subsystem: String,
        eventName: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
        launchId: String? = activeLaunchId,
        durationMs: Long? = null,
        parentOperationId: String? = null,
    ) {
        val event = DiagnosticEvent(
            sessionId = sessionId,
            launchId = launchId,
            wallClockMs = System.currentTimeMillis(),
            monotonicMs = SystemClock.elapsedRealtime(),
            processName = processName,
            pid = Process.myPid(),
            threadName = Thread.currentThread().name,
            severity = severity,
            subsystem = subsystem,
            eventName = eventName,
            message = SecretRedactor.redact(message).take(MAX_MESSAGE_LENGTH),
            fields = fields.entries
                .associate { (key, value) -> key to SecretRedactor.redactField(key, value) }
                .toSortedMap(),
            durationMs = durationMs,
            parentOperationId = parentOperationId,
        )

        Log.println(
            event.severity.androidPriority,
            LOG_TAG,
            "[${event.sessionId}/${event.launchId ?: "no-launch"}] ${event.subsystem}/${event.eventName} ${event.message}",
        )

        synchronized(lock) {
            if (!initialized) return
            appendRecent(event.toSummaryLine())
            writeEvent(event)
        }
    }

    fun recordThrowable(
        subsystem: String,
        eventName: String,
        throwable: Throwable,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        record(
            severity = DiagnosticSeverity.ERROR,
            subsystem = subsystem,
            eventName = eventName,
            message = throwable.message ?: throwable.javaClass.name,
            fields = fields + mapOf(
                "throwableType" to throwable.javaClass.name,
                "stackTrace" to Log.getStackTraceString(throwable),
            ),
        )
    }

    fun recentSummary(maxEvents: Int = 64): String = synchronized(lock) {
        recentEvents.toList().takeLast(maxEvents).joinToString(separator = "\n")
    }

    private fun appendRecent(line: String) {
        while (recentEvents.size >= MAX_RECENT_EVENTS) recentEvents.removeFirst()
        recentEvents.addLast(line)
    }

    private fun writeEvent(event: DiagnosticEvent) {
        val directory = sessionDirectory ?: return
        runCatching {
            File(directory, "events-$processFileName.jsonl").appendText("${event.toJsonLine()}\n", Charsets.UTF_8)
            File(directory, "summary-$processFileName.log").appendText("${event.toSummaryLine()}\n", Charsets.UTF_8)
        }.onFailure {
            Log.e(LOG_TAG, "Failed to write diagnostic event", it)
        }
    }

    private fun resolveDiagnosticsRoot(context: Context): File =
        context.getExternalFilesDir("diagnostics") ?: File(context.filesDir, "diagnostics")

    private fun resolveSessionId(root: File, context: Context, currentProcessName: String): String {
        val activeRecord = File(root, "active-session.properties")
        val isMainProcess = currentProcessName == context.packageName
        if (!isMainProcess) {
            readSessionId(activeRecord)?.let { return it }
        }

        val newSessionId = "session-${UUID.randomUUID()}"
        runCatching {
            val properties = Properties().apply {
                setProperty("sessionId", newSessionId)
                setProperty("startedAtMs", System.currentTimeMillis().toString())
                setProperty("ownerProcess", currentProcessName)
            }
            FileOutputStream(activeRecord).use { properties.store(it, "GameNativeXR diagnostic session") }
        }.onFailure {
            Log.w(LOG_TAG, "Could not persist active diagnostic session ID", it)
        }
        return newSessionId
    }

    private fun readSessionId(activeRecord: File): String? = runCatching {
        if (!activeRecord.isFile) return@runCatching null
        val properties = Properties()
        activeRecord.inputStream().use(properties::load)
        properties.getProperty("sessionId")?.takeIf { it.startsWith("session-") }
    }.getOrNull()

    private fun pruneOldSessions(root: File, current: File?) {
        root.listFiles { file -> file.isDirectory && file.name.startsWith("session-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_SESSION_DIRECTORIES)
            ?.filter { it != current }
            ?.forEach { stale -> runCatching { stale.deleteRecursively() } }
    }

    @Suppress("DEPRECATION")
    private fun resolveProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            ?: context.packageName
    }
}

private fun String.jsonEscape(): String = buildString(length) {
    for (character in this@jsonEscape) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
    }
}
