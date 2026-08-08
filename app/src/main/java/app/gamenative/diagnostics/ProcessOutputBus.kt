package app.gamenative.diagnostics

import app.gamenative.BuildConfig
import com.winlator.core.Callback
import com.winlator.core.ProcessHelper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

data class ProcessOutputRecord(
    val launchId: String?,
    val subsystem: String,
    val stream: String,
    val line: String,
)

fun interface ProcessOutputSubscriber {
    fun onOutput(record: ProcessOutputRecord)
}

/**
 * A process-output fanout that owns one persistent ProcessHelper callback. Screen-local debug
 * listeners may still come and go, but cannot clear this diagnostic subscriber.
 */
object ProcessOutputBus {
    private const val NORMAL_CAPTURE_LIMIT = 256
    private const val DIAGNOSTIC_CAPTURE_LIMIT = 4_096

    private val subscribers = CopyOnWriteArraySet<ProcessOutputSubscriber>()
    private val capturedLineCount = AtomicInteger(0)

    @Volatile
    private var installed = false
    @Volatile
    private var verboseCaptureEnabled = BuildConfig.DEBUG

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        DiagnosticSession.record(
            subsystem = "process_output",
            eventName = "persistent_subscriber_installed",
            fields = mapOf("verboseCaptureEnabled" to verboseCaptureEnabled),
        )
    }

    fun setVerboseCaptureEnabled(enabled: Boolean) {
        verboseCaptureEnabled = enabled
        capturedLineCount.set(0)
        DiagnosticSession.record(
            subsystem = "process_output",
            eventName = "capture_policy_changed",
            fields = mapOf("verboseCaptureEnabled" to enabled),
        )
    }

    fun subscribe(subscriber: ProcessOutputSubscriber) {
        subscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: ProcessOutputSubscriber) {
        subscribers.remove(subscriber)
    }

    @JvmStatic
    fun publish(pid: Int, subsystem: String, stream: String, rawLine: String) {
        val count = capturedLineCount.incrementAndGet()
        val captureLimit = if (verboseCaptureEnabled) DIAGNOSTIC_CAPTURE_LIMIT else NORMAL_CAPTURE_LIMIT

        if (count > captureLimit) {
            if (count == captureLimit + 1) {
                val record = ProcessOutputRecord(
                    launchId = DiagnosticSession.currentLaunchId,
                    subsystem = "process_output",
                    stream = stream,
                    line = "Process output capture capped at $captureLimit lines.",
                )
                DiagnosticSession.record(
                    severity = DiagnosticSeverity.WARN,
                    subsystem = "process_output",
                    eventName = "capture_capped",
                    message = record.line,
                    fields = mapOf("limit" to captureLimit),
                    launchId = record.launchId,
                )
                subscribers.forEach { subscriber ->
                    runCatching { subscriber.onOutput(record) }
                }
            }
            return
        }

        val record = ProcessOutputRecord(
            launchId = DiagnosticSession.currentLaunchId,
            subsystem = subsystem,
            stream = stream,
            line = SecretRedactor.redact(rawLine),
        )
        DiagnosticSession.record(
            severity = DiagnosticSeverity.DEBUG,
            subsystem = "process_output",
            eventName = "guest_output",
            message = record.line,
            fields = mapOf("stream" to record.stream, "pid" to pid),
            launchId = record.launchId,
        )
        subscribers.forEach { subscriber ->
            runCatching { subscriber.onOutput(record) }
        }
    }
}
