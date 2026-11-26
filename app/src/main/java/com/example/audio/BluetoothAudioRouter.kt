package com.example.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Pure descriptor for deterministic unit testing and UI telemetry of Bluetooth audio devices.
 */
data class BluetoothAudioRouteInfo(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val deviceTypeLabel: String = "",
    val deviceInfo: AudioDeviceInfo? = null
)

/**
 * Dynamic Bluetooth Audio Output Router (Android 6.0 / API 23 -> Android 16 / API 36).
 *
 * Detects connected Bluetooth headphones, earbuds, and speakers (`A2DP`, `BLE_HEADSET`,
 * `BLE_SPEAKER`, `SCO`) using zero-permission `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`
 * and live `AudioDeviceCallback` hot-plug monitoring.
 *
 * When a Bluetooth audio device is connected and Bluetooth Dub-Only routing is enabled,
 * pins the AI dubbing `AudioTrack` directly to the Bluetooth device via `setPreferredDevice(...)`
 * while the engine suppresses original media so ONLY the dubbing voice flows clearly to the
 * Bluetooth headset/speaker.
 */
class BluetoothAudioRouter(
    private val audioManager: AudioManager
) {
    companion object {
        // Explicit constants for API 31+ LE Audio device types so code compiles & runs cleanly from API 23+
        internal const val TYPE_BLE_HEADSET_SAFE = 26
        internal const val TYPE_BLE_SPEAKER_SAFE = 27

        /**
         * Returns priority rank for Bluetooth output types (higher = preferred),
         * or 0 if the device type is NOT a Bluetooth audio output.
         */
        fun bluetoothOutputPriority(deviceType: Int): Int = when (deviceType) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 300
            TYPE_BLE_HEADSET_SAFE -> 280
            TYPE_BLE_SPEAKER_SAFE -> 260
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 150
            else -> 0
        }

        fun isBluetoothOutputType(deviceType: Int): Boolean = bluetoothOutputPriority(deviceType) > 0

        fun labelForDeviceType(deviceType: Int): String = when (deviceType) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            TYPE_BLE_HEADSET_SAFE -> "Bluetooth LE Audio"
            TYPE_BLE_SPEAKER_SAFE -> "Bluetooth LE Speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
            else -> ""
        }

        /**
         * Pure selection helper for deterministic unit testing.
         * Pairs of (deviceType, displayName) -> returns best Bluetooth candidate or null.
         */
        fun pickBestCandidate(candidates: List<Pair<Int, String>>): Pair<Int, String>? {
            return candidates
                .filter { isBluetoothOutputType(it.first) }
                .maxByOrNull { bluetoothOutputPriority(it.first) }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var deviceCallback: AudioDeviceCallback? = null

    @Volatile
    var currentRoute: BluetoothAudioRouteInfo = BluetoothAudioRouteInfo()
        private set

    fun detectConnectedBluetoothOutput(): BluetoothAudioRouteInfo {
        return try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val best = outputs
                .filter { it.isSink && isBluetoothOutputType(it.type) }
                .maxByOrNull { bluetoothOutputPriority(it.type) }
            if (best != null) {
                val rawName = best.productName?.toString()?.trim().orEmpty()
                val label = labelForDeviceType(best.type)
                BluetoothAudioRouteInfo(
                    isConnected = true,
                    deviceName = rawName.ifEmpty { label },
                    deviceTypeLabel = label,
                    deviceInfo = best
                )
            } else {
                BluetoothAudioRouteInfo()
            }
        } catch (_: Throwable) {
            BluetoothAudioRouteInfo()
        }
    }

    /**
     * Applies (or clears) preferred Bluetooth device routing on the live dubbing [AudioTrack].
     * Works from Android 6.0 (API 23) through Android 16 (API 36).
     */
    fun applyRoutingToTrack(track: AudioTrack?, bluetoothDubOnlyEnabled: Boolean): BluetoothAudioRouteInfo {
        val route = detectConnectedBluetoothOutput()
        currentRoute = route
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (bluetoothDubOnlyEnabled && route.isConnected && route.deviceInfo != null) {
                    track.setPreferredDevice(route.deviceInfo)
                } else {
                    track.setPreferredDevice(null)
                }
            } catch (_: Throwable) {
            }
        }
        return route
    }

    /**
     * Registers a live AudioDeviceCallback so connecting or disconnecting Bluetooth headphones
     * or speakers during an active dubbing session immediately updates routing without interruption.
     */
    @Synchronized
    fun startMonitoring(onRouteChanged: (BluetoothAudioRouteInfo) -> Unit) {
        stopMonitoring()
        val initial = detectConnectedBluetoothOutput()
        currentRoute = initial
        onRouteChanged(initial)

        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val updated = detectConnectedBluetoothOutput()
                if (updated.isConnected != currentRoute.isConnected ||
                    updated.deviceName != currentRoute.deviceName ||
                    updated.deviceInfo?.id != currentRoute.deviceInfo?.id
                ) {
                    currentRoute = updated
                    onRouteChanged(updated)
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val updated = detectConnectedBluetoothOutput()
                if (updated.isConnected != currentRoute.isConnected ||
                    updated.deviceName != currentRoute.deviceName ||
                    updated.deviceInfo?.id != currentRoute.deviceInfo?.id
                ) {
                    currentRoute = updated
                    onRouteChanged(updated)
                }
            }
        }
        deviceCallback = cb
        try {
            audioManager.registerAudioDeviceCallback(cb, mainHandler)
        } catch (_: Throwable) {
            deviceCallback = null
        }
    }

    @Synchronized
    fun stopMonitoring() {
        val cb = deviceCallback ?: return
        deviceCallback = null
        try {
            audioManager.unregisterAudioDeviceCallback(cb)
        } catch (_: Throwable) {
        }
    }
}
