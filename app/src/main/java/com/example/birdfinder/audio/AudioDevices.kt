package com.example.birdfinder.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** A selectable microphone input. id 0 is the special "automatic / system default". */
data class InputDevice(
    val id: Int,
    val label: String,
)

/**
 * Enumerates microphone inputs (built-in, wired headset, USB — e.g. a shotgun mic on
 * USB-C, Bluetooth) so the user can route capture through better external hardware.
 */
object AudioDevices {

    val AUTOMATIC = InputDevice(id = 0, label = "Automatic (default mic)")

    fun list(context: Context): List<InputDevice> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource && it.type in INPUT_TYPES }
            .map { InputDevice(it.id, labelFor(it)) }
        return listOf(AUTOMATIC) + devices
    }

    /**
     * Resolve the user's choice to a current [AudioDeviceInfo]. Device ids change across
     * reconnects, so we fall back to matching the saved label. Returns null for the default.
     */
    fun resolve(context: Context, id: Int, name: String): AudioDeviceInfo? {
        if (id == 0 && name.isBlank()) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource }
        return inputs.firstOrNull { it.id == id }
            ?: inputs.firstOrNull { labelFor(it) == name }
    }

    private fun labelFor(d: AudioDeviceInfo): String {
        val product = d.productName?.toString()?.trim().orEmpty()
        val type = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
            else -> "External mic"
        }
        return if (product.isNotEmpty() && !product.equals("android", ignoreCase = true)) {
            "$type · $product"
        } else type
    }

    private val INPUT_TYPES = setOf(
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )
}
