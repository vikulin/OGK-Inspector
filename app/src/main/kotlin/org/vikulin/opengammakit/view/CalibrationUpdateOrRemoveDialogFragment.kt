package org.vikulin.opengammakit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.github.mikephil.charting.components.LimitLine
import org.vikulin.opengammakit.R
import org.vikulin.opengammakit.model.EmissionSource

class CalibrationUpdateOrRemoveDialogFragment(private val calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>) : DialogFragment() {

    // Define the callback interface
    interface CalibrationDialogListener {
        fun onCalibrationRemove(calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>)
        fun onCalibrationUpdate(calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>)
    }

    private var listener: CalibrationDialogListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_calibration_update_or_remove, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnRemove = view.findViewById<Button>(R.id.btnRemove)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        btnRemove.setOnClickListener {
            // Call the callback to return the values
            listener?.onCalibrationRemove(calibrationPoint)
            dismiss()
        }
        btnUpdate.setOnClickListener {
            listener?.onCalibrationUpdate(calibrationPoint)
            dismiss()
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
