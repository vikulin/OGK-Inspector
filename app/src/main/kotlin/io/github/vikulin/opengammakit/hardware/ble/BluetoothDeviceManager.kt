package io.github.vikulin.opengammakit.hardware.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BLE device discovery, parallel to hardware.usb.UsbDeviceManager. Scans without a
 * service-UUID filter (the exact advertised UUID isn't confirmed against real
 * firmware yet - see BleConstants) so any nearby BLE peripheral shows up and the
 * user can pick theirs by name/address; bonded devices are listed immediately
 * without needing to wait for an active scan to see them again.
 */
object BluetoothDeviceManager {

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasBlePermission(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission") // checked via hasBlePermission() before use
    fun getBondedDevices(context: Context): Set<BluetoothDevice> {
        if (!hasBlePermission(context)) return emptySet()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter?.bondedDevices ?: emptySet()
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context, onDeviceFound: (BluetoothDevice) -> Unit): ScanCallback? {
        if (!hasBlePermission(context)) return null
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) return null
        val scanner = adapter.bluetoothLeScanner ?: return null

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDeviceFound(result.device)
            }
        }
        scanner.startScan(null, settings, callback)
        return callback
    }

    @SuppressLint("MissingPermission")
    fun stopScan(context: Context, callback: ScanCallback) {
        if (!hasBlePermission(context)) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.bluetoothLeScanner?.stopScan(callback)
    }
}
