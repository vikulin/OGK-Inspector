package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R

class ReconnectDeviceDialogFragment : DialogFragment() {

    companion object {

        fun newInstance(): ReconnectDeviceDialogFragment {
            val fragment = ReconnectDeviceDialogFragment()
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Try to attach the listener to the host activity or parent fragment
        connectDeviceListener = when {
            parentFragment is ReconnectDeviceDialogListener -> parentFragment as ReconnectDeviceDialogListener
            context is ReconnectDeviceDialogListener -> context
            else -> throw IllegalStateException("Host must implement ReconnectDeviceDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        // Clear the reference to the listener to avoid memory leaks
        connectDeviceListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_device_reconnect, container, false)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnReconnect = view.findViewById<Button>(R.id.btnReconnect)
        // Set up the close button
        btnCancel.setOnClickListener {
            dismiss() // Close the dialog
        }
        btnReconnect.setOnClickListener {
            connectDeviceListener?.onReconnect()
            dismiss()
        }

        return view
    }

    interface ReconnectDeviceDialogListener {
        fun onReconnect()
    }

    private var connectDeviceListener: ReconnectDeviceDialogListener? = null

}