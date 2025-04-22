package io.github.vikulin.opengammakit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.vikulin.opengammakit.adapter.IsotopeAdapter
import io.github.vikulin.opengammakit.model.Isotope
import org.json.JSONObject

class IsotopeListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: IsotopeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_isotope_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.isotopeRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Load JSON from assets or file
        val json = loadJSONFromAsset("isotopes.json")
        var isotopes = parseIsotopes(json)
        adapter = IsotopeAdapter(isotopes)
        recyclerView.adapter = adapter

        view.findViewById<TextView>(R.id.sortByName).setOnClickListener {
            isotopes = isotopes.sortedBy { it.name }
            adapter.updateList(isotopes)
        }
        view.findViewById<TextView>(R.id.sortByEnergy).setOnClickListener {
            isotopes = isotopes.sortedBy { it.energies.firstOrNull() ?: 0.0 }
            adapter.updateList(isotopes)
        }
        view.findViewById<TextView>(R.id.sortByHalfLife).setOnClickListener {
            isotopes = isotopes.sortedBy { it.hl }
            adapter.updateList(isotopes)
        }
    }

    private fun loadJSONFromAsset(filename: String): String {
        return requireContext().assets.open(filename).bufferedReader().use { it.readText() }
    }

    private fun parseIsotopes(json: String): List<Isotope> {
        val jsonObject = JSONObject(json)
        val isotopeArray = jsonObject.getJSONArray("isotopes")
        val list = mutableListOf<Isotope>()

        for (i in 0 until isotopeArray.length()) {
            val obj = isotopeArray.getJSONObject(i)
            val name = obj.getString("name")
            val energies = obj.getJSONArray("energies").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }
            val halfLife = obj.getDouble("hl")
            list.add(Isotope(name, energies, halfLife))
        }

        return list
    }
}