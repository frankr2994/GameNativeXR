package app.gamenative.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun redact_removesCredentialsTokensAndUserSpecificPaths() {
        val raw = "password=super-secret Authorization: Bearer header-secret bearer abc.def.ghi " +
            "https://example.invalid/callback?access_token=query-secret " +
            "/data/user/0/app.gamenative/files C:\\Users\\Rob\\Steam"

        val redacted = SecretRedactor.redact(raw)

        assertFalse(redacted.contains("super-secret"))
        assertFalse(redacted.contains("header-secret"))
        assertFalse(redacted.contains("abc.def.ghi"))
        assertFalse(redacted.contains("query-secret"))
        assertFalse(redacted.contains("Rob"))
        assertTrue(redacted.contains(SecretRedactor.REDACTED))
    }

    @Test
    fun redactField_redactsSensitiveNamesAndPreservesSafeLaunchMetadata() {
        assertEquals(SecretRedactor.REDACTED, SecretRedactor.redactField("refresh_token", "value"))
        assertEquals("480", SecretRedactor.redactField("appId", 480))
        assertEquals("quest_2", SecretRedactor.redactField("profileId", "quest_2"))
    }
}
