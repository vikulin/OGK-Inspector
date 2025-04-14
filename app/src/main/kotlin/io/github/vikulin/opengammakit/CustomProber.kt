package io.github.vikulin.opengammakit

import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Add devices here, that are not known to DefaultProber
 *
 * If the App should auto start for these devices, also
 * add IDs to app/src/main/res/xml/device_filter.xml
 */
class CustomProber {

    companion object {
        fun getCustomProber(): UsbSerialProber {
            val customTable = ProbeTable()
            customTable.addProduct(0x1234, 0x0001, FtdiSerialDriver::class.java) // e.g. device with custom VID+PID
            customTable.addProduct(0x1234, 0x0002, FtdiSerialDriver::class.java) // e.g. device with custom VID+PID
            return UsbSerialProber(customTable)
        }
    }
}