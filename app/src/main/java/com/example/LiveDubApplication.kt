package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.example.data.SettingsRepository
import com.example.live.LiveSessionClient
import com.example.security.SecurityShield
import com.example.service.DubbingController
import com.example.service.DubbingNotificationHelper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(SecurityShield.buildStrictTlsSpecs())
        .followRedirects(false)
        .followSslRedirects(false)
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val liveSessionClient = LiveSessionClient(okHttpClient)
    val dubbingController = DubbingController()
}

class LiveDubApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DubbingNotificationHelper.ensureChannel(this)

        // Pre-warm DNS + TLS 1.3 connection to Google AI servers during splash so live dubbing starts fast!
        container.liveSessionClient.prewarmTlsConnection()

        // Restore any pending volume changes if a previous session was killed unexpectedly
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            container.settingsRepository.restoreAllPendingVolumes(am)
        } catch (_: Throwable) {
        }
    }
}
