package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.model.OpenGammaKitData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveSpectrumDataIntoFileDialogFragment : DialogFragment() {

    private lateinit var fileNameEditText: TextView
    private lateinit var selectedLocationText: TextView

    companion object {
        private const val SPECTRUM_DATA = "spectrum_data_to_save"

        fun newInstance(spectrumData: OpenGammaKitData): SaveSpectrumDataIntoFileDialogFragment {
            val fragment = SaveSpectrumDataIntoFileDialogFragment()
            val args = Bundle()
            args.putSerializable(SPECTRUM_DATA, spectrumData)
            fragment.arguments = args
            return fragment
        }
    }

    interface SaveSpectrumDataIntoFileListener {
        fun onSaveToFile(fileName: String, locationUri: String, spectrumData: OpenGammaKitData)
    }

    private var saveSpectrumListener: SaveSpectrumDataIntoFileListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        saveSpectrumListener = when {
            parentFragment is SaveSpectrumDataIntoFileListener -> parentFragment as SaveSpectrumDataIntoFileListener
            context is SaveSpectrumDataIntoFileListener -> context
            else -> throw IllegalStateException("Host must implement SaveSpectrumListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        saveSpectrumListener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_spectrum_file_save, container, false)

        fileNameEditText = view.findViewById<EditText>(R.id.fileNameEditText)
        val selectLocationButton = view.findViewById<ImageButton>(R.id.selectLocationButton)
        selectedLocationText = view.findViewById<TextView>(R.id.selectedLocationText)
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        val spectrumData = arguments?.getSerializable(SPECTRUM_DATA) as? OpenGammaKitData
            ?: return view
        val prefix = spectrumData.derivedSpectra.entries.first().value.name
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = formatter.format(Date())
        val fileName = prefix+"_$timestamp.json"
        fileNameEditText.text = fileName
        selectedLocationText.text = "Documents/OpenGammaKit/"
        selectLocationButton.setOnClickListener {
            val name = fileNameEditText.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(context, "Enter a file name first", Toast.LENGTH_SHORT).show()
            } else {
                createDocumentLauncher.launch(name)
            }
        }
        saveButton.setOnClickListener {
            val fileName = fileNameEditText.text.toString().trim()
            val uri = selectedLocationText.text

            when {
                fileName.isEmpty() -> {
                    Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
                }
                uri == null -> {
                    Toast.makeText(context, "Please select a save location", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    saveSpectrumListener?.onSaveToFile(fileName, uri.toString(), spectrumData)
                    dismiss()
                }
            }
        }

        return view
    }


    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                // Get URI path and remove file name + extension if present
                val rawPath = uri.path ?: uri.toString()
                val cleanedPath = rawPath
                    .substringBeforeLast('/')
                    .substringAfterLast(':')
                val fileName = rawPath.substringAfterLast('/')
                fileNameEditText.text = fileName
                selectedLocationText.text = cleanedPath

                val docFile = DocumentFile.fromSingleUri(requireContext(), uri)
                if (docFile?.delete() != true) {
                    Toast.makeText(requireContext(), "Delete not supported", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "File creation canceled", Toast.LENGTH_SHORT).show()
            }
        }
}
