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
import android.util.Log
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

    companion object {
        private const val TAG = "BleSerialTransport"

        private fun hex(data: ByteArray): String = data.joinToString(" ") { "%02X".format(it) }
    }

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
            Log.d(TAG, "onConnectionStateChange: status=$status newState=$newState")
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
            Log.d(TAG, "onServicesDiscovered: status=$status, services=${g.services.map { it.uuid }}")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                transportListener?.onStatus("connection failed: service discovery failed")
                transportListener?.onConnectFailed()
                return
            }

            val service = g.getService(BleConstants.UART_SERVICE_UUID)
            val rx = service?.getCharacteristic(BleConstants.UART_RX_CHARACTERISTIC_UUID)
            val tx = service?.getCharacteristic(BleConstants.UART_TX_CHARACTERISTIC_UUID)
            Log.d(TAG, "UART service found=${service != null}, rx found=${rx != null}, tx found=${tx != null}" +
                (service?.let { ", service characteristics=${it.characteristics.map { c -> c.uuid }}" } ?: ""))

            if (rx == null || tx == null) {
                transportListener?.onStatus("connection failed: UART service not found on device")
                transportListener?.onConnectFailed()
                return
            }

            rxCharacteristic = rx

            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            Log.d(TAG, "TX CCCD descriptor found=${cccd != null}, tx descriptors=${tx.descriptors.map { it.uuid }}")
            if (cccd != null) {
                writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                Log.w(TAG, "No CCCD descriptor on TX characteristic - notifications will not be enabled on the peripheral")
            }

            g.requestMtu(517)

            connectedOnce = true
            transportListener?.onConnected()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite: uuid=${descriptor.uuid} status=$status")
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu status=$status")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.UART_TX_CHARACTERISTIC_UUID) {
                val value = characteristic.value ?: ByteArray(0)
                Log.d(TAG, "RX << ${value.size} bytes [${hex(value)}] \"${String(value, Charsets.UTF_8)}\"")
                transportListener?.onDataReceived(value)
            } else {
                Log.d(TAG, "onCharacteristicChanged for unexpected characteristic ${characteristic.uuid}")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: status=$status")
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
        val result = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            if (g.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else -1
        }
        Log.d(TAG, "writeCharacteristic() result=$result")
    }

    private fun pumpWriteQueue(g: BluetoothGatt) {
        if (writeInFlight) {
            Log.d(TAG, "pumpWriteQueue: write already in flight, waiting")
            return
        }
        val characteristic = rxCharacteristic
        if (characteristic == null) {
            Log.w(TAG, "pumpWriteQueue: no RX characteristic (not connected yet?)")
            return
        }
        val chunk = synchronized(writeQueue) { writeQueue.poll() } ?: return
        writeInFlight = true
        Log.d(TAG, "TX >> ${chunk.size} bytes [${hex(chunk)}]")
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
        Log.d(TAG, "send() called: \"${String(data, Charsets.UTF_8)}\"")
        val g = gatt
        if (g == null) {
            Log.w(TAG, "send(): no GATT connection, dropping")
            return
        }
        // WESPP requires an RF_HEADER_TYPE_DATA byte in front of the payload on every
        // RX-characteristic write (see BleConstants.RF_HEADER_TYPE_DATA) - app-side
        // framing only, the device firmware neither expects nor needs to touch this.
        val framed = byteArrayOf(BleConstants.RF_HEADER_TYPE_DATA) + data
        var offset = 0
        synchronized(writeQueue) {
            while (offset < framed.size) {
                val end = minOf(offset + defaultChunkSize, framed.size)
                writeQueue.add(framed.copyOfRange(offset, end))
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
