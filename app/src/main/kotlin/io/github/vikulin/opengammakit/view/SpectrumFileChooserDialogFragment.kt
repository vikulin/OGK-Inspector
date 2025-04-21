package io.github.vikulin.opengammakit.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R
import androidx.core.net.toUri

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

    override fun onAttach(context: Context) {
        super.onAttach(context)

        chooseFileListener = when {
            parentFragment is ChooseFileDialogListener -> parentFragment as ChooseFileDialogListener
            context is ChooseFileDialogListener -> context
            else -> throw IllegalStateException("Host must implement ChooseFileDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()

        chooseFileListener = null
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
            chooseFileListener?.onChoose(uri.toString())
            dismiss()
        } ?: run {
            selectedFileText.error = "Please select a file first."
        }
    }

    // Define the callback interface
    interface ChooseFileDialogListener {
        fun onChoose(uri: String)
    }

    private var chooseFileListener: ChooseFileDialogListener? = null
}