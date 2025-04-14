package org.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.vikulin.opengammakit.R

class CounterThresholdDialogFragment : DialogFragment() {

    companion object {
        private const val COUNTER_THRESHOLD = "counter_threshold"

        fun newInstance(counterThreshold: Int): CounterThresholdDialogFragment {
            val fragment = CounterThresholdDialogFragment()
            val args = Bundle()
            args.putInt(COUNTER_THRESHOLD, counterThreshold)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Try to attach the listener to the host activity or parent fragment
        saveThresholdListener = when {
            parentFragment is SaveThresholdDialogListener -> parentFragment as SaveThresholdDialogListener
            context is SaveThresholdDialogListener -> context
            else -> throw IllegalStateException("Host must implement SaveThresholdDialogListener")
        }
        chooseThresholdListener = when {
            parentFragment is ChooseThresholdDialogListener -> parentFragment as ChooseThresholdDialogListener
            context is ChooseThresholdDialogListener -> context
            else -> throw IllegalStateException("Host must implement ChooseThresholdDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        // Clear the reference to the listener to avoid memory leaks
        saveThresholdListener = null
        chooseThresholdListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_counter_threshold, container, false)
        val thresholdTextView = view.findViewById<TextView>(R.id.editThreshold)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnChooseThreshold = view.findViewById<ImageButton>(R.id.btnChooseThreshold)
        // Retrieve the error message from arguments
        val threshold = arguments?.getInt(COUNTER_THRESHOLD) ?: ""
        thresholdTextView.text = threshold.toString()
        // Set up the close button
        btnCancel.setOnClickListener {
            dismiss() // Close the dialog
        }
        btnSave.setOnClickListener {
            saveThresholdListener?.onSave(thresholdTextView.text?.toString()!!.toInt())
            dismiss()
        }
        btnChooseThreshold.setOnClickListener {
            chooseThresholdListener?.onChoose()
            dismiss()
        }

        return view
    }

    interface SaveThresholdDialogListener {
        fun onSave(counterThreshold: Int)
    }

    private var saveThresholdListener: SaveThresholdDialogListener? = null

    // Define the callback interface
    interface ChooseThresholdDialogListener {
        fun onChoose()
    }

    private var chooseThresholdListener: ChooseThresholdDialogListener? = null
}