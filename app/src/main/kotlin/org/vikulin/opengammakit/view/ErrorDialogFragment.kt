package org.vikulin.opengammakit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.vikulin.opengammakit.R

class ErrorDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_ERROR_MESSAGE = "error_message"

        fun newInstance(errorMessage: String): ErrorDialogFragment {
            val fragment = ErrorDialogFragment()
            val args = Bundle()
            args.putString(ARG_ERROR_MESSAGE, errorMessage)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_error, container, false)
        val errorMessageTextView = view.findViewById<TextView>(R.id.errorMessageTextView)
        val btnClose = view.findViewById<Button>(R.id.btnClose)
        // Retrieve the error message from arguments
        val errorMessage = arguments?.getString(ARG_ERROR_MESSAGE) ?: "Unknown error occurred"
        errorMessageTextView.text = errorMessage // Display the error message
        
        // Set up the close button
        btnClose.setOnClickListener {
            dismiss() // Close the dialog
        }

        return view
    }
}