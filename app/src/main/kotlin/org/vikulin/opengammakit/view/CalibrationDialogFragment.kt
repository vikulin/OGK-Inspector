package org.vikulin.opengammakit.view

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import org.json.JSONObject
import org.vikulin.opengammakit.adapter.IsotopeAdapter
import org.vikulin.opengammakit.R
import org.vikulin.opengammakit.model.Isotope
import java.io.InputStream
import java.util.Locale


class CalibrationDialogFragment(private val x: Float) : DialogFragment() {

    // Define the callback interface
    interface CalibrationDialogListener {
        fun onCalibrationCompleted(peakChannel: Double, peakEnergy: Double, isotope: Isotope?)
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
                listener?.onCalibrationCompleted(peakChannel, peakEnergy, chosenIsotope)
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
