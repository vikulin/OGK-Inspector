package io.github.vikulin.opengammakit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.SimpleAdapter
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.iterator

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
        super.send((OpenGammaKitCommands().setOut("off")+'\n').toByteArray())
        super.send((OpenGammaKitCommands().readInfo()+'\n').toByteArray())
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

    private var openBraces = 0

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
                        openBraces++
                        buffer.append(char)
                    }
                    ']' -> {
                        openBraces--
                        buffer.append(char)
                    }
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
                        buffer.clear()
                        return
                    }
                    '\r' -> {}
                    '\n' -> {}
                    else -> {
                        buffer.append(char)
                    }
                }
                if(openBraces>1 || openBraces<0){
                    Log.e("Test", "openBraces issue: $buffer")
                    buffer.clear() // Discard malformed or incomplete JSON
                    openBraces = 0
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