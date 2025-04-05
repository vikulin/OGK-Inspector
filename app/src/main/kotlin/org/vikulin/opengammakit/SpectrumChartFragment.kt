package org.vikulin.opengammakit

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.serialization.json.Json
import org.vikulin.opengammakit.model.GammaKitData

class SpectrumChartFragment : Fragment() {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceValue: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimeValue: TextView

    private val testJson: String by lazy {
        requireContext().assets.open("test_spectrum.json").bufferedReader().use { it.readText() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_spectrum_chart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        spectrumChart = view.findViewById(R.id.spectrumChart)
        deviceValue = view.findViewById(R.id.deviceValue)
        pulseCountValue = view.findViewById(R.id.pulseCountValue)
        measureTimeValue = view.findViewById(R.id.measureTimeValue)

        setupChart()

        // Update the table with values
        updateTableWithValues()
    }

    private fun updateTableWithValues() {
        val parsed = Json.decodeFromString<GammaKitData>(testJson)

        // Extract values from parsed data
        val deviceName = parsed.data[0].deviceData.deviceName ?: "Unknown Device"
        val validPulseCount = parsed.data[0].resultData.energySpectrum.validPulseCount
        val measurementTime = parsed.data[0].resultData.energySpectrum.measurementTime

        // Update TextViews with the extracted values
        deviceValue.text = deviceName
        pulseCountValue.text = validPulseCount.toString()
        measureTimeValue.text = formatTime(measurementTime)
    }

    private fun formatTime(seconds: Long): String {
        val minutes = (seconds / 60).toInt()
        val remainingSeconds = (seconds % 60).toInt()

        return when {
            minutes > 0 -> String.format("%dmin %02dsec", minutes, remainingSeconds)
            else -> String.format("%dsec", remainingSeconds)
        }
    }

    private fun setupChart() {
        val parsed = Json.decodeFromString<GammaKitData>(testJson)
        val spectrum = parsed.data.firstOrNull()?.resultData?.energySpectrum ?: return

        val entries = spectrum.spectrum.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        val dataSet = LineDataSet(entries, "Energy Spectrum").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER  // interpolation for smooth curve
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            color = resources.getColor(android.R.color.holo_blue_light, null)
        }

        // Dynamically set colors based on the current theme
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val primaryColor = if (isNightMode) {
            resources.getColor(R.color.colorPrimaryNight, null)
        } else {
            resources.getColor(R.color.colorPrimaryDay, null)
        }

        spectrumChart.apply {
            data = LineData(dataSet)
            xAxis.labelRotationAngle = 0f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = primaryColor
            axisRight.isEnabled = true
            axisLeft.textColor = primaryColor
            description = Description().apply {
                text = "Channel vs Counts"
                textColor = primaryColor
            }
            legend.textColor = primaryColor
            invalidate()  // refresh chart
        }

        showResolutionRate(spectrum.spectrum)
    }

    private fun showResolutionRate(counts: List<Int>) {
        // Very basic resolution estimation
        val max = counts.maxOrNull() ?: return
        val maxIndex = counts.indexOf(max)

        // Find half max value indices (FWHM approximation)
        val halfMax = max / 2.0
        var left = maxIndex
        while (left > 0 && counts[left] > halfMax) left--
        var right = maxIndex
        while (right < counts.size && counts[right] > halfMax) right++

        val fwhm = right - left
        val resolutionPercent = (fwhm.toDouble() / maxIndex) * 100

        spectrumChart.centerViewToAnimated(
            maxIndex.toFloat(),
            max.toFloat(),
            com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT,
            500
        )

        spectrumChart.description.text += "\nResolution: %.2f%%".format(resolutionPercent)
    }
}
