package io.github.vikulin.opengammakit.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import io.github.vikulin.opengammakit.R
import io.github.vikulin.opengammakit.model.Isotope

class IsotopeSelectAdapter(context: Context, private val isotopes: List<Isotope>) :
    ArrayAdapter<Isotope>(context, R.layout.item_dropdown, isotopes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_dropdown, parent, false)
        val isotope = getItem(position)

        val textView = view.findViewById<TextView>(R.id.isotope) // Ensure reference exists in your layout
        textView.text = "${isotope?.name}: ${isotope?.energies?.joinToString(", ")}"

        return view
    }

    private var filteredIsotopes: List<Isotope> = isotopes // List for filtered results

    override fun getCount(): Int {
        return filteredIsotopes.size // Return the size of the filtered list
    }

    override fun getItem(position: Int): Isotope? {
        if(position > filteredIsotopes.size-1){
            return null
        }
        return filteredIsotopes[position] // Return the item from the filtered list
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase() ?: ""
                val results = FilterResults()

                filteredIsotopes = if (query.isEmpty()) {
                    isotopes // Show all items if the query is empty
                } else {
                    // Filter isotopes based on matching energies or names
                    isotopes.filter { isotope ->
                        isotope.name.lowercase().contains(query) ||
                                isotope.energies.any { energy -> energy.toString().contains(query) }
                    }
                }

                results.values = filteredIsotopes
                results.count = filteredIsotopes.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredIsotopes = results?.values as List<Isotope>? ?: isotopes
                notifyDataSetChanged() // Refresh the dropdown with filtered results
            }
        }
    }
}