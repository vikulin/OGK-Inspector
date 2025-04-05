package org.vikulin.opengammakit

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.*

class DevicesFragment : ListFragment() {

    data class ListItem(
        val device: UsbDevice,
        val port: Int,
        val driver: UsbSerialDriver?
    )

    private val listItems = ArrayList<ListItem>()
    private lateinit var listAdapter: ArrayAdapter<ListItem>
    private var baudRate = 19200
    private var withIoManager = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        listAdapter = object : ArrayAdapter<ListItem>(requireActivity(), 0, listItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = listItems[position]
                val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val text1 = view.findViewById<TextView>(R.id.text1)
                val text2 = view.findViewById<TextView>(R.id.text2)
                text1.text = when {
                    item.driver == null -> "<no driver>"
                    item.driver.ports.size == 1 -> item.driver.javaClass.simpleName.replace("SerialDriver", "")
                    else -> "${item.driver.javaClass.simpleName.replace("SerialDriver", "")}, Port ${item.port}"
                }
                text2.text = String.format(
                    Locale.US,
                    "Vendor %04X, Product %04X",
                    item.device.vendorId,
                    item.device.productId
                )
                return view
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(null)
        val header = layoutInflater.inflate(R.layout.device_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("<no USB devices found>")
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices, menu)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                refresh()
                true
            }
            R.id.baud_rate -> {
                val values = resources.getStringArray(R.array.baud_rates)
                val pos = values.indexOf(baudRate.toString())
                AlertDialog.Builder(requireActivity())
                    .setTitle("Baud rate")
                    .setSingleChoiceItems(values, pos) { dialog, which ->
                        baudRate = values[which].toInt()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                true
            }
            R.id.read_mode -> {
                val values = resources.getStringArray(R.array.read_modes)
                val pos = if (withIoManager) 0 else 1
                AlertDialog.Builder(requireActivity())
                    .setTitle("Read mode")
                    .setSingleChoiceItems(values, pos) { dialog, which ->
                        withIoManager = which == 0
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        val usbManager = requireActivity().getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDefaultProber = UsbSerialProber.getDefaultProber()
        val usbCustomProber = CustomProber.getCustomProber()
        listItems.clear()
        for (device in usbManager.deviceList.values) {
            var driver = usbDefaultProber.probeDevice(device)
            if (driver == null) {
                driver = usbCustomProber.probeDevice(device)
            }
            if (driver != null) {
                for (port in 0 until driver.ports.size) {
                    listItems.add(ListItem(device, port, driver))
                }
            } else {
                listItems.add(ListItem(device, 0, null))
            }
        }
        listAdapter.notifyDataSetChanged()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val item = listItems[position - 1]
        if (item.driver == null) {
            Toast.makeText(activity, "no driver", Toast.LENGTH_SHORT).show()
        } else {
            val args = Bundle().apply {
                putInt("device", item.device.deviceId)
                putInt("port", item.port)
                putInt("baud", baudRate)
                putBoolean("withIoManager", withIoManager)
            }
            val fragment: Fragment = TerminalFragment()
            fragment.arguments = args
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment, fragment, "terminal")
                .addToBackStack(null)
                .commit()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("USB", "Device attached: ${it.deviceName}")
                        refresh()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("USB", "Device detached: ${it.deviceName}")
                        refresh()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        requireActivity().registerReceiver(usbReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unregisterReceiver(usbReceiver)
    }
}