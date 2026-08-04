package app.gamenative.diagnostics

/**
 * Applies the same conservative secret filtering before a diagnostic record reaches any sink.
 * It intentionally preserves non-sensitive launch metadata such as app ID, component versions,
 * and the generated diagnostic session/launch IDs.
 */
object SecretRedactor {
    const val REDACTED = "[REDACTED]"

    private val assignmentPattern = Regex(
        "(?i)(\\b(?:password|passwd|pwd|access_token|refresh_token|token|authorization|cookie|totp|otp|" +
            "mfa[_-]?code|auth[_-]?code|secret|credential)\\b\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)"
    )
    private val bearerPattern = Regex("(?i)(\\b(?:bearer|basic)\\s+)([^\\s,;&]+)")
    private val queryPattern = Regex(
        "(?i)([?&](?:access_token|refresh_token|token|code|auth_code|totp|otp)=)([^&#\\s]+)"
    )
    private val androidHomePathPattern = Regex("(?i)(/data/(?:user|user_de)/\\d+/)[^\\s\"']+")
    private val windowsHomePathPattern = Regex("(?i)([a-z]:\\\\users\\\\)[^\\\\/\\s\"']+")

    private val sensitiveFieldFragments = listOf(
        "password",
        "passwd",
        "token",
        "authorization",
        "cookie",
        "totp",
        "otp",
        "secret",
        "credential",
    )

    fun redact(value: String?): String {
        if (value.isNullOrEmpty()) return value.orEmpty()

        return value
            .let { bearerPattern.replace(it) { match -> "${match.groupValues[1]}$REDACTED" } }
            .let { assignmentPattern.replace(it) { match -> "${match.groupValues[1]}$REDACTED" } }
            .let { queryPattern.replace(it) { match -> "${match.groupValues[1]}$REDACTED" } }
            .let { androidHomePathPattern.replace(it) { match -> "${match.groupValues[1]}$REDACTED" } }
            .let { windowsHomePathPattern.replace(it) { match -> "${match.groupValues[1]}$REDACTED" } }
    }

    fun redactField(fieldName: String, value: Any?): String {
        val normalizedName = fieldName.lowercase()
        if (sensitiveFieldFragments.any(normalizedName::contains)) return REDACTED
        return redact(value?.toString()).take(MAX_FIELD_VALUE_LENGTH)
    }

    private const val MAX_FIELD_VALUE_LENGTH = 2_048
}
