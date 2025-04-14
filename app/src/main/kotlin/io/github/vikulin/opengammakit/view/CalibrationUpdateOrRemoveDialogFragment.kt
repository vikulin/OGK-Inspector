package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.github.mikephil.charting.components.LimitLine
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.model.EmissionSource

class CalibrationUpdateOrRemoveDialogFragment : DialogFragment() {

    private lateinit var calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>

    private var calibrationPointIndex = 0

    companion object {
        fun newInstance(calibrationPointIndex: Int, calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>): CalibrationUpdateOrRemoveDialogFragment {
            val fragment = CalibrationUpdateOrRemoveDialogFragment()
            val args = Bundle()

            // Serialize the calibrationPoint's components
            args.putFloat("LIMIT_LINE_VALUE", calibrationPoint.first.limit)
            args.putString("LIMIT_LINE_LABEL", calibrationPoint.first.label)
            args.putDouble("CHANNEL", calibrationPoint.second.first)
            args.putString("SOURCE_NAME", calibrationPoint.second.second.name)
            args.putDouble("SOURCE_ENERGY", calibrationPoint.second.second.energy)
            args.putInt("CALIBRATION_POINT_INDEX", calibrationPointIndex)

            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the calibrationPoint from the arguments
        val limitLineValue = arguments?.getFloat("LIMIT_LINE_VALUE") ?: 0f
        val limitLineLabel = arguments?.getString("LIMIT_LINE_LABEL") ?: ""
        val channel = arguments?.getDouble("CHANNEL") ?: 0.0
        val sourceName = arguments?.getString("SOURCE_NAME") ?: ""
        val sourceEnergy = arguments?.getDouble("SOURCE_ENERGY") ?: 0.0
        calibrationPointIndex = arguments?.getInt("CALIBRATION_POINT_INDEX") ?: 0

        // Reconstruct the calibrationPoint
        val limitLine = LimitLine(limitLineValue, limitLineLabel)
        val emissionSource = EmissionSource(sourceName, sourceEnergy)
        calibrationPoint = Pair(limitLine, Pair(channel, emissionSource))
    }

    // Define the callback interface
    interface CalibrationDialogListener {
        fun onCalibrationRemove(calibrationPointIndex: Int)
        fun onCalibrationUpdate(calibrationPointIndex: Int)
    }

    private var listener: CalibrationDialogListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Try to attach the listener to the host activity or parent fragment
        listener = when {
            parentFragment is CalibrationDialogListener -> parentFragment as CalibrationDialogListener
            context is CalibrationDialogListener -> context
            else -> throw IllegalStateException("Host must implement CalibrationDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        // Clear the reference to the listener to avoid memory leaks
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_calibration_update_or_remove, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val calibrationPoint = view.findViewById<TextView>(R.id.calibrationPoint)
        calibrationPoint.text = this@CalibrationUpdateOrRemoveDialogFragment.calibrationPoint.first.label
        val btnRemove = view.findViewById<Button>(R.id.btnRemove)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        btnRemove.setOnClickListener {
            // Call the callback to return the values
            listener?.onCalibrationRemove(this@CalibrationUpdateOrRemoveDialogFragment.calibrationPointIndex)
            dismiss()
        }
        btnUpdate.setOnClickListener {
            listener?.onCalibrationUpdate(this@CalibrationUpdateOrRemoveDialogFragment.calibrationPointIndex)
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
