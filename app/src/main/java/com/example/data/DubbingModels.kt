package com.example.data

/**
 * Automatic System / Light / Dark Liquid Glass theme preference.
 * Defaults to SYSTEM so the app automatically follows the Android system theme.
 */
enum class AppThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromIdOrDefault(raw: String?): AppThemeMode {
            if (raw.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull {
                it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: SYSTEM
        }
    }
}

/**
 * Floating Liquid Glass OSD typography size presets.
 */
enum class OsdFontSizePreset(val id: String, val outputSp: Float, val inputSp: Float) {
    SMALL("small", 14.5f, 11.5f),
    MEDIUM("medium", 17f, 13f),
    LARGE("large", 20.5f, 15f);

    companion object {
        fun fromIdOrDefault(raw: String?): OsdFontSizePreset {
            if (raw.isNullOrBlank()) return MEDIUM
            return entries.firstOrNull {
                it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: MEDIUM
        }
    }
}

/**
 * Result of a real Gemini Live WebSocket handshake test.
 */
data class LiveConnectionTestResult(
    val modelId: String,
    val latencyMs: Long,
    val setupTierUsed: Int
)

/**
 * Dubbing source / target language catalog (34 world languages + Auto Detect).
 */
data class LanguageItem(
    val code: String,      // ISO-639-1 or "auto"
    val bcp47: String,     // BCP-47 locale tag for Gemini speechConfig
    val nameEn: String,    // English name for reliable model instructions
    val nameTr: String,    // Turkish display name
    val nativeName: String // Native display name
) {
    val isAuto: Boolean get() = code == AUTO_CODE

    companion object {
        const val AUTO_CODE = "auto"

        val AUTO_DETECT = LanguageItem(
            code = AUTO_CODE,
            bcp47 = "auto",
            nameEn = "Auto Detect",
            nameTr = "Otomatik algıla",
            nativeName = "Auto"
        )

        val ALL_LANGUAGES: List<LanguageItem> = listOf(
            LanguageItem("tr", "tr-TR", "Turkish", "Türkçe", "Türkçe"),
            LanguageItem("en", "en-US", "English", "İngilizce", "English"),
            LanguageItem("de", "de-DE", "German", "Almanca", "Deutsch"),
            LanguageItem("fr", "fr-FR", "French", "Fransızca", "Français"),
            LanguageItem("es", "es-ES", "Spanish", "İspanyolca", "Español"),
            LanguageItem("it", "it-IT", "Italian", "İtalyanca", "Italiano"),
            LanguageItem("ru", "ru-RU", "Russian", "Rusça", "Русский"),
            LanguageItem("ar", "ar-SA", "Arabic", "Arapça", "العربية"),
            LanguageItem("ja", "ja-JP", "Japanese", "Japonca", "日本語"),
            LanguageItem("ko", "ko-KR", "Korean", "Korece", "한국어"),
            LanguageItem("zh", "zh-CN", "Chinese", "Çince", "中文"),
            LanguageItem("el", "el-GR", "Greek", "Yunanca", "Ελληνικά"),
            LanguageItem("pt", "pt-PT", "Portuguese", "Portekizce", "Português"),
            LanguageItem("hi", "hi-IN", "Hindi", "Hintçe", "हिन्दी"),
            LanguageItem("az", "az-AZ", "Azerbaijani", "Azerbaycanca", "Azərbaycanca"),
            LanguageItem("nl", "nl-NL", "Dutch", "Felemenkçe", "Nederlands"),
            LanguageItem("pl", "pl-PL", "Polish", "Lehçe", "Polski"),
            LanguageItem("uk", "uk-UA", "Ukrainian", "Ukraynaca", "Українська"),
            LanguageItem("sv", "sv-SE", "Swedish", "İsveççe", "Svenska"),
            LanguageItem("ro", "ro-RO", "Romanian", "Rumence", "Română"),
            LanguageItem("fa", "fa-IR", "Persian", "Farsça", "فارسی"),
            LanguageItem("he", "he-IL", "Hebrew", "İbranice", "עברית"),
            LanguageItem("id", "id-ID", "Indonesian", "Endonezce", "Bahasa Indonesia"),
            LanguageItem("vi", "vi-VN", "Vietnamese", "Vietnamca", "Tiếng Việt"),
            LanguageItem("th", "th-TH", "Thai", "Tayca", "ไทย"),
            LanguageItem("cs", "cs-CZ", "Czech", "Çekçe", "Čeština"),
            LanguageItem("hu", "hu-HU", "Hungarian", "Macarca", "Magyar"),
            LanguageItem("fi", "fi-FI", "Finnish", "Fince", "Suomi"),
            LanguageItem("nb", "nb-NO", "Norwegian", "Norveççe", "Norsk"),
            LanguageItem("da", "da-DK", "Danca", "Danca", "Dansk"),
            LanguageItem("bg", "bg-BG", "Bulgarian", "Bulgarca", "Български"),
            LanguageItem("hr", "hr-HR", "Croatian", "Hırvatça", "Hrvatski"),
            LanguageItem("sr", "sr-RS", "Serbian", "Sırpça", "Српски"),
            LanguageItem("ur", "ur-PK", "Urdu", "Urduca", "اردو")
        )

        fun byCode(code: String, allowAuto: Boolean = true): LanguageItem {
            if (allowAuto && code.equals(AUTO_CODE, ignoreCase = true)) return AUTO_DETECT
            val fallbackCode = if (allowAuto) "en" else "tr"
            return ALL_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: ALL_LANGUAGES.first { it.code == fallbackCode }
        }
    }
}
