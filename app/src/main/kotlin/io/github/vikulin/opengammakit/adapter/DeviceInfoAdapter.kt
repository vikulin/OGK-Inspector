package io.github.vikulin.opengammakit.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import io.github.vikulin.opengammakit.R

class DeviceInfoAdapter(
    private val context: Context,
    private val data: LinkedHashMap<String, String>
) : BaseAdapter() {

    // Convert TreeMap keys into a list for indexed access
    private val keys: List<String> = data.keys.toList()

    override fun getCount(): Int = data.size

    override fun getItem(position: Int): Pair<String, String?>? {
        val key = keys[position]
        return key to data[key] // Return the key-value pair
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_info_list, parent, false
        )

        val keyText = view.findViewById<TextView>(R.id.keyText)
        val valueText = view.findViewById<TextView>(R.id.valueText)

        // Bind key and value to TextViews
        val (key, value) = getItem(position) ?: return view
        keyText.text = key
        valueText.text = value

        return view
    }
}