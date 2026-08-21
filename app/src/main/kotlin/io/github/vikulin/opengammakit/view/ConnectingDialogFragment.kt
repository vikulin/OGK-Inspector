package io.github.vikulin.opengammakit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R

/**
 * Transient "Connecting…" progress popup shown while a BLE connection attempt is
 * in flight (SerialConnectionFragment.connect()). Dismissed immediately on success;
 * updated in place via showFailure() if the async GATT connection fails instead.
 */
class ConnectingDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MESSAGE = "message"

        fun newInstance(message: String): ConnectingDialogFragment {
            return ConnectingDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }

    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var btnClose: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = false
        val view = inflater.inflate(R.layout.dialog_connecting, container, false)
        progressBar = view.findViewById(R.id.connectingProgress)
        statusText = view.findViewById(R.id.connectingStatus)
        btnClose = view.findViewById<Button>(R.id.btnConnectingClose).apply {
            setOnClickListener { dismissSafely() }
        }
        statusText?.text = arguments?.getString(ARG_MESSAGE)
        return view
    }

    fun showFailure(message: String) {
        if (!isAdded) return
        progressBar?.visibility = View.GONE
        statusText?.text = message
        btnClose?.visibility = View.VISIBLE
    }

    fun dismissSafely() {
        if (isAdded) {
            dismissAllowingStateLoss()
        }
    }
}
