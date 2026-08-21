package io.github.vikulin.opengammakit.hardware.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hoho.android.usbserial.driver.UsbSerialPort
import io.github.vikulin.opengammakit.hardware.DeviceTransport
import io.github.vikulin.opengammakit.model.BleConstants
import java.util.EnumSet
import java.util.LinkedList

/**
 * BLE GATT transport for the Wurth Proteus-III UART service (see BleConstants).
 * Data arrives asynchronously via characteristic notifications rather than a
 * blocking read, so read() is a no-op stub and connections always behave like
 * USB's "withIoManager" push mode.
 */
@SuppressLint("MissingPermission") // permission is checked in connect() before any GATT call is made
class BleSerialTransport(private val address: String) : DeviceTransport {

    override var deviceIdentifier: String? = address
        private set

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var transportListener: DeviceTransport.Listener? = null
    private var connectedOnce = false

    // GATT allows only one outstanding write at a time; queue and drain via onCharacteristicWrite.
    private val writeQueue = LinkedList<ByteArray>()
    private var writeInFlight = false

    // Conservative default chunk size before MTU negotiation confirms a larger safe payload.
    private val defaultChunkSize = 180

    private fun hasConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun connect(fragment: Fragment, listener: DeviceTransport.Listener) {
        transportListener = listener
        val context = fragment.requireContext()

        if (!hasConnectPermission(context)) {
            listener.onStatus("connection failed: BLUETOOTH_CONNECT permission not granted")
            listener.onConnectFailed()
            return
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onStatus("connection failed: bluetooth not enabled")
            listener.onConnectFailed()
            return
        }

        connectedOnce = false
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    transportListener?.onStatus("discovering services")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedOnce) {
                        transportListener?.onRunError(null)
                    } else {
                        transportListener?.onStatus("connection failed: gatt status $status")
                        transportListener?.onConnectFailed()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                transportListener?.onStatus("connection failed: service discovery failed")
                transportListener?.onConnectFailed()
                return
            }

            val service = g.getService(BleConstants.UART_SERVICE_UUID)
            val rx = service?.getCharacteristic(BleConstants.UART_RX_CHARACTERISTIC_UUID)
            val tx = service?.getCharacteristic(BleConstants.UART_TX_CHARACTERISTIC_UUID)

            if (rx == null || tx == null) {
                transportListener?.onStatus("connection failed: UART service not found on device")
                transportListener?.onConnectFailed()
                return
            }

            rxCharacteristic = rx

            g.setCharacteristicNotification(tx, true)
            tx.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let {
                writeDescriptor(g, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }

            g.requestMtu(517)

            connectedOnce = true
            transportListener?.onConnected()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.UART_TX_CHARACTERISTIC_UUID) {
                transportListener?.onDataReceived(characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            pumpWriteQueue(g)
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun writeCharacteristic(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        // WESPP's RX characteristic is "UnacknowledgedData" mode (writeWithoutResponse) -
        // see BleConstants / Wurth's Proteus-Connect WESPPProfile.ts.
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private fun pumpWriteQueue(g: BluetoothGatt) {
        if (writeInFlight) return
        val characteristic = rxCharacteristic ?: return
        val chunk = synchronized(writeQueue) { writeQueue.poll() } ?: return
        writeInFlight = true
        writeCharacteristic(g, characteristic, chunk)
    }

    override fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxCharacteristic = null
        synchronized(writeQueue) { writeQueue.clear() }
        writeInFlight = false
        connectedOnce = false
    }

    override fun read(): ByteArray {
        // BLE has no blocking read; data is pushed via onCharacteristicChanged instead.
        return ByteArray(0)
    }

    override fun send(data: ByteArray) {
        val g = gatt ?: return
        var offset = 0
        synchronized(writeQueue) {
            while (offset < data.size) {
                val end = minOf(offset + defaultChunkSize, data.size)
                writeQueue.add(data.copyOfRange(offset, end))
                offset = end
            }
        }
        pumpWriteQueue(g)
    }

    override fun sendBreak() {
        // Not a meaningful concept over BLE.
    }

    override fun setRts(rts: Boolean) {
        // Not a meaningful concept over BLE.
    }

    override fun setDtr(dtr: Boolean) {
        // Not a meaningful concept over BLE.
    }

    override fun getControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return EnumSet.noneOf(UsbSerialPort.ControlLine::class.java)
    }

    override fun getSupportedControlLines(): EnumSet<UsbSerialPort.ControlLine?>? {
        return EnumSet.noneOf(UsbSerialPort.ControlLine::class.java)
    }
}
