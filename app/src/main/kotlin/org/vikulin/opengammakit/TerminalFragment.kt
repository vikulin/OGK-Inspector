package org.vikulin.opengammakit

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.HexDump
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.*

class TerminalFragment : Fragment(), SerialInputOutputManager.Listener {

    private enum class UsbPermission { Unknown, Requested, Granted, Denied }

    private val INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB"
    private val WRITE_WAIT_MILLIS = 2000
    private val READ_WAIT_MILLIS = 2000

    private var deviceId = 0
    private var portNum = 0
    private var baudRate = 0
    private var withIoManager = false

    private val mainLooper = Handler(Looper.getMainLooper())
    private lateinit var receiveText: TextView
    private lateinit var controlLines: ControlLines

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    private var connected = false

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText = view.findViewById(R.id.receive_text)
        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText))
        receiveText.movementMethod = ScrollingMovementMethod.getInstance()

        val sendText: TextView = view.findViewById(R.id.send_text)
        val sendBtn: View = view.findViewById(R.id.send_btn)
        sendBtn.setOnClickListener { send(sendText.text.toString()) }

        val receiveBtn: View = view.findViewById(R.id.receive_btn)
        controlLines = ControlLines(view)
        if (withIoManager) {
            receiveBtn.visibility = View.GONE
        } else {
            receiveBtn.setOnClickListener { read() }
        }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear -> {
                receiveText.text = ""
                true
            }
            R.id.send_break -> {
                if (!connected) {
                    Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        usbSerialPort?.apply {
                            setBreak(true)
                            Thread.sleep(100)
                            setBreak(false)
                        }
                        val spn = SpannableStringBuilder().apply {
                            append("send <break>\n")
                            setSpan(ForegroundColorSpan(resources.getColor(R.color.colorSendText)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        receiveText.append(spn)
                    } catch (e: UnsupportedOperationException) {
                        Toast.makeText(activity, "BREAK not supported", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(activity, "BREAK failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewData(data: ByteArray?) {
        mainLooper.post {
            data?.let { receive(it) }
        }
    }

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

    private fun disconnect() {
        connected = false
        controlLines.stop()
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

    private fun send(str: String) {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val data = (str + '\n').toByteArray()
            val spn = SpannableStringBuilder().apply {
                append("send ${data.size} bytes\n")
                append(HexDump.dumpHexString(data)).append("\n")
                setSpan(ForegroundColorSpan(resources.getColor(R.color.colorSendText)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            receiveText.append(spn)
            usbSerialPort?.write(data, WRITE_WAIT_MILLIS)
        } catch (e: Exception) {
            onRunError(e)
        }
    }

    private fun read() {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val buffer = ByteArray(8192)
            val len = usbSerialPort?.read(buffer, READ_WAIT_MILLIS) ?: 0
            receive(buffer.copyOf(len))
        } catch (e: IOException) {
            status("connection lost: ${e.message}")
            disconnect()
        }
    }

    private fun receive(data: ByteArray) {
        val spn = SpannableStringBuilder().apply {
            append("receive ${data.size} bytes\n")
            if (data.isNotEmpty()) append(HexDump.dumpHexString(data)).append("\n")
        }
        receiveText.append(spn)
    }

    fun status(str: String) {
        val spn = SpannableStringBuilder("$str\n").apply {
            setSpan(ForegroundColorSpan(resources.getColor(R.color.colorStatusText)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        receiveText.append(spn)
    }

    inner class ControlLines(view: View) {
        private val refreshInterval = 200L

        private val rtsBtn: ToggleButton = view.findViewById(R.id.controlLineRts)
        private val ctsBtn: ToggleButton = view.findViewById(R.id.controlLineCts)
        private val dtrBtn: ToggleButton = view.findViewById(R.id.controlLineDtr)
        private val dsrBtn: ToggleButton = view.findViewById(R.id.controlLineDsr)
        private val cdBtn: ToggleButton = view.findViewById(R.id.controlLineCd)
        private val riBtn: ToggleButton = view.findViewById(R.id.controlLineRi)

        private val runnable = Runnable { run() }

        init {
            rtsBtn.setOnClickListener(::toggle)
            dtrBtn.setOnClickListener(::toggle)
        }

        private fun toggle(v: View) {
            val btn = v as ToggleButton
            if (!connected) {
                btn.isChecked = !btn.isChecked
                Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                when (btn) {
                    rtsBtn -> usbSerialPort?.rts = btn.isChecked
                    dtrBtn -> usbSerialPort?.dtr = btn.isChecked
                }
            } catch (e: IOException) {
                status("set ${btn.text}() failed: ${e.message}")
            }
        }

        private fun run() {
            if (!connected) return
            try {
                val controlLines = usbSerialPort?.controlLines ?: return
                rtsBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.RTS)
                ctsBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.CTS)
                dtrBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.DTR)
                dsrBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.DSR)
                cdBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.CD)
                riBtn.isChecked = controlLines.contains(UsbSerialPort.ControlLine.RI)
                mainLooper.postDelayed(runnable, refreshInterval)
            } catch (e: Exception) {
                status("getControlLines() failed: ${e.message} -> stopped control line refresh")
            }
        }

        fun start() {
            if (!connected) return
            try {
                val supported = usbSerialPort?.supportedControlLines ?: return
                if (UsbSerialPort.ControlLine.RTS !in supported) rtsBtn.visibility = View.INVISIBLE
                if (UsbSerialPort.ControlLine.CTS !in supported) ctsBtn.visibility = View.INVISIBLE
                if (UsbSerialPort.ControlLine.DTR !in supported) dtrBtn.visibility = View.INVISIBLE
                if (UsbSerialPort.ControlLine.DSR !in supported) dsrBtn.visibility = View.INVISIBLE
                if (UsbSerialPort.ControlLine.CD !in supported) cdBtn.visibility = View.INVISIBLE
                if (UsbSerialPort.ControlLine.RI !in supported) riBtn.visibility = View.INVISIBLE
                run()
            } catch (e: Exception) {
                Toast.makeText(activity, "getSupportedControlLines() failed: ${e.message}", Toast.LENGTH_SHORT).show()
                listOf(rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn).forEach { it.visibility = View.INVISIBLE }
            }
        }

        fun stop() {
            mainLooper.removeCallbacks(runnable)
            listOf(rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn).forEach { it.isChecked = false }
        }
    }
}
