package org.vikulin.opengammakit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class InfoFragment : Fragment() {

    private var deviceId: Int = 0
    private var portNum: Int = 0
    private var baudRate: Int = 0
    private var withIoManager: Boolean = false
    private var driver: String? = null
    private var vendorId: String? = null
    private var productId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_info, container, false)

        // Assign values to corresponding TextViews
        view.findViewById<TextView>(R.id.driverText)?.text = driver ?: "<no driver>"
        view.findViewById<TextView>(R.id.vendorText)?.text = vendorId ?: "Unknown Vendor"
        view.findViewById<TextView>(R.id.productText)?.text = productId ?: "Unknown Product"
        view.findViewById<TextView>(R.id.deviceIdText)?.text = "$deviceId"
        view.findViewById<TextView>(R.id.portText)?.text = "$portNum"
        view.findViewById<TextView>(R.id.baudText)?.text = "$baudRate"
        view.findViewById<TextView>(R.id.ioManagerText)?.text = "$withIoManager"

        return view
    }
}