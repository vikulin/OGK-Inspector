package io.github.vikulin.opengammakit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import io.github.vikulin.opengammakit.hardware.ConnectionStateRegistry
import io.github.vikulin.opengammakit.hardware.DeviceTransport
import io.github.vikulin.opengammakit.hardware.ble.BleSerialTransport
import io.github.vikulin.opengammakit.hardware.usb.UsbSerialTransport
import io.github.vikulin.opengammakit.view.ConnectingDialogFragment
import io.github.vikulin.opengammakit.view.ReconnectDeviceDialogFragment
import java.util.EnumSet

abstract class SerialConnectionFragment : Fragment(),
    DeviceTransport.Listener,
    ReconnectDeviceDialogFragment.ReconnectDeviceDialogListener {

    internal var deviceId = 0
    internal var portNum = 0
    internal var baudRate = 0
    internal var withIoManager = false
    internal var connectionType = CONNECTION_TYPE_USB
    internal var bleAddress: String? = null
    internal var bleName: String? = null

    private val mainLooper = Handler(Looper.getMainLooper())
    private lateinit var controlLines: ControlLines
    private lateinit var transport: DeviceTransport

    internal var connected = false
    internal var serialNumber: String? = null
    private var connectingDialog: ConnectingDialogFragment? = null
    private var lastStatusMessage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        arguments?.let {
            deviceId = it.getInt("device")
            portNum = it.getInt("port")
            baudRate = it.getInt("baud")
            withIoManager = it.getBoolean("withIoManager")
            connectionType = it.getString("connectionType", CONNECTION_TYPE_USB)
            bleAddress = it.getString("bleAddress")
            bleName = it.getString("bleName")
        }
        controlLines = ControlLines()
        transport = when (connectionType) {
            CONNECTION_TYPE_BLE -> BleSerialTransport(bleAddress ?: "")
            else -> UsbSerialTransport(deviceId, portNum, baudRate, withIoManager)
        }
    }

    override fun onStart() {
        super.onStart()
        transport.onStart(this)
    }

    override fun onStop() {
        transport.onStop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!connected && transport.shouldAttemptConnect()) {
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

    abstract fun receive(bytes: ByteArray)

    private fun connect() {
        if (connectionType == CONNECTION_TYPE_BLE) {
            showConnectingDialog()
        }
        transport.connect(this, this)
    }

    private fun showConnectingDialog() {
        if (connectingDialog?.isAdded == true) return
        val label = bleName ?: bleAddress ?: getString(R.string.unknown_bluetooth_device)
        val dialog = ConnectingDialogFragment.newInstance(getString(R.string.connecting_to, label))
        connectingDialog = dialog
        dialog.show(childFragmentManager, "connecting_dialog")
    }

    open fun onConnectionSuccess() {

    }

    open fun onConnectionFailed() {

    }

    internal fun disconnect() {
        controlLines.stop()
        connected = false
        transport.disconnect()
        ConnectionStateRegistry.markDisconnected(connectionKey)
    }

    private val connectionKey: String
        get() = when (connectionType) {
            CONNECTION_TYPE_BLE -> ConnectionStateRegistry.bleKey(bleAddress ?: "")
            else -> ConnectionStateRegistry.usbKey(deviceId, portNum)
        }

    internal open fun read() {
        try {
            val data = transport.read()
            receive(data)
        } catch (e: Exception) {
            status("connection lost: ${e.message}")
            disconnect()
        }
    }

    internal open fun send(data: ByteArray) {
        transport.send(data)
    }

    internal open fun sendBreak() {
        transport.sendBreak()
    }

    internal open fun setRts(rts: Boolean) {
        transport.setRts(rts)
    }

    internal open fun setDtr(dtr: Boolean) {
        transport.setDtr(dtr)
    }

    private fun getControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return transport.getControlLines()
    }

    private fun getSupportedControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return transport.getSupportedControlLines()
    }

    abstract fun status(str: String)

    open fun controlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {

    }

    open fun supportedControlLines(values: EnumSet<UsbSerialPort.ControlLine?>) {

    }

    open fun onDisconnected() {

    }

    open fun onReconnect(fragment: Fragment, tag: String) {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragment.arguments = arguments
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment, fragment, tag)
            .addToBackStack(null)
            .commit()
    }

    open fun getBundle(): Bundle {
        return Bundle().apply {
            putInt("device", deviceId)
            putInt("port", portNum)
            putInt("baud", baudRate)
            putBoolean("withIoManager", withIoManager)
            putString("connectionType", connectionType)
            putString("bleAddress", bleAddress)
            putString("bleName", bleName)
        }
    }

    // -- DeviceTransport.Listener --
    // BLE's BluetoothGattCallback fires on a background Binder thread by default
    // (unlike USB, which calls back synchronously on whatever thread called
    // connect() - always the main thread here), so every callback that touches UI
    // (dialogs, status(), fragment transactions) must hop to the main thread rather
    // than assume it's already there.

    override fun onStatus(message: String) {
        mainLooper.post {
            lastStatusMessage = message
            status(message)
        }
    }

    override fun onConnected() {
        mainLooper.post {
            status("connected")
            connected = true
            controlLines.start()
            serialNumber = transport.deviceIdentifier
            ConnectionStateRegistry.markConnected(connectionKey)
            connectingDialog?.showSuccess(getString(R.string.connected))
            connectingDialog = null
            onConnectionSuccess()
        }
    }

    override fun onConnectFailed() {
        mainLooper.post {
            connectingDialog?.showFailure(lastStatusMessage.ifBlank { getString(R.string.connection_failed) })
            connectingDialog = null
            onConnectionFailed()
        }
    }

    override fun onDataReceived(data: ByteArray) {
        mainLooper.post { receive(data) }
    }

    override fun onRunError(e: Exception?) {
        Log.d("SerialConnectionFragment", "onRunError")
        mainLooper.post {
            status("connection lost: ${e?.message}")
            disconnect()
            val reconnectDeviceDialog = ReconnectDeviceDialogFragment.Companion.newInstance()
            reconnectDeviceDialog.show(childFragmentManager, "reconnect_device_dialog_fragment")
        }
    }

    inner class ControlLines {
        private val refreshInterval = 200L

        private val runnable = Runnable { run() }

        private fun run() {
            if (!connected) return
            try {
                val values = getControlLines()
                if (values != null) {
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
                if (values != null) {
                    supportedControlLines(values)
                }
                run()
            } catch (e: Exception) {
                Toast.makeText(activity, "getSupportedControlLines() failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun stop() {
            mainLooper.removeCallbacks(runnable)
            SerialConnectionFragment@onDisconnected()
        }
    }

    companion object {
        const val CONNECTION_TYPE_USB = "usb"
        const val CONNECTION_TYPE_BLE = "ble"
    }
}
