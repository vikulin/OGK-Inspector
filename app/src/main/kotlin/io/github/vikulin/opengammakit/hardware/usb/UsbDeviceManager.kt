package io.github.vikulin.opengammakit.hardware.usb

import DevicesFragment.ListItem
import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.vikulin.opengammakit.CustomProber

object UsbDeviceManager {

    fun setUsbDevices(context: Context, listItems: ArrayList<ListItem>){
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDefaultProber = UsbSerialProber.getDefaultProber()
        val usbCustomProber = CustomProber.getCustomProber()

        for (device in usbManager.deviceList.values) {
            var driver = usbDefaultProber.probeDevice(device) ?: usbCustomProber.probeDevice(device)
            driver?.let {
                for (port in 0 until it.ports.size) {
                    listItems.add(ListItem(device, port, it))
                }
            }
        }
    }
}