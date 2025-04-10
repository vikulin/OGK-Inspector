package org.vikulin.opengammakit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.SimpleAdapter
import org.vikulin.opengammakit.model.OpenGammaKitCommands
import kotlin.collections.component1
import kotlin.collections.component2

class InfoFragment() : SerialConnectionFragment() {

    private var driver: String? = null
    private var vendorId: String? = null
    private var productId: String? = null

    private val buffer = StringBuilder()

    private lateinit var listView: ListView
    private var deviceInfoList = mutableListOf<Map<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        arguments?.let {
            deviceId = it.getInt("device")
            portNum = it.getInt("port")
            baudRate = it.getInt("baud")
            withIoManager = it.getBoolean("withIoManager")
            driver = it.getString("driver")
            vendorId = it.getString("vendor")
            productId = it.getString("product")
        }
    }

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        super.setDtr(true)
        super.send((OpenGammaKitCommands().readInfo()+'\n').toByteArray())
    }



    fun parseOutput(input: String): Map<String, String> {
        val result = LinkedHashMap<String, String>() // Maintains insertion order

        // Split the input by the "[#]" separator
        val lines = input.split("[#]").map { it.replace("\n", "").replace("\r", "").trim() }

        for (line in lines) {
            // Skip empty lines or those without key-value pairs
            if (line.contains("|")) {
                // Split into key and value parts
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].replace("\n", "").replace("\r", "").trim()
                    val value = parts[1].replace("\n", "").replace("\r", "").trim()
                    result[key] = value
                }
            }
        }
        return result
    }

    override fun receive(bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            val inputString = bytes.toString(Charsets.UTF_8)
            val EOF = '\u0000'
            //Log.d("Test","received $inputString")
            for (char in inputString) {
                when (char) {
                    EOF -> {
                        val deviceInfo = parseOutput(buffer.toString())

                        deviceInfoList.addAll(deviceInfo.map { (key, value) ->
                            mapOf("key" to key, "value" to value)
                        })

                        val adapter = SimpleAdapter(
                            requireContext(),
                            deviceInfoList, // Use the converted list
                            R.layout.info_list_item,
                            arrayOf("key", "value"),
                            intArrayOf(R.id.keyText, R.id.valueText)
                        )
                        listView.adapter = adapter
                        adapter.notifyDataSetChanged()
                        return
                    }
                    else -> buffer.append(inputString)
                }
            }
        }
    }

    override fun status(str: String) {

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_info, container, false)
        deviceInfoList.addAll(
            listOf(
                mapOf("key" to "Driver", "value" to (driver ?: "<no driver>")),
                mapOf("key" to "Vendor", "value" to (vendorId ?: "Unknown Vendor")),
                mapOf("key" to "Product", "value" to (productId ?: "Unknown Product")),
                mapOf("key" to "Device Id", "value" to "$deviceId"),
                mapOf("key" to "Port", "value" to "$portNum"),
                mapOf("key" to "Baud", "value" to "$baudRate"),
                mapOf("key" to "With IoManager", "value" to "$withIoManager")
            )
        )

        listView = view.findViewById(R.id.keyValueListView)

        return view
    }
}