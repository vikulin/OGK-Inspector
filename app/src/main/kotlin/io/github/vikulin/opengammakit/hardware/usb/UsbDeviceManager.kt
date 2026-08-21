package io.github.vikulin.opengammakit.hardware.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.vikulin.opengammakit.CustomProber

object UsbDeviceManager {

    data class UsbListItem(
        val device: UsbDevice,
        val port: Int,
        val driver: UsbSerialDriver?
    )

    fun setUsbDevices(context: Context, listItems: ArrayList<UsbListItem>){
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDefaultProber = UsbSerialProber.getDefaultProber()
        val usbCustomProber = CustomProber.getCustomProber()

        for (device in usbManager.deviceList.values) {
            var driver = usbDefaultProber.probeDevice(device) ?: usbCustomProber.probeDevice(device)
            driver?.let {
                for (port in 0 until it.ports.size) {
                    listItems.add(UsbListItem(device, port, it))
                }
            }
        }
    }
}
