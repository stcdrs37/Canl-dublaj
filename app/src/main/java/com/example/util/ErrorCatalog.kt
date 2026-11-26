package com.example.util

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ErrorEntry(
    val code: String,
    val title: String,
    val probableCause: String,
    val solution: String,
    val shouldRetry: Boolean
)

object ErrorCatalog {

    fun fromCode(code: Int, rawDetail: String = ""): ErrorEntry {
        val safeDetail = Redactor.redact(rawDetail)
        return when (code) {
            1008, 401, 403 -> ErrorEntry(
                code = code.toString(),
                title = "API anahtarı geçersiz/yetkisiz",
                probableCause = "Girilen Gemini API anahtarı hatalı, iptal edilmiş veya yetkisiz. $safeDetail".trim(),
                solution = "Ayarlar'daki API Anahtarı bölümüne Google AI Studio'dan aldığınız geçerli bir API anahtarı girin.",
                shouldRetry = false
            )
            429 -> ErrorEntry(
                code = "429",
                title = "Kota/hız sınırı",
                probableCause = "Dakikalık veya günlük Gemini API istek sınırına ulaşıldı. $safeDetail".trim(),
                solution = "5 saniye bekleyip tekrar deneyin veya Google AI Studio üzerinden kota durumunuzu kontrol edin.",
                shouldRetry = true
            )
            1007, 400 -> ErrorEntry(
                code = "1007",
                title = "Sunucu kurulumu reddetti (1007)",
                probableCause = "Model kurulum parametreleri veya hedef model yapılandırması sunucu tarafından kabul edilmedi. $safeDetail".trim(),
                solution = "Modelleri Güncelle butonuyla listeyi yenileyip önerilen çeviri modelini seçin.",
                shouldRetry = false
            )
            1006, 1011, 1012, 1013, 500, 502, 503, 504 -> ErrorEntry(
                code = code.toString(),
                title = "Bağlantı koptu",
                probableCause = "WebSocket oturumu beklenmedik biçimde kapandı veya sunucu geçici olarak yanıt vermedi. $safeDetail".trim(),
                solution = "Otomatik yeniden bağlanma devrede; bağlantı 1, 2, 4, 8, 16, 30 sn aralıklarla yenilenecektir.",
                shouldRetry = true
            )
            else -> ErrorEntry(
                code = code.toString(),
                title = "Bağlantı/Sunucu Hatası ($code)",
                probableCause = if (safeDetail.isNotBlank()) safeDetail else "Bilinmeyen sunucu yanıt kodu alındı.",
                solution = "İnternet bağlantınızı ve API anahtarınızı kontrol edip tekrar deneyin.",
                shouldRetry = code >= 500
            )
        }
    }

    fun fromThrowable(t: Throwable): ErrorEntry {
        val msg = Redactor.redact(t.message ?: t.javaClass.simpleName)
        return when (t) {
            is UnknownHostException -> ErrorEntry(
                code = "DNS_NO_NET",
                title = "İnternet bağlantısı yok",
                probableCause = "generativelanguage.googleapis.com adresine ulaşılamıyor (DNS/ağ hatası: $msg).",
                solution = "Wi-Fi veya mobil verinizi kontrol edin; VPN/DNS engelleyici varsa kapatıp tekrar deneyin.",
                shouldRetry = true
            )
            is SocketTimeoutException -> ErrorEntry(
                code = "TIMEOUT",
                title = "Bağlantı koptu (Zaman aşımı)",
                probableCause = "Sunucu süresi içinde yanıt vermedi ($msg).",
                solution = "Ağ kalitenizi kontrol edin; otomatik yeniden bağlanma devreye girecektir.",
                shouldRetry = true
            )
            is SSLException -> ErrorEntry(
                code = "SSL_ERR",
                title = "Güvenli bağlantı (TLS/SSL) hatası",
                probableCause = "Güvenli soket bağlantısı kesildi veya ağ güvenlik duvarı engelledi ($msg).",
                solution = "Tarih/saat ayarlarınızı ve ağ/VPN kısıtlamalarını kontrol edin.",
                shouldRetry = true
            )
            is SecurityException -> ErrorEntry(
                code = "SECURITY_PERM",
                title = "İzin / Güvenlik Hatası",
                probableCause = "Ses yakalama veya ekran paylaşımı izni reddedildi ya da süresi doldu ($msg).",
                solution = "Dublajı yeniden başlatıp ekran/ses yakalama onayını tekrar verin.",
                shouldRetry = false
            )
            else -> ErrorEntry(
                code = "NETWORK_IO",
                title = "Bağlantı koptu",
                probableCause = "Ağ iletişimi sırasında hata oluştu: $msg",
                solution = "İnternet bağlantınızı kontrol edip tekrar deneyin.",
                shouldRetry = true
            )
        }
    }
}
