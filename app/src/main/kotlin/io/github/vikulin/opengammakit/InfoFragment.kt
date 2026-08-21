package io.github.vikulin.opengammakit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import io.github.vikulin.opengammakit.adapter.DeviceInfoAdapter
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class InfoFragment() : SerialConnectionFragment() {

    private var driver: String? = null
    private var vendorId: String? = null
    private var productId: String? = null

    private val buffer = StringBuilder()
    private var openBraces = 0

    private lateinit var listView: ListView
    internal var deviceInfoList = LinkedHashMap<String, String>()

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
        super.send(OpenGammaKitCommands().requestStatus().toByteArray())
    }

    /**
     * Parses a single JSON "status" message (Core/Src/protocol.c Protocol_SerializeStatus()):
     * {"type":"status","battery_percent":<uint>,"battery_voltage":<uint>,
     *  "temperature":<float>,"pulse_count":<uint>,"cps":<uint>}
     */
    private fun handleStatusMessage(json: String) {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root["type"]?.jsonPrimitive?.contentOrNull != "status") {
            return
        }

        val statusInfo = LinkedHashMap<String, String>()
        root["battery_percent"]?.jsonPrimitive?.contentOrNull?.let { statusInfo["Battery"] = "$it%" }
        root["battery_voltage"]?.jsonPrimitive?.contentOrNull?.let { statusInfo["Battery Voltage"] = "$it mV" }
        root["temperature"]?.jsonPrimitive?.contentOrNull?.let { statusInfo["Temperature"] = "$it °C" }
        root["pulse_count"]?.jsonPrimitive?.contentOrNull?.let { statusInfo["Pulse Count"] = it }
        root["cps"]?.jsonPrimitive?.contentOrNull?.let { statusInfo["CPS"] = it }

        deviceInfoList.putAll(statusInfo)
        deviceInfoList["Serial Number"] = serialNumber.toString()
        getDeviceInfo(deviceInfoList)
        refresh()
    }

    override fun receive(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val inputString = bytes.toString(Charsets.UTF_8)
        for (char in inputString) {
            when (char) {
                '{' -> {
                    openBraces++
                    buffer.append(char)
                }
                '}' -> {
                    openBraces--
                    buffer.append(char)
                }
                '\r', '\n' -> {} // Ignore these
                else -> {
                    if (openBraces > 0) {
                        buffer.append(char)
                    }
                }
            }
            if (openBraces == 0 && buffer.isNotEmpty()) {
                try {
                    handleStatusMessage(buffer.toString())
                } catch (e: Exception) {
                    Log.e("InfoFragment", "Failed to parse status message: ${e.message}")
                } finally {
                    buffer.clear()
                }
            }
            if (openBraces < 0) {
                Log.e("InfoFragment", "openBraces issue, discarding buffer")
                buffer.clear()
                openBraces = 0
            }
        }
    }

    open fun getDeviceInfo(deviceInfoList: LinkedHashMap<String, String>){
        val adapter = DeviceInfoAdapter(requireContext(), deviceInfoList)
        listView.adapter = adapter
    }

    private fun refresh(){
        (listView.adapter as DeviceInfoAdapter).notifyDataSetChanged()
    }

    override fun status(str: String) {

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_info, container, false)
        deviceInfoList.putAll(
            mapOf(
                "Driver" to (driver ?: "<no driver>"),
                "Vendor" to (vendorId ?: "Unknown Vendor"),
                "Product" to (productId ?: "Unknown Product"),
                "Device Id" to "$deviceId",
                "Port" to "$portNum",
                "Baud" to "$baudRate"
            )
        )

        listView = view.findViewById(R.id.keyValueListView)

        return view
    }

    override fun onReconnect() {
    }
}
