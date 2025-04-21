package io.github.vikulin.opengammakit.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.model.GammaKitEntry

class SpectrumSaveAdapter(
    private val context: Context,
    private val spectrumData: List<GammaKitEntry>
) : RecyclerView.Adapter<SpectrumSaveAdapter.SpectrumViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    inner class SpectrumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorIndicator: MaterialCardView = itemView.findViewById(R.id.colorIndicator)
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val checkBox: CheckBox = itemView.findViewById(R.id.spectrumCheckBox)

        fun bind(entry: GammaKitEntry, index: Int) {
            colorIndicator.setBackgroundTintList(
                ColorStateList.valueOf(SpectrumFragment.getLineColor(context, index))
            )

            val name = entry.deviceData.deviceName.takeIf { it.isNotEmpty() } ?: "Spectrum #$index"
            deviceName.text = name
            checkBox.isChecked = selectedPositions.contains(index)

            itemView.setOnClickListener {
                toggleSelection(index)
                notifyItemChanged(index)
            }

            checkBox.setOnClickListener {
                toggleSelection(index)
            }
        }

        private fun toggleSelection(index: Int) {
            if (selectedPositions.contains(index)) {
                selectedPositions.remove(index)
            } else {
                selectedPositions.add(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpectrumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_save_spectrum, parent, false)
        return SpectrumViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpectrumViewHolder, position: Int) {
        holder.bind(spectrumData[position], position)
    }

    override fun getItemCount(): Int = spectrumData.size

    fun getSelectedItems(): List<GammaKitEntry> {
        return selectedPositions.map { spectrumData[it] }
    }

    fun getSelectedIndexes(): List<Int> {
        return selectedPositions.toList()
    }
}