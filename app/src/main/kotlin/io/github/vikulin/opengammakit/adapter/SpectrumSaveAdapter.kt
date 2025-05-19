package io.github.vikulin.opengammakit.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.SpectrumFragment
import io.github.vikulin.opengammakit.model.OpenGammaKitData
import kotlin.collections.mutableMapOf

class SpectrumSaveAdapter(
    private val context: Context,
    private val spectrumData: OpenGammaKitData
) : RecyclerView.Adapter<SpectrumSaveAdapter.SpectrumViewHolder>() {

    private val selectedPositions = mutableMapOf<Int, String>()

    inner class SpectrumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorIndicator: MaterialCardView = itemView.findViewById(R.id.colorIndicator)
        private val spectrumName: EditText = itemView.findViewById(R.id.spectrumName)
        private val checkBox: CheckBox = itemView.findViewById(R.id.spectrumCheckBox)

        fun bind(entry: OpenGammaKitData, index: Int) {
            colorIndicator.setBackgroundTintList(
                ColorStateList.valueOf(SpectrumFragment.getLineColor(context, index))
            )

            val name = entry.derivedSpectra[index]?.name ?: "Noname"
            spectrumName.setText(name)
            checkBox.isChecked = selectedPositions.contains(index)

            itemView.setOnClickListener {
                toggleSelection(index, spectrumName.text.toString())
                notifyItemChanged(index)
            }

            checkBox.setOnClickListener {
                toggleSelection(index, spectrumName.text.toString())
            }
        }

        private fun toggleSelection(index: Int, name: String) {
            if (selectedPositions.contains(index)) {
                selectedPositions.remove(index)
            } else {
                selectedPositions[index] = name
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpectrumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_save_spectrum, parent, false)
        return SpectrumViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpectrumViewHolder, position: Int) {
        holder.bind(spectrumData, position)
    }

    override fun getItemCount(): Int = spectrumData.data.size

    fun getSelectedIndexes(): MutableMap<Int, String> {
        return selectedPositions
    }
}