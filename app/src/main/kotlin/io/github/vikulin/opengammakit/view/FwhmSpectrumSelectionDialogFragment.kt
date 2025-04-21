package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.adapter.SpectrumAdapter
import io.github.vikulin.opengammakit.model.OpenGammaKitData

class FwhmSpectrumSelectionDialogFragment: DialogFragment() {

    companion object {
        private const val SPECTRUM_DATA = "spectrum_data"

        fun newInstance(spectrumData: OpenGammaKitData): FwhmSpectrumSelectionDialogFragment {
            val fragment = FwhmSpectrumSelectionDialogFragment()
            val args = Bundle()
            args.putSerializable(SPECTRUM_DATA, spectrumData)
            fragment.arguments = args
            return fragment
        }
    }

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
        val view = inflater.inflate(R.layout.dialog_spectrum_selection, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.spectrumRecyclerView)

        val spectrumData: OpenGammaKitData = arguments?.getSerializable(SPECTRUM_DATA) as OpenGammaKitData

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = SpectrumAdapter(this, spectrumData.data, chooseSpectrumListener)

        return view
    }

    // Define the callback interface
    interface ChooseSpectrumDialogListener {
        fun onChoose(spectrumIndex: Int)
    }

    private var chooseSpectrumListener: ChooseSpectrumDialogListener? = null
}