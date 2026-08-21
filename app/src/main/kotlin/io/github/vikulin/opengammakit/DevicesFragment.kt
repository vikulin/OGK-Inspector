import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import com.hoho.android.usbserial.driver.UsbSerialDriver
import io.github.vikulin.opengammakit.CounterFragment
import io.github.vikulin.opengammakit.InfoFragment
import io.github.vikulin.opengammakit.OpenGammaKitApp
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.SerialConnectionFragment
import io.github.vikulin.opengammakit.SettingsFragment
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.TerminalFragment
import io.github.vikulin.opengammakit.hardware.ConnectionStateRegistry
import io.github.vikulin.opengammakit.hardware.ble.BluetoothDeviceManager
import io.github.vikulin.opengammakit.hardware.usb.UsbDeviceManager
import io.github.vikulin.opengammakit.model.BleConstants
import io.github.vikulin.opengammakit.IsotopeListFragment
import io.github.vikulin.opengammakit.view.SpectrumFileChooserDialogFragment
import java.util.Locale

class DevicesFragment : ListFragment(), SpectrumFileChooserDialogFragment.ChooseFileDialogListener {

    sealed class DeviceListItem {
        data class Usb(
            val device: UsbDevice,
            val port: Int,
            val driver: UsbSerialDriver?
        ) : DeviceListItem()

        data class Ble(
            val device: BluetoothDevice,
            val name: String,
            val address: String
        ) : DeviceListItem()
    }

    private val deviceListItems = ArrayList<DeviceListItem>()
    private lateinit var deviceListAdapter: ArrayAdapter<DeviceListItem>
    private var baudRate = 19200
    private var withIoManager = true
    private lateinit var sharedPreferences: SharedPreferences

    private var bleScanCallback: ScanCallback? = null
    private val bleScanHandler = Handler(Looper.getMainLooper())
    private val bleScanDurationMillis = 10_000L

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startBleScan()
        } else {
            Toast.makeText(requireContext(), R.string.bluetooth_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("AppPreferences", 0)
        val savedOption = sharedPreferences.getString("open_when_boot", "None")

        deviceListAdapter = object : ArrayAdapter<DeviceListItem>(requireActivity(), R.layout.item_device, deviceListItems) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = deviceListItems[position]
                val view = convertView ?: layoutInflater.inflate(R.layout.item_device, parent, false)
                val deviceTypeIcon = view.findViewById<ImageView>(R.id.deviceTypeIcon)
                val deviceLabel = view.findViewById<TextView>(R.id.deviceLabel)
                val usbPort = view.findViewById<TextView>(R.id.usbPort)
                val info = view.findViewById<ImageButton>(R.id.info)
                val terminal = view.findViewById<ImageButton>(R.id.terminal)
                val spectrometer = view.findViewById<ImageButton>(R.id.spectrometer)
                val counter = view.findViewById<ImageButton>(R.id.counter)

                val connectionKey = when (item) {
                    is DeviceListItem.Usb -> ConnectionStateRegistry.usbKey(item.device.deviceId, item.port)
                    is DeviceListItem.Ble -> ConnectionStateRegistry.bleKey(item.address)
                }
                val isConnected = ConnectionStateRegistry.isConnected(connectionKey)
                val tintColor = resources.getColor(
                    if (isConnected) R.color.colorConnected else R.color.colorPrimary, null
                )

                val args: Bundle
                when (item) {
                    is DeviceListItem.Usb -> {
                        deviceTypeIcon.setImageResource(R.drawable.ic_usb)
                        // Rows are recycled by ListView, so this must be set explicitly rather
                        // than relying on item_device.xml's default text (a recycled BLE row
                        // would otherwise keep showing its old device name).
                        deviceLabel.text = getString(R.string.usb_device_label)
                        usbPort.visibility = View.VISIBLE
                        usbPort.text = (item.port + 1).toString()

                        val driver = when {
                            item.driver == null -> "<no driver>"
                            item.driver.ports.size == 1 -> item.driver.javaClass.simpleName.replace("SerialDriver", "")
                            else -> "${item.driver.javaClass.simpleName.replace("SerialDriver", "")}, Port ${item.port}"
                        }
                        val vendor = item.device.vendorId
                        val product = item.device.productId

                        args = Bundle().apply {
                            putInt("device", item.device.deviceId)
                            putInt("port", item.port)
                            putInt("baud", baudRate)
                            putBoolean("withIoManager", withIoManager)
                            putString("connectionType", SerialConnectionFragment.CONNECTION_TYPE_USB)
                        }

                        info.setOnClickListener {
                            val infoArgs = Bundle(args).apply {
                                putString("driver", driver)
                                putString("vendor", String.format(Locale.US, "%04X", vendor))
                                putString("product", String.format(Locale.US, "%04X", product))
                            }
                            val fragment: Fragment = InfoFragment()
                            fragment.arguments = infoArgs
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment, fragment, "info")
                                .addToBackStack(null)
                                .commit()
                        }
                    }
                    is DeviceListItem.Ble -> {
                        deviceTypeIcon.setImageResource(R.drawable.ic_bluetooth)
                        deviceLabel.text = item.name
                        usbPort.visibility = View.GONE

                        args = Bundle().apply {
                            putString("connectionType", SerialConnectionFragment.CONNECTION_TYPE_BLE)
                            putString("bleAddress", item.address)
                            putString("bleName", item.name)
                            putBoolean("withIoManager", true)
                        }

                        info.setOnClickListener {
                            // InfoFragment fills in Vendor/Product/Device Id itself for BLE,
                            // based on connectionType - no USB-style placeholders needed here.
                            val fragment: Fragment = InfoFragment()
                            fragment.arguments = Bundle(args)
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment, fragment, "info")
                                .addToBackStack(null)
                                .commit()
                        }
                    }
                }
                deviceTypeIcon.setColorFilter(tintColor)

                counter.setOnClickListener {
                    val fragment: Fragment = CounterFragment()
                    fragment.arguments = Bundle(args)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "counter")
                        .addToBackStack(null)
                        .commit()
                }
                spectrometer.setOnClickListener {
                    val fragment: Fragment = SpectrumFragment()
                    fragment.arguments = Bundle(args)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "spectrometer")
                        .addToBackStack(null)
                        .commit()
                }
                terminal.setOnClickListener {
                    val fragment: Fragment = TerminalFragment()
                    fragment.arguments = Bundle(args)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment, fragment, "terminal")
                        .addToBackStack(null)
                        .commit()
                }

                if (savedOption != "None" && position == deviceListItems.size - 1) {
                    val appInstance = requireActivity().application as OpenGammaKitApp
                    if (!appInstance.isBootDone) {
                        // last element processed
                        appInstance.isBootDone = true
                        when (savedOption) {
                            "Counter" -> {
                                counter.performClick()
                            }
                            "Spectrum" -> {
                                spectrometer.performClick()
                            }
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

        val btnScanBle = view.findViewById<ImageButton>(R.id.btnScanBle)
        btnScanBle.setOnClickListener {
            refreshUsb()
            if (BluetoothDeviceManager.hasBlePermission(requireContext())) {
                refreshBonded()
                startBleScan()
            } else {
                requestBlePermissions.launch(BluetoothDeviceManager.requiredPermissions())
            }
        }

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
        setListAdapter(deviceListAdapter)
    }

    override fun onResume() {
        super.onResume()
        refreshUsb()
        refreshBonded()
        deviceListAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        stopBleScan()
    }

    private fun refreshUsb() {
        deviceListItems.removeAll { it is DeviceListItem.Usb }
        val usbItems = ArrayList<UsbDeviceManager.UsbListItem>()
        UsbDeviceManager.setUsbDevices(requireActivity(), usbItems)
        usbItems.forEach {
            deviceListItems.add(DeviceListItem.Usb(it.device, it.port, it.driver))
        }
        deviceListAdapter.notifyDataSetChanged()
    }

    private fun refreshBonded() {
        if (!BluetoothDeviceManager.hasBlePermission(requireContext())) return
        BluetoothDeviceManager.getBondedDevices(requireContext()).forEach { addBleDevice(it) }
    }

    private fun startBleScan() {
        stopBleScan()
        bleScanCallback = BluetoothDeviceManager.startScan(requireContext()) { device ->
            requireActivity().runOnUiThread { addBleDevice(device) }
        }
        if (bleScanCallback == null) {
            Toast.makeText(requireContext(), R.string.bluetooth_scan_failed, Toast.LENGTH_SHORT).show()
        } else {
            bleScanHandler.postDelayed({ stopBleScan() }, bleScanDurationMillis)
        }
    }

    private fun stopBleScan() {
        bleScanCallback?.let {
            BluetoothDeviceManager.stopScan(requireContext(), it)
        }
        bleScanCallback = null
        bleScanHandler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("MissingPermission") // guarded by hasBlePermission() before scan/bonded lookup starts
    private fun addBleDevice(device: BluetoothDevice) {
        val address = device.address ?: return
        if (deviceListItems.any { it is DeviceListItem.Ble && it.address == address }) return
        val name = try {
            device.name
        } catch (e: SecurityException) {
            null
        }
        // Only surface the target spectrometer, not every nearby BLE peripheral.
        if (name == null || !name.startsWith(BleConstants.DEVICE_NAME_PREFIX, ignoreCase = true)) {
            return
        }
        deviceListItems.add(DeviceListItem.Ble(device, name, address))
        deviceListAdapter.notifyDataSetChanged()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("DeviceFragment", "Device attached: ${it.deviceName}")
                        refreshUsb()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("USB", "Device detached: ${it.deviceName}")
                        refreshUsb()
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

    override fun onChoose(uri: String) {
        val args = Bundle().apply {
            putString("file_spectrum_uri", uri)
        }
        val fragment = SpectrumFragment().apply {
            arguments = args
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment, fragment, "spectrum_view")
            .addToBackStack(null)
            .commit()
    }
}
