package io.github.vikulin.opengammakit.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.model.GammaKitEntry
import io.github.vikulin.opengammakit.view.FwhmSpectrumSelectionDialogFragment

class SpectrumFwhmSelectAdapter(
    private val dialog: FwhmSpectrumSelectionDialogFragment,
    private val spectrumData: List<GammaKitEntry>,
    private val onItemSelected: FwhmSpectrumSelectionDialogFragment.ChooseSpectrumDialogListener?
) : RecyclerView.Adapter<SpectrumFwhmSelectAdapter.SpectrumViewHolder>() {

    inner class SpectrumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorIndicator: MaterialCardView = itemView.findViewById(R.id.colorIndicator)
        val deviceName: TextView = itemView.findViewById(R.id.spectrumName)

        fun bind(entry: GammaKitEntry, index: Int) {
            colorIndicator.setBackgroundTintList(ColorStateList.valueOf(SpectrumFragment.getLineColor(dialog.requireContext(), index)))

            // Get device name or default to "Spectrum #index"
            val name = entry.deviceData.deviceName.takeIf { it.isNotEmpty() } ?: "Spectrum #$index"
            deviceName.text = name

            itemView.setOnClickListener {
                dialog.dismiss()
                onItemSelected?.onChoose(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpectrumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fwhm_spectrum, parent, false)
        return SpectrumViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpectrumViewHolder, position: Int) {
        holder.bind(spectrumData[position], position)
    }

    override fun getItemCount(): Int = spectrumData.size
}