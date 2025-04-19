package io.github.vikulin.opengammakit.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.model.OpenGammaKitData
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

class SpectrumFileChooserDialogFragment : DialogFragment() {

    private var selectedFileUri: Uri? = null
    private lateinit var selectedFileText: TextView

    // Using ActivityResultLauncher for modern result handling
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedFileUri = result.data?.data
                selectedFileUri?.let { uri ->
                    val fileName = uri.lastPathSegment
                    selectedFileText.text = fileName ?: "No file selected"
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_spectrum_file_chooser, container, false)

        selectedFileText = view.findViewById(R.id.selectedFileText)
        val btnChooseFile: ImageButton = view.findViewById(R.id.btnChooseFile)
        val btnCancel: View = view.findViewById(R.id.btnCancel)
        val btnOpen: View = view.findViewById(R.id.btnOpen)

        btnChooseFile.setOnClickListener { openFileChooser() }
        btnCancel.setOnClickListener { dismiss() }
        btnOpen.setOnClickListener { handleFileOpen() }

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the selected file URI to the bundle
        selectedFileUri?.let { uri ->
            outState.putString("selectedFileUri", uri.toString())
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Restore the URI and update the TextView if available
        savedInstanceState?.getString("selectedFileUri")?.let { uriString ->
            selectedFileUri = uriString.toUri()
            selectedFileUri?.let { uri ->
                val fileName = uri.lastPathSegment
                selectedFileText.text = fileName ?: "No file selected"
            }
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json" // Only JSON files
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Choose a spectrum file"))
    }

    private fun handleFileOpen() {
        selectedFileUri?.let { uri ->
            val args = Bundle().apply {
                putString("file_spectrum_uri", uri.toString()) // Pass the Uri as a string
            }
            val fragment = SpectrumFragment().apply {
                arguments = args
            }
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment, fragment, "spectrum_view")
                .addToBackStack(null)
                .commit()
            dismiss()
        } ?: run {
            selectedFileText.error = "Please select a file first."
        }
    }
}