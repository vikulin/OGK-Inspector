package org.vikulin.opengammakit

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import kotlinx.serialization.json.Json
import org.vikulin.opengammakit.model.GammaKitData
import org.vikulin.opengammakit.model.OpenGammaKitCommands
import org.vikulin.opengammakit.view.ResolutionMarkerView
import kotlin.math.abs

class SpectrumChartFragment : SerialConnectionFragment() {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceValue: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimer: Chronometer
    private var spectrumDataSet: LineDataSet? = null
    private var pauseOffset: Long = 0
    private var verticalLimitLine: LimitLine? = null
    private var horizontalLimitLine: LimitLine? = null

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

        val marker = ResolutionMarkerView(requireContext(), R.layout.marker_resolution)
        spectrumChart.marker = marker
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

        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)

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

        setupChartTouchListener()
    }

    private fun setupChartTouchListener() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Translate touch point and handle pick selection
                handleChartTap(e.x)
                return true
            }
        })

        spectrumChart.setOnTouchListener { v, event ->
            // Let gestureDetector process it
            gestureDetector.onTouchEvent(event)

            // Important: allow LineChart to handle other gestures (zoom, drag)
            spectrumChart.onTouchEvent(event)
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

    private fun handleChartTap(tapX: Float) {
        val tapY = spectrumChart.height / 2f
        val transformer = spectrumChart.getTransformer(YAxis.AxisDependency.LEFT)
        val touchPoint = transformer.getValuesByTouchPoint(tapX, tapY)

        val tappedX = touchPoint.x
        val closestEntry = getClosestEntryToX(tappedX)
        if (closestEntry != null) {
            val pickX = closestEntry.x
            val pickY = closestEntry.y

            drawVerticalLine(pickX)

            val halfHeight = pickY / 2f
            drawHorizontalLine(halfHeight)

            val (leftX, rightX) = findCrossingPoints(halfHeight, pickX)
            val resolutionRate = calculateResolutionRate(leftX, rightX, pickX)
            val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
            // Show marker at the cross point (pickX, halfHeight)
            val marker = spectrumChart.marker as? ResolutionMarkerView
            marker?.apply {
                setResolution(resolutionRate, primaryColor)
                refreshContent(Entry(pickX, halfHeight), Highlight(pickX, halfHeight, 0))
            }

            spectrumChart.highlightValue(Highlight(pickX, halfHeight, 0)) // triggers marker display

        }
    }

    private fun getClosestEntryToX(xVal: Double): Entry? {
        val dataSet = spectrumChart.data.getDataSetByIndex(0)
        var closestEntry: Entry? = null
        var minDiff = Double.MAX_VALUE

        for (i in 0 until dataSet.entryCount) {
            val entry = dataSet.getEntryForIndex(i)
            val diff = abs(entry.x - xVal)
            if (diff < minDiff) {
                closestEntry = entry
                minDiff = diff
            }
        }
        return closestEntry
    }

    private fun drawVerticalLine(x: Float) {
        val xAxis = spectrumChart.xAxis

        verticalLimitLine?.let { xAxis.removeLimitLine(it) }
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        verticalLimitLine = LimitLine(x, "Peak")
        verticalLimitLine?.apply {
            lineColor = Color.RED          // fixed line color
            textColor = primaryColor     // dynamic label color
            lineWidth = 2f
            enableDashedLine(3f, 3f, 0f)
        }

        xAxis.addLimitLine(verticalLimitLine)
        spectrumChart.invalidate()
    }

    private fun drawHorizontalLine(y: Float) {
        val yAxis = spectrumChart.axisLeft
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        horizontalLimitLine?.let { yAxis.removeLimitLine(it) }

        horizontalLimitLine = LimitLine(y, "FWHM / 2")
        horizontalLimitLine?.apply {
            lineColor = Color.GREEN        // fixed line color
            textColor = primaryColor     // dynamic label color
            lineWidth = 2f
            enableDashedLine(3f, 3f, 0f)
        }

        yAxis.addLimitLine(horizontalLimitLine)
        spectrumChart.invalidate()
    }
    private fun findCrossingPoints(halfHeight: Float, pickX: Float): Pair<Float, Float> {
        val dataSet = spectrumChart.data.getDataSetByIndex(0)
        var leftX = -1f
        var rightX = -1f

        // Scan left of the peak
        for (i in dataSet.entryCount - 1 downTo 0) {
            val entry = dataSet.getEntryForIndex(i)
            if (entry.x < pickX && entry.y <= halfHeight) {
                leftX = entry.x
                break
            }
        }

        // Scan right of the peak
        for (i in 0 until dataSet.entryCount) {
            val entry = dataSet.getEntryForIndex(i)
            if (entry.x > pickX && entry.y <= halfHeight) {
                rightX = entry.x
                break
            }
        }

        return Pair(leftX, rightX)
    }

    private fun calculateResolutionRate(leftX: Float, rightX: Float, pickX: Float): Float {
        val resolutionWidth = rightX - leftX
        return (resolutionWidth / pickX) * 100
    }
}