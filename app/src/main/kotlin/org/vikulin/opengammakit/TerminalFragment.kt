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
import java.io.IOException
import java.util.EnumSet

class TerminalFragment : SerialConnectionFragment() {

    private lateinit var rtsBtn: ToggleButton
    private lateinit var ctsBtn: ToggleButton
    private lateinit var dtrBtn: ToggleButton
    private lateinit var dsrBtn: ToggleButton
    private lateinit var cdBtn: ToggleButton
    private lateinit var riBtn: ToggleButton

    private lateinit var receiveText: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        rtsBtn = view.findViewById(R.id.controlLineRts)
        ctsBtn = view.findViewById(R.id.controlLineCts)
        dtrBtn = view.findViewById(R.id.controlLineDtr)
        dsrBtn = view.findViewById(R.id.controlLineDsr)
        cdBtn = view.findViewById(R.id.controlLineCd)
        riBtn = view.findViewById(R.id.controlLineRi)
        receiveText = view.findViewById(R.id.receive_text)
        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText))
        receiveText.movementMethod = ScrollingMovementMethod.getInstance()

        val sendText: TextView = view.findViewById(R.id.send_text)
        val sendBtn: View = view.findViewById(R.id.send_btn)
        sendBtn.setOnClickListener { send(sendText.text.toString()) }

        val receiveBtn: View = view.findViewById(R.id.receive_btn)

        if (withIoManager) {
            receiveBtn.visibility = View.GONE
        } else {
            receiveBtn.setOnClickListener { read() }
        }

        rtsBtn.setOnClickListener(::toggle)
        dtrBtn.setOnClickListener(::toggle)

        return view
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

    @Deprecated("Deprecated in Java")
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

    private fun send(str: String) {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val data = (str + '\n').toByteArray()
            val spn = SpannableStringBuilder().apply {
                append(">").append(str).append("\n")
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
        //Log.d("TerminalFragment","received ${data.size} bytes")
        val spn = SpannableStringBuilder().apply {
            if (data.isNotEmpty()) append(String(data)).append("\n")
        }
        receiveText.append(spn)
    }

    override fun status(str: String) {
        val spn = SpannableStringBuilder("$str\n").apply {
            setSpan(ForegroundColorSpan(resources.getColor(R.color.colorStatusText)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        receiveText.append(spn)
    }

    override fun controlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {
        super.controlLines(values)
        rtsBtn.isChecked = values.contains(UsbSerialPort.ControlLine.RTS)
        ctsBtn.isChecked = values.contains(UsbSerialPort.ControlLine.CTS)
        dtrBtn.isChecked = values.contains(UsbSerialPort.ControlLine.DTR)
        dsrBtn.isChecked = values.contains(UsbSerialPort.ControlLine.DSR)
        cdBtn.isChecked = values.contains(UsbSerialPort.ControlLine.CD)
        riBtn.isChecked = values.contains(UsbSerialPort.ControlLine.RI)
    }

    override fun supportedControlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {
        super.supportedControlLines(values)
        try {
            if (UsbSerialPort.ControlLine.RTS !in values) rtsBtn.visibility = View.INVISIBLE
            if (UsbSerialPort.ControlLine.CTS !in values) ctsBtn.visibility = View.INVISIBLE
            if (UsbSerialPort.ControlLine.DTR !in values) dtrBtn.visibility = View.INVISIBLE
            if (UsbSerialPort.ControlLine.DSR !in values) dsrBtn.visibility = View.INVISIBLE
            if (UsbSerialPort.ControlLine.CD !in values) cdBtn.visibility = View.INVISIBLE
            if (UsbSerialPort.ControlLine.RI !in values) riBtn.visibility = View.INVISIBLE
        } catch (e: Exception) {
            Toast.makeText(activity, "getSupportedControlLines() failed: ${e.message}", Toast.LENGTH_SHORT).show()
            listOf(rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn).forEach { it.visibility = View.INVISIBLE }
        }
    }

    override fun stopCommunication() {
        super.stopCommunication()
        listOf(rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn).forEach { it.isChecked = false }
    }
}
