package org.vikulin.opengammakit.view

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.utils.MPPointF
import org.vikulin.opengammakit.R

class CalibrationMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val resolutionText: TextView = findViewById(R.id.valueTextView)

    fun setEnergy(value: Float, textColor: Int) {
        resolutionText.text = "%.2f%%keV".format(value)
        resolutionText.setTextColor(textColor)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(0f+20, -height.toFloat()-20)
    }
}