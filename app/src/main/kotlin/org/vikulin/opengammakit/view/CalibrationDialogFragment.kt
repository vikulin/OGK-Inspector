package org.vikulin.opengammakit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import org.json.JSONObject
import org.vikulin.opengammakit.R
import java.io.InputStream
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
        val editPeak = view.findViewById<AutoCompleteTextView>(R.id.editPeak)
        val editChannel = view.findViewById<EditText>(R.id.editChannel)
        val btnCalibrate = view.findViewById<Button>(R.id.btnCalibrate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        editChannel.setText(String.format(Locale.US, "%.1f", x))

        val isotopesKeV = mutableListOf<String>()

        try {
            // Open the JSON file from assets
            val json = requireContext().assets.open("isotopes_keV.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val isotopes = jsonObject.getJSONArray("isotopes")

            // Extract isotope names and energies
            for (i in 0 until isotopes.length()) {
                val isotope = isotopes.getJSONObject(i)
                val name = isotope.getString("name")
                val energies = isotope.getJSONArray("energies")

                // Combine isotope name with its energies
                val energyList = mutableListOf<String>()
                for (j in 0 until energies.length()) {
                    energyList.add("${energies.getDouble(j)} keV")
                }
                isotopesKeV.add("$name: ${energyList.joinToString(", ")}")
            }

            // Create an ArrayAdapter and attach it to the AutoCompleteTextView
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, isotopesKeV)
            editPeak.setAdapter(adapter)

            // Corrected: Ensure correct type retrieval & use `joinToString` properly
            editPeak.setOnItemClickListener { parent, _, position, _ ->
                val isotope = isotopes.getJSONObject(position)
                val name = isotope.getString("name")
                val energy = isotope.getJSONArray("energies")[0].toString()
                editPeak.setText(energy) // Replace input with only energy values
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
