package com.example.util

import java.util.Locale

/**
 * Pure-Kotlin automatic source language normalizer and text-based fallback detector.
 * Ensures that LiveDub always identifies and displays the source language (e.g. EN -> TR)
 * both when Gemini sends `inputTranscription.languageCode` (BCP-47) and when the server
 * sends incremental `inputTranscription.text` frames without a `languageCode` field.
 */
object LanguageDetector {

    private val LANGUAGE_NAMES_TR = mapOf(
        "EN" to "EN (İngilizce)",
        "TR" to "TR (Türkçe)",
        "DE" to "DE (Almanca)",
        "FR" to "FR (Fransızca)",
        "ES" to "ES (İspanyolca)",
        "IT" to "IT (İtalyanca)",
        "PT" to "PT (Portekizce)",
        "RU" to "RU (Rusça)",
        "AR" to "AR (Arapça)",
        "JA" to "JA (Japonca)",
        "KO" to "KO (Korece)",
        "ZH" to "ZH (Çince)",
        "NL" to "NL (Felemenkçe)",
        "PL" to "PL (Lehçe)",
        "UK" to "UK (Ukraynaca)",
        "EL" to "EL (Yunanca)",
        "HE" to "HE (İbranice)",
        "HI" to "HI (Hintçe)",
        "FA" to "FA (Farsça)",
        "AZ" to "AZ (Azerbaycan Türkçesi)",
        "SV" to "SV (İsveççe)",
        "RO" to "RO (Rumence)"
    )

    private val EN_STOPWORDS = setOf(
        "the", "and", "you", "that", "this", "with", "for", "are", "have", "was",
        "what", "from", "they", "will", "would", "about", "there", "their", "can",
        "all", "your", "when", "going", "know", "like", "just", "time", "people",
        "into", "hello", "welcome", "today", "here", "how", "why", "where", "who",
        "which", "because", "really", "think", "make", "good", "well", "out", "now",
        "see", "look", "come", "back", "want", "need", "something", "everything",
        "nothing", "right", "actually", "very", "much", "more", "some", "been", "were"
    )

    private val TR_STOPWORDS = setOf(
        "bir", "ve", "bu", "için", "ile", "ama", "çok", "daha", "gibi", "olan",
        "olarak", "var", "yok", "ben", "sen", "biz", "evet", "hayır", "nasıl",
        "neden", "şimdi", "sonra", "kadar", "değil", "merhaba", "arkadaşlar",
        "bugün", "burada", "böyle", "ancak", "çünkü", "zaman", "her", "şey"
    )

    private val DE_STOPWORDS = setOf(
        "und", "der", "die", "das", "ist", "nicht", "ich", "sie", "wir", "mit",
        "auf", "für", "eine", "einem", "auch", "sich", "aber", "oder", "wenn",
        "wie", "noch", "nur", "haben", "wird", "sind", "von", "dem", "den"
    )

    private val FR_STOPWORDS = setOf(
        "les", "des", "est", "une", "dans", "pour", "pas", "que", "qui", "sur",
        "plus", "avec", "sont", "mais", "nous", "vous", "comme", "tout", "cette",
        "être", "fait", "bien", "deux", "même", "encore", "aussi", "leur"
    )

    private val ES_STOPWORDS = setOf(
        "los", "las", "una", "para", "con", "por", "como", "más", "pero", "sus",
        "este", "esta", "entre", "cuando", "todo", "también", "fue", "había",
        "muy", "sin", "sobre", "hola", "todos", "porque", "están", "tiene"
    )

    private val IT_STOPWORDS = setOf(
        "che", "non", "una", "per", "sono", "della", "questo", "questa", "anche",
        "come", "più", "suo", "anni", "stato", "hanno", "molto", "tutti", "dalla"
    )

    private val PT_STOPWORDS = setOf(
        "não", "uma", "com", "para", "por", "mais", "como", "mas", "foi", "sua",
        "seus", "pelo", "pela", "está", "isso", "muito", "também", "você", "são"
    )

    /**
     * Normalizes a BCP-47 tag (e.g. "en-US", "en_GB", "tr-TR", "ja") to a 2-letter uppercase ISO code.
     */
    fun normalizeIsoCode(rawCode: String?): String? {
        if (rawCode.isNullOrBlank()) return null
        val cleaned = rawCode.trim()
            .replace('_', '-')
            .split('-')
            .firstOrNull()
            ?.uppercase(Locale.ROOT)
            ?: return null
        if (cleaned.length !in 2..3 || cleaned == "AUTO" || cleaned == "UND") return null
        return cleaned
    }

    /**
     * Formats an ISO code such as "EN" into "EN (İngilizce)".
     */
    fun formatDisplayLabel(isoCode: String): String {
        val upper = isoCode.uppercase(Locale.ROOT)
        return LANGUAGE_NAMES_TR[upper] ?: upper
    }

    /**
     * Detects the 2-letter ISO language code from transcript text when the server omits `languageCode`.
     */
    fun detectIsoFromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length < 2) return null

        // 1. Check non-Latin Unicode scripts first
        var cyrillicCount = 0
        var arabicCount = 0
        var kanaCount = 0
        var hangulCount = 0
        var cjkCount = 0
        var greekCount = 0
        var hebrewCount = 0
        var devanagariCount = 0

        for (ch in trimmed) {
            when (ch.code) {
                in 0x0400..0x04FF -> cyrillicCount++
                in 0x0600..0x06FF, in 0x0750..0x077F -> arabicCount++
                in 0x3040..0x30FF -> kanaCount++
                in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> hangulCount++
                in 0x4E00..0x9FFF -> cjkCount++
                in 0x0370..0x03FF -> greekCount++
                in 0x0590..0x05FF -> hebrewCount++
                in 0x0900..0x097F -> devanagariCount++
            }
        }

        if (kanaCount >= 1) return "JA"
        if (hangulCount >= 2) return "KO"
        if (cjkCount >= 2) return "ZH"
        if (cyrillicCount >= 3) return "RU"
        if (arabicCount >= 3) return "AR"
        if (greekCount >= 3) return "EL"
        if (hebrewCount >= 3) return "HE"
        if (devanagariCount >= 3) return "HI"

        // 2. Check distinctive Turkish characters
        val hasTurkishSpecificChars = trimmed.any {
            it == 'ğ' || it == 'Ğ' || it == 'ş' || it == 'Ş' || it == 'ı' || it == 'İ'
        }
        if (hasTurkishSpecificChars) return "TR"

        // 3. Tokenize Latin words and score stopwords
        val words = trimmed.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-ZçğıöşüÇĞİÖŞÜäöüßéèêàùâîôûñãõ]+"))
            .filter { it.length >= 2 }

        if (words.isEmpty()) return null

        var enScore = 0
        var trScore = 0
        var deScore = 0
        var frScore = 0
        var esScore = 0
        var itScore = 0
        var ptScore = 0

        for (w in words) {
            if (w in EN_STOPWORDS) enScore += 2
            if (w in TR_STOPWORDS) trScore += 2
            if (w in DE_STOPWORDS) deScore += 2
            if (w in FR_STOPWORDS) frScore += 2
            if (w in ES_STOPWORDS) esScore += 2
            if (w in IT_STOPWORDS) itScore += 2
            if (w in PT_STOPWORDS) ptScore += 2
        }

        if (trimmed.any { it == 'ß' }) deScore += 3
        if (trimmed.any { it == 'ñ' || it == '¿' || it == '¡' }) esScore += 3
        if (trimmed.any { it == 'ã' || it == 'õ' }) ptScore += 3

        val best = listOf(
            "EN" to enScore,
            "TR" to trScore,
            "DE" to deScore,
            "FR" to frScore,
            "ES" to esScore,
            "IT" to itScore,
            "PT" to ptScore
        ).maxByOrNull { it.second }

        if (best != null && best.second >= 2) {
            return best.first
        }

        return null
    }

    /**
     * Resolves both the 2-letter ISO code (for stats) and the Turkish display label (for UI).
     */
    fun resolveSourceLanguage(
        serverLanguageCode: String?,
        fullTurnText: String,
        currentDisplayLabel: String
    ): Pair<String?, String> {
        val isoFromServer = normalizeIsoCode(serverLanguageCode)
        if (isoFromServer != null) {
            return isoFromServer to formatDisplayLabel(isoFromServer)
        }
        val isoFromText = detectIsoFromText(fullTurnText)
        if (isoFromText != null) {
            return isoFromText to formatDisplayLabel(isoFromText)
        }
        return null to currentDisplayLabel
    }

    fun resolveSourceLanguageLabel(
        explicitLanguageCode: String?,
        transcriptSample: String,
        previousLabel: String
    ): String {
        return resolveSourceLanguage(
            serverLanguageCode = explicitLanguageCode,
            fullTurnText = transcriptSample,
            currentDisplayLabel = previousLabel
        ).second
    }
}
