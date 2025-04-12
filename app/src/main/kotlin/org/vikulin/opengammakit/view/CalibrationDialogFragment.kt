package org.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.github.mikephil.charting.components.LimitLine
import org.json.JSONObject
import org.vikulin.opengammakit.adapter.IsotopeAdapter
import org.vikulin.opengammakit.R
import org.vikulin.opengammakit.model.EmissionSource
import org.vikulin.opengammakit.model.Isotope
import java.util.Locale

class CalibrationDialogFragment : DialogFragment() {

    private lateinit var calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>

    private var calibrationPointIndex = 0

    companion object {
        fun newInstance(calibrationPointIndex: Int, calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>): CalibrationDialogFragment {
            val fragment = CalibrationDialogFragment()
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
        fun onCalibrationCompleted(calibrationPointIndex: Int, peakChannel: Double, peakEnergy: Double, isotope: Isotope?)
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
        val editPeak = view.findViewById<AutoCompleteTextView>(R.id.editPeak)
        val editChannel = view.findViewById<EditText>(R.id.editChannel)
        val btnCalibrate = view.findViewById<Button>(R.id.btnCalibrate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        editChannel.setText(String.format(Locale.US, "%.1f", calibrationPoint.second.first))

        val isotopesList = mutableListOf<Isotope>()
        var chosenIsotope: Isotope? = null

        try {
            // Open the JSON file from assets
            val json = requireContext().assets.open("isotopes_keV.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val isotopesArray = jsonObject.getJSONArray("isotopes")

            // Extract isotope names and energies
            for (i in 0 until isotopesArray.length()) {
                val isotope = isotopesArray.getJSONObject(i)
                val name = isotope.getString("name")
                val energies = mutableListOf<Double>()

                val energiesArray = isotope.getJSONArray("energies")
                for (j in 0 until energiesArray.length()) {
                    energies.add(energiesArray.getDouble(j))
                }
                isotopesList.add(Isotope(name, energies))
            }

            val adapter = IsotopeAdapter(requireContext(), isotopesList)
            editPeak.setAdapter(adapter)

            var enteredValue = ""

            // Add a TextWatcher to monitor changes in the text field
            editPeak.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // No action needed before text change
                    enteredValue = s?.toString().toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Update the enteredValue whenever the text changes

                }

                override fun afterTextChanged(s: Editable?) {
                    // No action needed after text change
                }
            })

            editPeak.setOnItemClickListener { parent, _, position, _ ->
                val selectedIsotope = parent.getItemAtPosition(position) as Isotope

                // Check if the chosen value is a number
                // Check if the chosen value is a number (integer or double)
                val parsedNumber = enteredValue.toDoubleOrNull()

                if (parsedNumber != null) {
                    // Find the first matching energy
                    val matchingEnergy = selectedIsotope.energies.find { energy ->
                        "%.1f".format(energy).contains("%.0f".format(parsedNumber))
                    }

                    // Assign matching value to editPeak if found
                    if (matchingEnergy != null) {
                        editPeak.setText("%.1f".format(matchingEnergy))
                        chosenIsotope = selectedIsotope
                    } else {
                        editPeak.error = "No matching energy found"
                    }
                } else {
                    val selectedIsotope = parent.getItemAtPosition(position) as Isotope
                    editPeak.setText(selectedIsotope.energies[0].toString())
                    chosenIsotope = selectedIsotope
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        btnCalibrate.setOnClickListener {
            val peakChannel = editChannel.text.toString().toDoubleOrNull()
            val peakEnergy = editPeak.text.toString().toDoubleOrNull()
            if (peakChannel != null && peakEnergy != null) {
                // Call the callback to return the values
                listener?.onCalibrationCompleted(calibrationPointIndex, peakChannel, peakEnergy, chosenIsotope)
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
}
