package org.vikulin.opengammakit

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.serialization.json.Json
import org.vikulin.opengammakit.model.GammaKitData
import org.vikulin.opengammakit.model.OpenGammaKitCommands

class SpectrumChartFragment : SerialConnectionFragment() {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceValue: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimer: Chronometer
    private var spectrumDataSet: LineDataSet? = null
    private var pauseOffset: Long = 0

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
        measureTimer = view.findViewById(R.id.measureTimer)

        setupChart()
    }

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        super.setDtr(true)
        val command = OpenGammaKitCommands().setOut("spectrum" + '\n').toByteArray()
        super.send(command)
    }

    override fun onConnectionFailed() {
        super.onConnectionFailed()
        // Add a message here
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

    private fun updateChartSpectrumData(parsed: List<Long>) {

        val entries = parsed.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        spectrumDataSet?.let {
            it.values = entries
            spectrumChart.data.notifyDataChanged()
            spectrumChart.notifyDataSetChanged()
            spectrumChart.invalidate()
        }
    }

    private fun updateCounts(counts: Long){
        pulseCountValue.text = counts.toString()
    }

    private fun showResolutionRate(counts: List<Long>) {
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

    private val buffer = StringBuilder()
    private var openBraces = 0

    override fun receive(bytes: ByteArray) {
        //Log.d("Test","received ${bytes.size}")
        if (bytes.isNotEmpty()) {
            val inputString = bytes.toString(Charsets.UTF_8)
            //Log.d("Test","received ${bytes.toString(Charsets.UTF_8)}")
            val EOF = '\uFFFF'
            for (char in inputString) {
                when (char) {
                    '[' -> {
                        openBraces++
                        buffer.append(char)
                    }
                    ']' -> {
                        openBraces--
                        buffer.append(char)
                    }
                    EOF -> {}
                    '\r' -> {}
                    '\n' -> {}
                    else -> {
                        buffer.append(char)
                    }
                }

                if (openBraces == 0 && buffer.isNotEmpty()) {
                    try {
                        val json = Json {
                            allowTrailingComma = true // Enables trailing commas in JSON parsing
                        }
                        val parsed = json.decodeFromString<List<Long>>(buffer.toString())
                        val counts = parsed.fold(0L) { acc, num -> acc + num }
                        Log.d("Test", "List size: ${parsed.size}, counts: $counts")
                        buffer.clear()
                        updateChartSpectrumData(parsed)
                        updateCounts(counts)
                    } catch (e: Exception) {
                        Log.e("Test", "Failed to parse JSON: ${e.message}")
                        Log.d("Test",buffer.toString())
                        buffer.clear() // Discard malformed or incomplete JSON
                    }
                }
                if(openBraces>1 || openBraces<0){
                    Log.e("Test", "openBraces issue: $buffer")
                    buffer.clear() // Discard malformed or incomplete JSON
                    openBraces = 0
                }
            }
        }
    }

    override fun status(str: String) {
        //TODO implement
    }

    private fun startTimer() {
        measureTimer.base = SystemClock.elapsedRealtime() - pauseOffset
        measureTimer.start()
    }

    private fun pauseTimer() {
        measureTimer.stop()
        pauseOffset = SystemClock.elapsedRealtime() - measureTimer.base

    }

    override fun onPause() {
        super.onPause()
        pauseTimer() // Automatically pause the timer when the fragment is paused
    }

    override fun onResume() {
        super.onResume()
        startTimer() // Automatically resume the timer when the fragment is visible again
    }

    override fun onDestroy() {
        super.onDestroy()
        val args = getBundle().apply {
            putString("command", "set out off\n")
        }
        val fragment: Fragment = SerialCommandFragment()
        fragment.arguments = args
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment, fragment, "command")
            .addToBackStack(null)
            .commit()
    }
}