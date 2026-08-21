package io.github.vikulin.opengammakit.hardware

import androidx.fragment.app.Fragment
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.util.EnumSet

/**
 * Transport-agnostic connection to the device: bytes in, bytes out, regardless
 * of whether they travel over USB serial or a BLE GATT link. SerialConnectionFragment
 * and its subclasses (Counter/Spectrum/Terminal/Info/SerialCommand fragments) only
 * ever talk to this interface, so they don't need to know which transport is active.
 */
interface DeviceTransport {

    interface Listener {
        fun onStatus(message: String)
        fun onConnected()
        fun onConnectFailed()
        fun onDataReceived(data: ByteArray)
        fun onRunError(e: Exception?)
    }

    /** Serial number (USB) or MAC address (BLE) - used as a per-device preferences key. */
    val deviceIdentifier: String?

    fun onStart(fragment: Fragment) {}
    fun onStop(fragment: Fragment) {}

    /** False once a connection attempt is known to be pointless right now (e.g. a USB
     *  permission request is still pending, or was already denied) - avoids retrying
     *  connect() and re-showing failures on every onResume(). */
    fun shouldAttemptConnect(): Boolean = true

    fun connect(fragment: Fragment, listener: Listener)
    fun disconnect()

    fun read(): ByteArray
    fun send(data: ByteArray)
    fun sendBreak()
    fun setRts(rts: Boolean)
    fun setDtr(dtr: Boolean)

    /** Control-line concepts (RTS/CTS/DTR/DSR/CD/RI) are USB-only; BLE transports report none supported. */
    fun getControlLines(): EnumSet<UsbSerialPort.ControlLine?>?
    fun getSupportedControlLines(): EnumSet<UsbSerialPort.ControlLine?>?
}
