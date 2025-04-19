package io.github.vikulin.opengammakit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.model.Isotope

class IsotopeAdapter(private var isotopes: List<Isotope>) :
    RecyclerView.Adapter<IsotopeAdapter.IsotopeViewHolder>() {

    class IsotopeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val energiesText: TextView = view.findViewById(R.id.energiesText)
        val halfLifeText: TextView = view.findViewById(R.id.halfLifeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IsotopeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.isotope_item, parent, false)
        return IsotopeViewHolder(view)
    }

    override fun onBindViewHolder(holder: IsotopeViewHolder, position: Int) {
        val isotope = isotopes[position]
        holder.nameText.text = isotope.name
        holder.energiesText.text = "${isotope.energies.joinToString(", ")}"
        holder.halfLifeText.text = "${formatHalfLife(isotope.hl)}"
    }

    override fun getItemCount() = isotopes.size

    private fun formatHalfLife(seconds: Double): String {
        val minute = 60.0
        val hour = 60 * minute
        val day = 24 * hour
        val month = 30.44 * day
        val year = 365.25 * day

        fun formatValue(value: Double, unit: String): String {
            return if (value > 999) {
                "%.2e %s".format(value, unit)
            } else {
                "%.2f %s".format(value, unit)
            }
        }

        return when {
            seconds >= year -> formatValue(seconds / year, "Years")
            seconds >= month -> formatValue(seconds / month, "Months")
            seconds >= day -> formatValue(seconds / day, "Days")
            seconds >= hour -> formatValue(seconds / hour, "Hours")
            seconds >= minute -> formatValue(seconds / minute, "Minutes")
            else -> formatValue(seconds, "Seconds")
        }
    }

    fun updateList(newList: List<Isotope>) {
        isotopes = newList
        notifyDataSetChanged()
    }
}
