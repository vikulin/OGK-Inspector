package io.github.vikulin.opengammakit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import io.github.vikulin.opengammakit.adapter.DeviceInfoAdapter
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.text.iterator

open class InfoFragment() : SerialConnectionFragment() {

    private var driver: String? = null
    private var vendorId: String? = null
    private var productId: String? = null

    private val buffer = StringBuilder()

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
        super.send((OpenGammaKitCommands().setOut("off")).toByteArray())
        super.send((OpenGammaKitCommands().readInfo()).toByteArray())
    }


    /**
     * Parses a JSON array string where each element contains a key-value pair in the format "key|value".
     *
     * The method expects the input string to be a valid JSON array of strings,
     * with each string containing a key and value separated by the '|' character.
     * It extracts these pairs and returns them in a map, preserving the order of appearance.
     *
     * Example input:
     * ```
     * ["CPS|123", "TEMP|25.6", "STATUS|OK"]
     * ```
     * Resulting map:
     * ```
     * {CPS=123, TEMP=25.6, STATUS=OK}
     * ```
     *
     * @param input A JSON array string with elements formatted as "key|value".
     * @return A [Map] of parsed key-value pairs, preserving the insertion order.
     * @throws kotlinx.serialization.json.JsonDecodingException if the input is not a valid JSON array.
     */
    fun parseOutput(input: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val cleaned = input.trim()

        // Ensure it's a valid JSON array
        val json = Json{ allowTrailingComma = true }.decodeFromString(JsonArray.serializer(), cleaned)

        for (element in json) {
            val text = (element as JsonPrimitive).content
            val parts = text.split("|", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                result[key] = value
            }
        }

        return result
    }

    override fun receive(bytes: ByteArray) {
        //Log.d("Test","received ${bytes.size}")
        if (bytes.isNotEmpty()) {
            val inputString = bytes.toString(Charsets.UTF_8)
            //Log.d("Test","received ${bytes.toString(Charsets.UTF_8)}")
            val EOF = '\u0000'
            for (char in inputString) {
                when (char) {
                    '[' -> {
                        buffer.clear()
                        buffer.append(char)
                    }
                    EOF -> {
                        try {
                            val deviceInfo = parseOutput(buffer.toString())
                            deviceInfo.forEach {
                                deviceInfoList[it.key] = it.value
                            }
                            deviceInfoList["Serial Number"] = serialNumber.toString()
                            getDeviceInfo(deviceInfoList)
                            refresh()
                        } catch (e: Exception){
                            e.printStackTrace()
                        } finally {
                            buffer.clear()
                        }
                    }
                    '\r', '\n' -> {}
                    else -> {
                        buffer.append(char)
                    }
                }
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