package com.example.util

/**
 * Pure Kotlin redaction utility (no Android framework dependencies) so it can be unit-tested on JVM.
 * Masks API keys in URLs ("key=..."), HTTP headers ("x-goog-api-key:..."), Bearer tokens,
 * encrypted vault blobs ("v2:..."), and standalone Google API keys ("AIza...").
 */
object Redactor {
    private val keyParamRegex = Regex("""key=[^&\s"]+""", RegexOption.IGNORE_CASE)
    private val headerKeyRegex = Regex("""x-goog-api-key\s*[:=]\s*[^\s,;"]+""", RegexOption.IGNORE_CASE)
    private val bearerRegex = Regex("""Bearer\s+[A-Za-z0-9._~+/-]+=*""", RegexOption.IGNORE_CASE)
    private val aizaRegex = Regex("""AIza[0-9A-Za-z_-]{20,}""")
    private val vaultBlobRegex = Regex("""v2:(?:ks|fb):[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(keyParamRegex, "***REDACTED***")
            .replace(headerKeyRegex, "x-goog-api-key: ***REDACTED***")
            .replace(bearerRegex, "Bearer ***REDACTED***")
            .replace(aizaRegex, "***REDACTED***")
            .replace(vaultBlobRegex, "***VAULT_REDACTED***")
    }
}
