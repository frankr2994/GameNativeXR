package app.gamenative.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ProcessOutputBusTest {

    @Test
    fun testOutputIsRedacted() {
        // Reset state
        ProcessOutputBus.setVerboseCaptureEnabled(false)

        val records = CopyOnWriteArrayList<ProcessOutputRecord>()
        val subscriber = ProcessOutputSubscriber { record ->
            records.add(record)
        }
        ProcessOutputBus.subscribe(subscriber)

        try {
            val linesToTest = listOf(
                "Authorization: Bearer secret_token_123",
                "export MY_SECRET=super_secret_value",
                "http://example.com/api?token=abc123xyz",
                "Just a normal non-secret line"
            )

            linesToTest.forEach { line ->
                ProcessOutputBus.publish(1234, "test", "stdout", line)
            }

            assertEquals(4, records.size)

            val bearerRecord = records[0]
            assertTrue(bearerRecord.line.contains(SecretRedactor.REDACTED))
            assertEquals(-1, bearerRecord.line.indexOf("secret_token_123"))

            val assignRecord = records[1]
            assertTrue(assignRecord.line.contains(SecretRedactor.REDACTED))
            assertEquals(-1, assignRecord.line.indexOf("super_secret_value"))

            val queryRecord = records[2]
            assertTrue(queryRecord.line.contains(SecretRedactor.REDACTED))
            assertEquals(-1, queryRecord.line.indexOf("abc123xyz"))

            val normalRecord = records[3]
            assertEquals("Just a normal non-secret line", normalRecord.line)

        } finally {
            ProcessOutputBus.unsubscribe(subscriber)
        }
    }

    @Test
    fun testCaptureLimitExceeded() {
        // Reset state, set limit to 256
        ProcessOutputBus.setVerboseCaptureEnabled(false)

        val records = CopyOnWriteArrayList<ProcessOutputRecord>()
        val subscriber = ProcessOutputSubscriber { record ->
            records.add(record)
        }
        ProcessOutputBus.subscribe(subscriber)

        try {
            for (i in 1..260) {
                ProcessOutputBus.publish(1, "test", "stdout", "line $i")
            }

            // We expect exactly 257 records: 256 normal lines, and 1 'capped' warning line
            assertEquals(257, records.size)
            assertTrue(records.last().line.contains("Process output capture capped"))
        } finally {
            ProcessOutputBus.unsubscribe(subscriber)
        }
    }
}
