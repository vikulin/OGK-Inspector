package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.adapter.SpectrumSaveAdapter
import io.github.vikulin.opengammakit.model.OpenGammaKitData

class SaveSelectedSpectrumDialogFragment : DialogFragment() {

    companion object {
        private const val SPECTRUM_DATA = "spectrum_data_to_save"

        fun newInstance(spectrumData: OpenGammaKitData): SaveSelectedSpectrumDialogFragment {
            val fragment = SaveSelectedSpectrumDialogFragment()
            val args = Bundle()
            args.putSerializable(SPECTRUM_DATA, spectrumData)
            fragment.arguments = args
            return fragment
        }
    }

    private var chooseSpectrumListener: ChooseSpectrumDialogListener? = null
    private lateinit var adapter: SpectrumSaveAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        chooseSpectrumListener = when {
            parentFragment is ChooseSpectrumDialogListener -> parentFragment as ChooseSpectrumDialogListener
            context is ChooseSpectrumDialogListener -> context
            else -> throw IllegalStateException("Host must implement ChooseSpectrumDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        chooseSpectrumListener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_spectrum_save, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.spectrumRecyclerView)
        val saveButton: Button = view.findViewById(R.id.saveButton)

        val spectrumData = arguments?.getSerializable(SPECTRUM_DATA) as? OpenGammaKitData
            ?: return view

        adapter = SpectrumSaveAdapter(this.requireContext(), spectrumData)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        saveButton.setOnClickListener {
            val selected = adapter.getSelectedIndexes()
            if (selected.isEmpty()) {
                Toast.makeText(context, "Please select at least one spectrum", Toast.LENGTH_SHORT).show()
            } else {
                val selectedIndexes = adapter.getSelectedIndexes()
                chooseSpectrumListener?.onChooseMultiple(selectedIndexes)
                dismiss()
            }
        }

        return view
    }

    // Updated callback to support multiple selection
    interface ChooseSpectrumDialogListener {
        fun onChooseMultiple(selectedIndexes: MutableMap<Int, String>)
    }
}
