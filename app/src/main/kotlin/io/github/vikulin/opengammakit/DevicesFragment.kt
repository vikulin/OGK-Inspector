import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import io.github.vikulin.opengammakit.CounterFragment
import io.github.vikulin.opengammakit.InfoFragment
import io.github.vikulin.opengammakit.OpenGammaKitApp
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.SettingsFragment
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.TerminalFragment
import io.github.vikulin.opengammakit.hardware.usb.UsbDeviceManager
import io.github.vikulin.opengammakit.view.IsotopeListFragment
import io.github.vikulin.opengammakit.view.SpectrumFileChooserDialogFragment
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
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("AppPreferences", 0)
        val savedOption = sharedPreferences.getString("open_when_boot", "None")

        listAdapter = object : ArrayAdapter<ListItem>(requireActivity(), R.layout.device_list_item, listItems) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = listItems[position]
                val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val deviceLabel = view.findViewById<TextView>(R.id.deviceLabel)
                val usbPort = view.findViewById<TextView>(R.id.usbPort)
                val info = view.findViewById<ImageButton>(R.id.info)
                val terminal = view.findViewById<ImageButton>(R.id.terminal)
                val spectrometer = view.findViewById<ImageButton>(R.id.spectrometer)
                val counter = view.findViewById<ImageButton>(R.id.counter)

                val driver = when {
                    item.driver == null -> "<no driver>"
                    item.driver.ports.size == 1 -> item.driver.javaClass.simpleName.replace("SerialDriver", "")
                    else -> "${item.driver.javaClass.simpleName.replace("SerialDriver", "")}, Port ${item.port}"
                }

                val vendor = item.device.vendorId
                val product = item.device.productId
                usbPort.text = (item.port+1).toString()
                counter.setOnClickListener {
                    val args = Bundle().apply {
                        putInt("device", item.device.deviceId)
                        putInt("port", item.port)
                        putInt("baud", baudRate)
                        putBoolean("withIoManager", withIoManager)
                    }
                    val fragment: Fragment = CounterFragment()
                    fragment.arguments = args
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "counter")
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
                    val fragment: Fragment = SpectrumFragment()
                    fragment.arguments = args
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "spectrometer")
                        .addToBackStack(null)
                        .commit()
                }
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
                if(savedOption != "None" && position == listItems.size - 1){
                    val appInstance = requireActivity().application as OpenGammaKitApp
                    if(!appInstance.isBootDone) {
                        // last element processed
                        appInstance.isBootDone = true
                        when(savedOption) {
                            "Counter" -> {counter.performClick()}
                            "Spectrum" -> {spectrometer.performClick()}
                            else -> {}
                        }
                    }
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
        val btnOpenFile = view.findViewById<ImageButton>(R.id.btnOpenFile)
        btnOpenFile.setOnClickListener {
            val spectrumFileChooserDialog = SpectrumFileChooserDialogFragment()
            spectrumFileChooserDialog.show(childFragmentManager, "spectrum_file_chooser_dialog_fragment")
        }
        val btnIsotope = view.findViewById<ImageButton>(R.id.btnIsotope)
        btnIsotope.setOnClickListener {
            val fragment: Fragment = IsotopeListFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment, fragment, "isotope_list_fragment")
                .addToBackStack(null)
                .commit()
        }
        val btnSettings = view.findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            val fragment: Fragment = SettingsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment, fragment, "app_settings_fragment")
                .addToBackStack(null)
                .commit()
        }
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
        listItems.clear()
        UsbDeviceManager.setUsbDevices(requireActivity(), listItems)
        listAdapter.notifyDataSetChanged()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("DeviceFragment", "Device attached: ${it.deviceName}")
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