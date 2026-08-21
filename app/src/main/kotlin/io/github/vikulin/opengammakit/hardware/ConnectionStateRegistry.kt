package io.github.vikulin.opengammakit.hardware

/**
 * Tracks which devices this app currently holds an open transport connection to,
 * keyed by a transport-specific identity (USB device id + port, or BLE address).
 * SerialConnectionFragment updates this on connect/disconnect; DevicesFragment
 * reads it to highlight already-connected rows.
 */
object ConnectionStateRegistry {
    private val connectedKeys = mutableSetOf<String>()

    fun usbKey(deviceId: Int, port: Int): String = "usb:$deviceId:$port"
    fun bleKey(address: String): String = "ble:$address"

    fun markConnected(key: String) {
        connectedKeys.add(key)
    }

    fun markDisconnected(key: String) {
        connectedKeys.remove(key)
    }

    fun isConnected(key: String): Boolean = key in connectedKeys
}
