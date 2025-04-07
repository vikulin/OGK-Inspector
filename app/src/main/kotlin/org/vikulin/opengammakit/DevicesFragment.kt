import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.vikulin.opengammakit.CustomProber
import org.vikulin.opengammakit.InfoFragment
import org.vikulin.opengammakit.R
import org.vikulin.opengammakit.SpectrumChartFragment
import org.vikulin.opengammakit.TerminalFragment
import java.util.Locale

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
        listAdapter = object : ArrayAdapter<ListItem>(requireActivity(), R.layout.device_list_item, listItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = listItems[position]
                val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val deviceLabel = view.findViewById<TextView>(R.id.deviceLabel)
                val deviceId = view.findViewById<TextView>(R.id.deviceId)
                val info = view.findViewById<ImageButton>(R.id.info)
                val terminal = view.findViewById<ImageButton>(R.id.terminal)
                val spectrometer = view.findViewById<ImageButton>(R.id.spectrometer)

                val driver = when {
                    item.driver == null -> "<no driver>"
                    item.driver.ports.size == 1 -> item.driver.javaClass.simpleName.replace("SerialDriver", "")
                    else -> "${item.driver.javaClass.simpleName.replace("SerialDriver", "")}, Port ${item.port}"
                }

                val vendor = item.device.vendorId
                val product = item.device.productId
                deviceId.text = item.device.deviceId.toString()
                terminal.setOnClickListener {
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
                spectrometer.setOnClickListener {
                    val args = Bundle().apply {
                        putInt("device", item.device.deviceId)
                        putInt("port", item.port)
                        putInt("baud", baudRate)
                        putBoolean("withIoManager", withIoManager)
                    }
                    val fragment: Fragment = SpectrumChartFragment()
                    fragment.arguments = args
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "spectrometer")
                        .addToBackStack(null)
                        .commit()
                }
                info.setOnClickListener {
                    val args = Bundle().apply {
                        putString("driver", driver)
                        putString("vendor", String.format(
                            Locale.US,
                            "%04X",
                            vendor
                        ))
                        putString("product",
                            String.format(
                                Locale.US,
                                "%04X",
                                product
                            ))
                        putInt("device", item.device.deviceId)
                        putInt("port", item.port)
                        putInt("baud", baudRate)
                        putBoolean("withIoManager", withIoManager)
                    }
                    val fragment: Fragment = InfoFragment()
                    fragment.arguments = args
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "info")
                        .addToBackStack(null)
                        .commit()
                }
                return view
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_devices, container, false)

        // Find the ListView and the empty TextView
        val listView = view.findViewById<ListView>(android.R.id.list)
        listView.emptyView = view.findViewById(R.id.empty)

        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListAdapter(listAdapter)
    }

    override fun onResume() {
        super.onResume()
        refresh()
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