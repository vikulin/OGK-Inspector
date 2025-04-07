package org.vikulin.opengammakit

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.EnumSet

abstract class SerialConnectionFragment : Fragment(), SerialInputOutputManager.Listener {

    private val WRITE_WAIT_MILLIS = 2000
    private val READ_WAIT_MILLIS = 2000

    private enum class UsbPermission { Unknown, Requested, Granted, Denied }

    private val INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB"

    private var deviceId = 0
    private var portNum = 0
    private var baudRate = 0
    internal var withIoManager = false

    private val mainLooper = Handler(Looper.getMainLooper())
    private lateinit var controlLines: ControlLines

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    internal var connected = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {
                usbPermission = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    UsbPermission.Granted else UsbPermission.Denied
                connect()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        arguments?.let {
            deviceId = it.getInt("device")
            portNum = it.getInt("port")
            baudRate = it.getInt("baud")
            withIoManager = it.getBoolean("withIoManager")
        }
        controlLines = ControlLines()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireActivity(), broadcastReceiver,
            IntentFilter(INTENT_ACTION_GRANT_USB),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!connected && (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)) {
            mainLooper.post { connect() }
        }
    }

    override fun onPause() {
        if (connected) {
            status("disconnected")
            disconnect()
        }
        super.onPause()
    }

    override fun onNewData(data: ByteArray?) {
        mainLooper.post {
            data?.let { receive(it) }
        }
    }

    abstract fun receive(bytes: ByteArray)

    override fun onRunError(e: Exception?) {
        mainLooper.post {
            status("connection lost: ${e?.message}")
            disconnect()
        }
    }

    private fun connect() {
        val usbManager = requireActivity().getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }

        if (device == null) {
            status("connection failed: device not found")
            return
        }

        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device)
        }

        if (driver == null) {
            status("connection failed: no driver for device")
            return
        }

        if (driver.ports.size < portNum) {
            status("connection failed: not enough ports at device")
            return
        }

        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)

        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.device)) {
            usbPermission = UsbPermission.Requested
            val flags = PendingIntent.FLAG_MUTABLE
            val intent = Intent(INTENT_ACTION_GRANT_USB).apply {
                setPackage(requireActivity().packageName)
            }
            val usbPermissionIntent = PendingIntent.getBroadcast(requireActivity(), 0, intent, flags)
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }

        if (usbConnection == null) {
            status(if (!usbManager.hasPermission(driver.device)) "connection failed: permission denied" else "connection failed: open failed")
            return
        }

        try {
            usbSerialPort?.apply {
                open(usbConnection)
                try {
                    setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE)
                } catch (e: UnsupportedOperationException) {
                    status("unsupport setparameters")
                }
            }

            if (withIoManager) {
                usbIoManager = SerialInputOutputManager(usbSerialPort, this).also { it.start() }
            }

            status("connected")
            connected = true
            controlLines.start()
        } catch (e: Exception) {
            status("connection failed: ${e.message}")
            disconnect()
        }
    }

    internal fun disconnect() {
        controlLines.stop()
        connected = false
        usbIoManager?.apply {
            listener = null
            stop()
        }
        usbIoManager = null
        try {
            usbSerialPort?.close()
        } catch (_: IOException) {}
        usbSerialPort = null
    }

    internal open fun read() {
        try {
            val buffer = ByteArray(8192)
            val len = usbSerialPort?.read(buffer, READ_WAIT_MILLIS) ?: 0
            receive(buffer.copyOf(len))
        } catch (e: IOException) {
            status("connection lost: ${e.message}")
            disconnect()
        }
    }

    internal open fun send(data: ByteArray) {
        usbSerialPort?.write(data, WRITE_WAIT_MILLIS)
    }

    internal open fun sendBreak(){
        usbSerialPort?.apply {
            setBreak(true)
            Thread.sleep(100)
            setBreak(false)
        }
    }

    internal open fun setRts(rts: Boolean){
        usbSerialPort?.rts = rts
    }

    internal open fun setDtr(dtr: Boolean){
        usbSerialPort?.dtr = dtr
    }

    private fun getControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return usbSerialPort?.controlLines
    }

    private fun getSupportedControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return usbSerialPort?.supportedControlLines
    }

    abstract fun status(str: String)

    open fun controlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {

    }

    open fun supportedControlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {

    }

    open fun stopCommunication(){

    }

    inner class ControlLines() {
        private val refreshInterval = 200L

        private val runnable = Runnable { run() }

        private fun run() {
            if (!connected) return
            try {
                val values = getControlLines()
                if(values!=null){
                    controlLines(values)
                }
                mainLooper.postDelayed(runnable, refreshInterval)
            } catch (e: Exception) {
                status("getControlLines() failed: ${e.message} -> stopped control line refresh")
            }
        }

        fun start() {
            if (!connected) return
            try {

                val values = getSupportedControlLines()
                if(values!=null){
                    supportedControlLines(values)
                }
                run()
            } catch (e: Exception) {
                Toast.makeText(activity, "getSupportedControlLines() failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun stop() {
            mainLooper.removeCallbacks(runnable)
            SerialConnectionFragment@stopCommunication()
        }
    }
}
