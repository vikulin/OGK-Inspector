package org.vikulin.opengammakit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import org.vikulin.opengammakit.R
import java.util.Locale

class CalibrationDialogFragment(private val x: Float) : DialogFragment() {

    // Define the callback interface
    interface CalibrationDialogListener {
        fun onCalibrationCompleted(peakChannel: Double, peakEnergy: Double)
    }

    private var listener: CalibrationDialogListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_calibration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val editPeak = view.findViewById<EditText>(R.id.editPeak)
        val editChannel = view.findViewById<EditText>(R.id.editChannel)
        val btnCalibrate = view.findViewById<Button>(R.id.btnCalibrate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        editChannel.setText(String.format(Locale.US, "%.1f", x))

        btnCalibrate.setOnClickListener {
            val peakChannel = editChannel.text.toString().toDoubleOrNull()
            val peakEnergy = editPeak.text.toString().toDoubleOrNull()
            if (peakChannel != null && peakEnergy != null) {
                // Call the callback to return the values
                listener?.onCalibrationCompleted(peakChannel, peakEnergy)
                dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Optional: set width to match_parent
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Allow the parent fragment to set the listener
    fun setCalibrationDialogListener(listener: CalibrationDialogListener) {
        this.listener = listener
    }
}
