package io.github.vikulin.opengammakit.hardware.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import io.github.vikulin.opengammakit.BuildConfig
import io.github.vikulin.opengammakit.CustomProber
import io.github.vikulin.opengammakit.hardware.DeviceTransport
import java.io.IOException
import java.util.EnumSet

/**
 * USB CDC serial transport - extracted from the original SerialConnectionFragment
 * implementation with the same connect/permission-request flow, now behind the
 * transport-agnostic DeviceTransport interface.
 */
class UsbSerialTransport(
    private val deviceId: Int,
    private val portNum: Int,
    private val baudRate: Int,
    private val withIoManager: Boolean
) : DeviceTransport {

    private enum class UsbPermission { Unknown, Requested, Granted, Denied }

    private val intentActionGrantUsb = BuildConfig.APPLICATION_ID + ".GRANT_USB"
    private val readWaitMillis = 2000
    private val writeWaitMillis = 2000

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    private var transportListener: DeviceTransport.Listener? = null
    private var pendingFragment: Fragment? = null

    override var deviceIdentifier: String? = null
        private set

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intentActionGrantUsb == intent.action) {
                usbPermission = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    UsbPermission.Granted else UsbPermission.Denied
                val fragment = pendingFragment
                val listener = transportListener
                if (fragment != null && listener != null) {
                    connect(fragment, listener)
                }
            }
        }
    }

    override fun onStart(fragment: Fragment) {
        ContextCompat.registerReceiver(
            fragment.requireActivity(), broadcastReceiver,
            IntentFilter(intentActionGrantUsb),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop(fragment: Fragment) {
        fragment.requireActivity().unregisterReceiver(broadcastReceiver)
    }

    override fun shouldAttemptConnect(): Boolean {
        return usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted
    }

    override fun connect(fragment: Fragment, listener: DeviceTransport.Listener) {
        pendingFragment = fragment
        transportListener = listener

        val activity = fragment.requireActivity()
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }

        if (device == null) {
            listener.onStatus("connection failed: device not found")
            return
        }

        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device)
        }

        if (driver == null) {
            listener.onStatus("connection failed: no driver for device")
            return
        }

        if (driver.ports.size < portNum) {
            listener.onStatus("connection failed: not enough ports at device")
            return
        }

        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)

        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.device)) {
            usbPermission = UsbPermission.Requested
            val flags = PendingIntent.FLAG_MUTABLE
            val intent = Intent(intentActionGrantUsb).apply {
                setPackage(activity.packageName)
            }
            val usbPermissionIntent = PendingIntent.getBroadcast(activity, 0, intent, flags)
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }

        if (usbConnection == null) {
            listener.onStatus(if (!usbManager.hasPermission(driver.device)) "connection failed: permission denied" else "connection failed: open failed")
            return
        }

        try {
            usbSerialPort?.apply {
                open(usbConnection)
                try {
                    setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE)
                } catch (e: UnsupportedOperationException) {
                    listener.onStatus("unsupport setparameters")
                }
            }

            if (withIoManager) {
                usbIoManager = SerialInputOutputManager(usbSerialPort, object : SerialInputOutputManager.Listener {
                    override fun onNewData(data: ByteArray?) {
                        data?.let { listener.onDataReceived(it) }
                    }

                    override fun onRunError(e: Exception?) {
                        listener.onRunError(e)
                    }
                }).also { it.start() }
            }

            deviceIdentifier = device.serialNumber
            listener.onConnected()
        } catch (e: Exception) {
            listener.onStatus("connection failed: ${e.message}")
            disconnect()
            listener.onConnectFailed()
        }
    }

    override fun disconnect() {
        usbIoManager?.listener = null
        usbIoManager?.stop()
        usbIoManager = null
        try {
            usbSerialPort?.close()
        } catch (_: IOException) {
        }
        usbSerialPort = null
    }

    override fun read(): ByteArray {
        val buffer = ByteArray(8192)
        val len = usbSerialPort?.read(buffer, readWaitMillis) ?: 0
        return buffer.copyOf(len)
    }

    override fun send(data: ByteArray) {
        usbSerialPort?.write(data, writeWaitMillis)
    }

    override fun sendBreak() {
        usbSerialPort?.apply {
            setBreak(true)
            Thread.sleep(100)
            setBreak(false)
        }
    }

    override fun setRts(rts: Boolean) {
        usbSerialPort?.rts = rts
    }

    override fun setDtr(dtr: Boolean) {
        usbSerialPort?.dtr = dtr
    }

    override fun getControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return usbSerialPort?.controlLines
    }

    override fun getSupportedControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return usbSerialPort?.supportedControlLines
    }
}
