package io.github.vikulin.opengammakit

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("AppPreferences", 0)

        // Spinner setup
        val spinner: Spinner = view.findViewById(R.id.spinnerOpenWhenBoot)
        val options = listOf("None", "Counter", "Spectrum")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Restore saved value
        val savedOption = sharedPreferences.getString("open_when_boot", "None")
        val selectedIndex = options.indexOf(savedOption)
        if (selectedIndex != -1) {
            spinner.setSelection(selectedIndex)
        }

        // Save value on selection
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedOption = options[position]
                sharedPreferences.edit().putString("open_when_boot", selectedOption).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        return view
    }
}