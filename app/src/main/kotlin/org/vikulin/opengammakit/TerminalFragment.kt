package org.vikulin.opengammakit

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
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.HexDump
import java.io.IOException

class TerminalFragment : SerialConnectionFragment() {

    private lateinit var receiveText: TextView
    private lateinit var controlLines: ControlLines

    private val mainLooper = Handler(Looper.getMainLooper())

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

    @Deprecated("Deprecated in Java")
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
                        super.sendBreak()
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

    override fun controlLinesStop() {
        controlLines.stop()
    }

    override fun controlLinesStart() {
        controlLines.start()
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
            super.send(data)
        } catch (e: Exception) {
            onRunError(e)
        }
    }

    override fun read() {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        super.read()
    }

    override fun receive(data: ByteArray) {
        val spn = SpannableStringBuilder().apply {
            append("receive ${data.size} bytes\n")
            if (data.isNotEmpty()) append(HexDump.dumpHexString(data)).append("\n")
        }
        receiveText.append(spn)
    }

    override fun status(str: String) {
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
                    rtsBtn -> setRts(btn.isChecked)
                    dtrBtn -> setDtr(btn.isChecked)
                }
            } catch (e: IOException) {
                status("set ${btn.text}() failed: ${e.message}")
            }
        }

        private fun run() {
            if (!connected) return
            try {
                val controlLines = getControlLines() ?: return
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
                val supported = getSupportedControlLines() ?: return
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
