package org.vikulin.opengammakit

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.serialization.json.Json
import org.vikulin.opengammakit.model.GammaKitData

class SpectrumChartFragment : SerialConnectionFragment() {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceValue: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimeValue: TextView
    private var spectrumDataSet: LineDataSet? = null

    private val zeroedData: String by lazy {
        requireContext().assets.open("spectrum_zeroed.json").bufferedReader().use { it.readText() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_spectrum_chart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spectrumChart = view.findViewById(R.id.spectrumChart)
        deviceValue = view.findViewById(R.id.deviceValue)
        pulseCountValue = view.findViewById(R.id.pulseCountValue)
        measureTimeValue = view.findViewById(R.id.measureTimeValue)

        setupChart()

        val command = OpenGammaKitCommands().setOut("spectrum"+"\n").toByteArray()
        super.send(command)

    }

    private fun updateTableWithValues(parsed: GammaKitData) {
        val data = parsed.data.firstOrNull() ?: return

        val deviceName = data.deviceData.deviceName ?: "Unknown Device"
        val validPulseCount = data.resultData.energySpectrum.validPulseCount
        val measurementTime = data.resultData.energySpectrum.measurementTime

        deviceValue.text = deviceName
        pulseCountValue.text = validPulseCount.toString()
        measureTimeValue.text = formatTime(measurementTime)
    }

    private fun formatTime(seconds: Long): String {
        val minutes = (seconds / 60).toInt()
        val remainingSeconds = (seconds % 60).toInt()
        return if (minutes > 0) "%dmin %02dsec".format(minutes, remainingSeconds)
        else "%dsec".format(remainingSeconds)
    }

    private fun setupChart() {
        val parsed = Json.decodeFromString<GammaKitData>(zeroedData)
        val spectrum = parsed.data.firstOrNull()?.resultData?.energySpectrum ?: return

        val entries = spectrum.spectrum.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        spectrumDataSet = LineDataSet(entries, "Energy Spectrum").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            color = resources.getColor(android.R.color.holo_blue_light, null)
        }

        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val primaryColor = if (isNightMode) {
            resources.getColor(R.color.colorPrimaryNight, null)
        } else {
            resources.getColor(R.color.colorPrimaryDay, null)
        }

        spectrumChart.apply {
            data = LineData(spectrumDataSet)
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
            invalidate()
        }

        showResolutionRate(spectrum.spectrum)
    }

    private fun updateChartData(parsed: GammaKitData) {
        val spectrum = parsed.data.firstOrNull()?.resultData?.energySpectrum ?: return
        val counts = spectrum.spectrum

        val entries = counts.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        spectrumDataSet?.let {
            it.values = entries
            spectrumChart.data.notifyDataChanged()
            spectrumChart.notifyDataSetChanged()
            spectrumChart.invalidate()
        }
    }

    private fun showResolutionRate(counts: List<Int>) {
        val max = counts.maxOrNull() ?: return
        val maxIndex = counts.indexOf(max)
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

    override fun read() {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        super.read()
    }

    override fun receive(bytes: ByteArray) {
        Log.d("Test","received ${bytes.size} bytes")
        if (bytes.isNotEmpty()) {
            val spectrum = String(bytes)
            //val parsed = Json.decodeFromString<GammaKitData>(spectrum)
            //updateTableWithValues(parsed)
            //updateChartData(parsed)
        }
    }

    override fun status(str: String) {
        //TODO implement
    }
}