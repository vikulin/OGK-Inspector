package io.github.vikulin.opengammakit

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageButton
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
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import io.github.vikulin.opengammakit.view.ResolutionMarkerView
import io.github.vikulin.opengammakit.view.CalibrationDialogFragment
import kotlin.math.abs
import androidx.core.graphics.createBitmap
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import io.github.vikulin.opengammakit.model.CalibrationData
import io.github.vikulin.opengammakit.model.EmissionSource
import io.github.vikulin.opengammakit.model.Isotope
import java.io.OutputStream
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.vikulin.opengammakit.model.OpenGammaKitData
import io.github.vikulin.opengammakit.view.CalibrationUpdateOrRemoveDialogFragment
import io.github.vikulin.opengammakit.view.ClockProgressView
import io.github.vikulin.opengammakit.view.ErrorDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.iterator

class SpectrumFragment : SerialConnectionFragment(),
    CalibrationUpdateOrRemoveDialogFragment.CalibrationDialogListener,
    CalibrationDialogFragment.CalibrationDialogListener {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceName: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimer: Chronometer
    private lateinit var btnCalibration: ImageButton
    private lateinit var btnFwhm: ImageButton
    private lateinit var btnScreenshot: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnLive: ImageButton
    private lateinit var btnSchedule: ImageButton
    private lateinit var clockProgressView: ClockProgressView
    private lateinit var spectrumDataSet: LineDataSet
    private var pauseOffset: Long = 0
    private var verticalLimitLine: LimitLine? = null
    private var horizontalLimitLine: LimitLine? = null
    private lateinit var gestureDetector: GestureDetector
    private var verticalCalibrationLineList = mutableListOf<Pair<LimitLine, Pair<Double, EmissionSource>>>()
    private val calibrationPreferencesKey = "calibration_data_"
    private lateinit var sharedPreferences: SharedPreferences

    private val zeroedData: String by lazy {
        requireContext().assets.open("spectrum_zeroed.json").bufferedReader().use { it.readText() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_spectrum, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spectrumChart = view.findViewById(R.id.spectrumChart)
        deviceName = view.findViewById(R.id.deviceName)
        pulseCountValue = view.findViewById(R.id.pulseCountValue)
        measureTimer = view.findViewById(R.id.measureTimer)
        btnCalibration = view.findViewById(R.id.btnCalibration)
        btnFwhm = view.findViewById(R.id.btnFwhm)
        btnScreenshot = view.findViewById(R.id.btnScreenshot)
        btnShare = view.findViewById(R.id.btnShare)
        btnLive = view.findViewById(R.id.btnLive)
        btnSchedule = view.findViewById(R.id.btnSchedule)
        clockProgressView = view.findViewById(R.id.clockProgressView)

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                when(measureMode) {
                    SpectrumMeasureMode.Live -> {
                        // UI actions are blocked while spectrum measurements
                    }
                    SpectrumMeasureMode.Scheduled -> {
                        // UI actions are blocked while spectrum measurements
                    }
                    SpectrumMeasureMode.Fwhm -> {
                        val marker = ResolutionMarkerView(requireContext(), R.layout.marker_view)
                        spectrumChart.marker = marker
                        showPeakResolution(e.x)
                    }
                    SpectrumMeasureMode.Calibration -> {
                        spectrumChart.marker = null
                        val closestEntry = getValuesByTouchPoint(e.x)
                        if (closestEntry != null) {
                            val pickX = closestEntry.x
                            // Check if peakChannel already exists in the list
                            val existingPair = verticalCalibrationLineList.find { abs(it.second.first - pickX) < 20 }
                            if(existingPair != null){
                                val index = verticalCalibrationLineList.indexOf(existingPair)
                                //update or remove
                                val calibrationDialogFragment = CalibrationUpdateOrRemoveDialogFragment.Companion.newInstance(index, existingPair)
                                calibrationDialogFragment.show(childFragmentManager, "calibration_dialog_remove_or_update")
                            } else {
                                val limitLine = LimitLine(e.x, "")
                                val emissionSource = EmissionSource("", 0.0)
                                val calibrationPoint = Pair(limitLine, Pair(pickX.toDouble(), emissionSource))
                                showCalibrationDialog(verticalCalibrationLineList.indexOf(calibrationPoint), calibrationPoint)
                            }
                        }
                    }

                    SpectrumMeasureMode.Idle -> {}
                }
                return true
            }
        })

        initChart(savedInstanceState)

        initCalibration(savedInstanceState)

        setupChart()

        updateChartWithCombinedXAxis()

        showCalibrationLimitLines()

        btnCalibration.setOnClickListener {
            measureMode = SpectrumMeasureMode.Calibration
            measureTimer.stop()
            val spectrumCommand = OpenGammaKitCommands().setOut("off").toByteArray()
            super.send(spectrumCommand)
        }

        btnFwhm.setOnClickListener {
            measureMode = SpectrumMeasureMode.Fwhm
            measureTimer.stop()
            val spectrumCommand = OpenGammaKitCommands().setOut("off").toByteArray()
            super.send(spectrumCommand)
        }

        btnLive.setOnClickListener {
            measureMode = SpectrumMeasureMode.Live
            // **Reset & Start Chronometer**
            measureTimer.base = SystemClock.elapsedRealtime() // Reset timer initially
            measureTimer.start()
            val resetCommand = OpenGammaKitCommands().resetSpectrum().toByteArray()
            super.send(resetCommand)
            val spectrumCommand = OpenGammaKitCommands().setOut("spectrum").toByteArray()
            super.send(spectrumCommand)
        }

        btnSchedule.setOnClickListener {
            measureMode = SpectrumMeasureMode.Scheduled
            // **Reset & Start Chronometer**
            measureTimer.base = SystemClock.elapsedRealtime() // Reset timer initially
            measureTimer.start()

            val resetCommand = OpenGammaKitCommands().resetSpectrum().toByteArray()
            super.send(resetCommand)
            val spectrumCommand = OpenGammaKitCommands().setOut("spectrum").toByteArray()
            super.send(spectrumCommand)

            val recordTime = 10 // Take duration dynamically
            val command = OpenGammaKitCommands().recordStart(recordTime, "test").toByteArray()
            super.send(command)
            // Start tracking progress separately
            startProgressUpdate(recordTime)

            lifecycleScope.launch {

                delay(recordTime * 1000L+200L) // Wait for recording time (handled by coroutine)
                // After recording ends
                val spectrumOffCommand = OpenGammaKitCommands().setOut("off").toByteArray()
                super.send(spectrumOffCommand)

                measureMode = SpectrumMeasureMode.Idle
                measureTimer.stop() // Stop the Chronometer

                measureTimer.base = SystemClock.elapsedRealtime() - (recordTime * 1000L)

                delay(500L) // Wait for command output

                val readCommand = OpenGammaKitCommands().readSpectrum().toByteArray()
                super.send(readCommand)

            }
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }

        btnShare.setOnClickListener {
            shareChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }
    }

    private fun updateRecordingProgress(progress: Float) {
        when {
            progress <= 0f -> {
                btnSchedule.alpha = 0.5f
                clockProgressView.setProgress(0f)
            }
            progress in 0.01f..99.99f -> {
                btnSchedule.alpha = 0.5f
                clockProgressView.setProgress(progress)
            }
            progress >= 100f -> {
                btnSchedule.alpha = 1f
                clockProgressView.setProgress(0f)
            }
        }
    }

    private fun startProgressUpdate(durationSeconds: Int) {
        val handler = Handler(Looper.getMainLooper())
        val totalSteps = durationSeconds * 20
        var step = 0

        val runnable = object : Runnable {
            override fun run() {
                if (step <= totalSteps) {
                    val progress = (step.toFloat() / totalSteps) * 100f
                    updateRecordingProgress(progress)
                    step++
                    handler.postDelayed(this, 50L)
                }
            }
        }

        handler.post(runnable)
    }

    private fun initCalibration(savedInstanceState: Bundle?) {
        savedInstanceState?.getSerializable("CALIBRATION_DATA")?.let { data ->
            val calibrationDataArray = data as Array<CalibrationData>
            val calibrationDataList = calibrationDataArray.toList() // Convert back to List

            // Update your UI or logic with the restored data
            verticalCalibrationLineList = calibrationDataList.map { calibrationData ->
                Pair(
                    LimitLine(calibrationData.limitLineValue, calibrationData.limitLineLabel),
                    Pair(calibrationData.channel, calibrationData.emissionSource)
                )
            }.toMutableList()

            Log.d("Restore", "Calibration data restored: $calibrationDataList")
        }
    }

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        loadCalibrationData()
        updateChartWithCombinedXAxis()
        showCalibrationLimitLines()
        super.setDtr(true)
        val command = OpenGammaKitCommands().setOut("spectrum").toByteArray()
        super.send(command)
    }

    override fun onConnectionFailed() {
        super.onConnectionFailed()
        // Add a message here
    }

    private fun initChart(savedInstanceState: Bundle?) {

        // Restore graph data from savedInstanceState if available
        val restoredEntries = savedInstanceState?.getSerializable("GRAPH_ENTRIES")?.let { data ->
            val entriesArray = data as Array<FloatArray>
            entriesArray.map { entryData ->
                Entry(entryData[0], entryData[1]) // Create Entry objects
            }
        } ?: run {
            // Load zeroed data if no saved state exists
            val parsed = Json.decodeFromString<OpenGammaKitData>(zeroedData)
            val spectrum = parsed.data.firstOrNull()?.resultData?.energySpectrum ?: return@run emptyList()

            spectrum.spectrum.mapIndexed { index, count ->
                Entry(index.toFloat(), count.toFloat())
            }
        }

        // Initialize the dataset with restored or zeroed entries
        spectrumDataSet = LineDataSet(restoredEntries, "Energy Spectrum").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            color = resources.getColor(android.R.color.holo_blue_light, null)
        }
        // Recover the elapsed time and restore the Chronometer
        savedInstanceState?.getLong("chronometer_elapsed_time", 0L)?.let {
            measureTimer.base = SystemClock.elapsedRealtime() - it
        }
        // Restore measureMode state
        measureMode = savedInstanceState?.getString("measure_mode", SpectrumMeasureMode.Live.name)
            ?.let { SpectrumMeasureMode.valueOf(it) } ?: SpectrumMeasureMode.Live
        if(measureMode == SpectrumMeasureMode.Scheduled){
            measureTimer.start() // Resume the timer
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val entriesArray = spectrumDataSet.values.map { entry ->
            floatArrayOf(entry.x, entry.y)
        }.toTypedArray()
        outState.putSerializable("GRAPH_ENTRIES", entriesArray)
        // Convert the calibration data list to a List of CalibrationData instances
        val calibrationDataList = verticalCalibrationLineList.map {
            CalibrationData(
                limitLineValue = it.first.limit,
                limitLineLabel = it.first.label,
                channel = it.second.first,
                emissionSource = it.second.second
            )
        }.toTypedArray()
        outState.putSerializable("CALIBRATION_DATA", calibrationDataList)
        // Save the elapsed time from the Chronometer
        val elapsedTime = SystemClock.elapsedRealtime() - measureTimer.base
        outState.putLong("chronometer_elapsed_time", elapsedTime)

        // Save measureMode state if needed
        outState.putString("measure_mode", measureMode.name)

    }

    private fun setupChart() {

        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)

        spectrumChart.apply {
            data = LineData(spectrumDataSet)
            xAxis.apply {
                labelRotationAngle = 0f
                position = XAxis.XAxisPosition.BOTTOM
                textColor = primaryColor
                position = XAxis.XAxisPosition.BOTTOM
                textColor = primaryColor
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return value.toInt().toString()
                    }
                }
            }
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

        spectrumDataSet.let {
            it.values = entries
            spectrumChart.data.notifyDataChanged()
            spectrumChart.notifyDataSetChanged()
            spectrumChart.invalidate()
        }
    }

    private fun updateChartWithCombinedXAxis() {
        // Ensure calibration data exists
        if (verticalCalibrationLineList.size < 2) {
            println("Insufficient calibration data!")
            return
        }

        // Sort the calibration list by channel
        val sortedCalibrationList = verticalCalibrationLineList.sortedBy { it.second.first }

        // Interpolation function to compute energy for a given channel
        fun interpolateEnergy(channel: Double): Double {
            if (channel <= sortedCalibrationList.first().second.first) {
                // Extrapolate for channels below the first calibration point
                val firstPoint = sortedCalibrationList.first()
                val secondPoint = sortedCalibrationList[1]
                val ratio = (channel - firstPoint.second.first) / (secondPoint.second.first - firstPoint.second.first)
                return firstPoint.second.second.energy.toFloat() + ratio * (secondPoint.second.second.energy.toFloat() - firstPoint.second.second.energy.toFloat())
            } else if (channel >= sortedCalibrationList.last().second.first) {
                // Extrapolate for channels above the last calibration point
                val lastPoint = sortedCalibrationList.last()
                val secondLastPoint = sortedCalibrationList[sortedCalibrationList.size - 2]
                val ratio = (channel - secondLastPoint.second.first) / (lastPoint.second.first - secondLastPoint.second.first)
                return secondLastPoint.second.second.energy.toFloat() + ratio * (lastPoint.second.second.energy.toFloat() - secondLastPoint.second.second.energy.toFloat())
            } else {
                // Interpolate for channels within the calibration range
                for (i in 0 until sortedCalibrationList.size - 1) {
                    val point1 = sortedCalibrationList[i]
                    val point2 = sortedCalibrationList[i + 1]
                    if (channel >= point1.second.first && channel <= point2.second.first) {
                        val ratio = (channel - point1.second.first) / (point2.second.first - point1.second.first)
                        return point1.second.second.energy.toFloat() + ratio * (point2.second.second.energy.toFloat() - point1.second.second.energy.toFloat())
                    }
                }
            }
            return channel // Default fallback (shouldn't be reached)
        }

        // Transform the spectrum data to use energy values
        val energyEntries = if(spectrumDataSet.values.isEmpty()){
            val spectrum = Json.decodeFromString<OpenGammaKitData>(zeroedData).data.firstOrNull()?.resultData?.energySpectrum ?: return
            spectrum.spectrum.mapIndexed { index, zero ->
                val energy = interpolateEnergy(index.toDouble())
                Entry(energy.toFloat(), zero.toFloat())
            }
        } else {
            spectrumDataSet.values.mapIndexed { index, count ->
                val energy = interpolateEnergy(index.toDouble())
                Entry(energy.toFloat(), count.y)
            }
        }

        spectrumDataSet = LineDataSet(energyEntries, "Energy Spectrum").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            color = resources.getColor(android.R.color.holo_blue_light, null)
        }

        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)

        // Add a dummy dataset for calibration lines (to appear in the legend)
        val calibrationLegendDataSet = LineDataSet(listOf(Entry(0f, 0f)), "Calibration Points").apply {
            color = Color.MAGENTA // Use the calibration line color
            lineWidth = 2f
            setDrawValues(false)
            setDrawCircles(false)
        }

        // Update the chart
        spectrumChart.apply {
            data = LineData(spectrumDataSet, calibrationLegendDataSet)

            // Combine Channel and Energy values in x-axis labels
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = primaryColor
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        val energy = interpolateEnergy(value.toDouble())
                        return "%.1f keV".format(energy) // Combine labels with newline
                    }
                }
            }
            axisLeft.textColor = primaryColor
            // Set the description and refresh the chart
            description = Description().apply {
                text = "Counts vs Energy"
                textColor = primaryColor
            }
            legend.textColor = primaryColor
            invalidate() // Refresh the chart to apply changes
        }
        //this code corrects calibration and axis data after screen rotation
        updateChartSpectrumData(spectrumDataSet.values.map {
            it.y.toLong()
        })
    }

    private fun updateCounts(counts: Long){
        pulseCountValue.text = counts.toString()
    }

    private fun updateDeviceName(deviceName: String){
        this@SpectrumFragment.deviceName.text = deviceName
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
    private var isUsbSerialRecording = false
    private var measureMode = SpectrumMeasureMode.Live

    enum class SpectrumMeasureMode {
        Live, Scheduled, Fwhm, Calibration, Idle
    }

//    override fun receive(bytes: ByteArray) {
//        if (bytes.isNotEmpty()) {
//            val inputString = bytes.toString(Charsets.UTF_8)
//            Log.d("Test", ": $inputString")
//        }
//    }

    override fun receive(bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            val inputString = bytes.toString(Charsets.UTF_8)
            Log.d("Test", ": $inputString")
            val EOF = '\u0000'
            for (char in inputString) {
                when (measureMode) {
                    SpectrumMeasureMode.Live, SpectrumMeasureMode.Scheduled -> {
                        when (char) {
                            '[' -> {
                                openBraces++
                                buffer.append(char)
                            }
                            ']' -> {
                                openBraces--
                                buffer.append(char)
                            }
                            EOF, '\r', '\n' -> {} // Ignore these
                            else -> {
                                buffer.append(char)
                            }
                        }
                        if (openBraces == 0 && buffer.isNotEmpty()) {
                            try {
                                val json = Json {
                                    allowTrailingComma = true
                                }
                                val spectrum =
                                    json.decodeFromString<List<Long>>(buffer.toString())
                                val counts = spectrum.fold(0L) { acc, num -> acc + num }
                                Log.d(
                                    "Test",
                                    "Spectrum size: ${spectrum.size}, counts: $counts"
                                )
                                updateChartSpectrumData(spectrum)
                                updateCounts(counts)
                            } catch (e: Exception) {
                                Log.e("Test", "Failed to parse data: ${e.message}")
                                Log.d("Test", buffer.toString())
                            } finally {
                                buffer.clear()
                            }
                        }
                        if (openBraces > 1 || openBraces < 0) {
                            Log.e("Test", "openBraces issue: $buffer")
                            buffer.clear() // Discard malformed or incomplete data
                            openBraces = 0
                        }
                    }

                    SpectrumMeasureMode.Fwhm -> {}
                    SpectrumMeasureMode.Calibration -> {}
                    SpectrumMeasureMode.Idle -> {
                        when (char) {
                            '{', -> {
                                // start input recording into a buffer
                                isUsbSerialRecording = true
                                buffer.append(char)
                            }
                            '\r', '\n' -> {} // Ignore these
                            EOF -> {
                                try {
                                    val json = Json {
                                        allowTrailingComma = true
                                    }
                                    val openGammaKitData =
                                        json.decodeFromString<OpenGammaKitData>(buffer.toString())
                                    Log.d(
                                        "Test",
                                        "Parsed scheduled data spectrum size: ${openGammaKitData.data.size}"
                                    )
                                    // Process scheduled data here
                                    val counts =
                                        openGammaKitData.data[0].resultData.energySpectrum.validPulseCount
                                    val spectrum =
                                        openGammaKitData.data[0].resultData.energySpectrum.spectrum
                                    updateChartSpectrumData(spectrum)
                                    updateCounts(counts)
                                    updateDeviceName(openGammaKitData.data[0].deviceData.deviceName)
                                } catch (e: Exception) {
                                    Log.e("Test", "Failed to parse data: ${e.message}")
                                    Log.d("Test", buffer.toString())
                                } finally {
                                    buffer.clear()
                                    isUsbSerialRecording = false
                                }
                            } else -> {
                                if(isUsbSerialRecording) {
                                    buffer.append(char)
                                }
                            }
                        }
                    }

                    SpectrumMeasureMode.Idle -> {}
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
        saveCalibrationData()
    }

    private fun showPeakResolution(tapX: Float) {
        val closestEntry = getValuesByTouchPoint(tapX)
        if (closestEntry != null) {
            val pickX = closestEntry.x
            val pickY = closestEntry.y

            drawVerticalLimitLine(pickX)

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

    private fun drawVerticalLimitLine(x: Float) {
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

    private fun updateVerticalCalibrationLine(calibrationPointIndex: Int, peakChannel: Double, peakEnergy: Double, peakIsotope: Isotope?) {
        val xAxis = spectrumChart.xAxis
        // Check if peakChannel already exists in the list
        val existingPair = verticalCalibrationLineList[calibrationPointIndex]
        val limitLine = xAxis.limitLines.find { existingPair.first.limit == it.limit }
        xAxis.removeLimitLine(limitLine)
        existingPair.let { verticalCalibrationLineList.remove(it) }
        // Create a new LimitLine and add it to the list
        val label = if(peakIsotope != null){
            "%s %.1f keV".format(peakIsotope.name, peakEnergy)
        } else {
            "%.1f keV".format(peakEnergy)
        }
        val verticalCalibrationLine = LimitLine(peakChannel.toFloat(), label)
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        verticalCalibrationLine.apply {
            lineColor = Color.MAGENTA
            textColor = primaryColor
            lineWidth = 2f
            enableDashedLine(3f, 3f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            yOffset = 40.0f

        }
        xAxis.addLimitLine(verticalCalibrationLine)
        val emissionSource = if(peakIsotope != null){
            EmissionSource(peakIsotope.name, peakEnergy)
        } else {
            EmissionSource("", peakEnergy)
        }
        verticalCalibrationLineList.add(Pair(verticalCalibrationLine, Pair(peakChannel, emissionSource)))
        spectrumChart.invalidate() // Refresh the chart
    }

    private fun addVerticalCalibrationLine(peakChannel: Double, peakEnergy: Double, peakIsotope: Isotope?) {
        val xAxis = spectrumChart.xAxis
        val label = if(peakIsotope != null){
            "%s %.1f keV".format(peakIsotope.name, peakEnergy)
        } else {
            "%.1f keV".format(peakEnergy)
        }
        val verticalCalibrationLine = LimitLine(peakChannel.toFloat(), label)
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        verticalCalibrationLine.apply {
            lineColor = Color.MAGENTA
            textColor = primaryColor
            lineWidth = 2f
            enableDashedLine(3f, 3f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            yOffset = 40.0f
        }
        xAxis.addLimitLine(verticalCalibrationLine)
        val emissionSource = if(peakIsotope != null){
            EmissionSource(peakIsotope.name, peakEnergy)
        } else {
            EmissionSource("", peakEnergy)
        }
        verticalCalibrationLineList.add(Pair(verticalCalibrationLine, Pair(peakChannel, emissionSource)))
        spectrumChart.invalidate() // Refresh the chart
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

    private fun showCalibrationDialog(calibrationPointIndex: Int, calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>) {
        val existing = childFragmentManager.findFragmentByTag("calibration_dialog")
        if (existing == null) {
            val calibrationDialogFragment = CalibrationDialogFragment.Companion.newInstance(calibrationPointIndex, calibrationPoint)
            calibrationDialogFragment.show(childFragmentManager, "calibration_dialog")
        }
    }

    private fun getValuesByTouchPoint(tapX: Float): Entry? {
        val tapY = spectrumChart.height / 2f
        val transformer = spectrumChart.getTransformer(YAxis.AxisDependency.LEFT)
        val touchPoint = transformer.getValuesByTouchPoint(tapX, tapY)
        val tappedX = touchPoint.x
        val closestEntry = getClosestEntryToX(tappedX)
        return closestEntry
    }

    fun saveChartScreenshot(context: Context, spectrumChart: View, tableContainer: View) {
        // Create a Bitmap capturing both the spectrumChart and tableContainer
        val combinedHeight = spectrumChart.height + tableContainer.height
        val bitmap = createBitmap(spectrumChart.width, combinedHeight)
        val canvas = Canvas(bitmap)

        // Draw both views onto the Canvas
        spectrumChart.draw(canvas)
        canvas.translate(0f, spectrumChart.height.toFloat()) // Move down for table
        tableContainer.draw(canvas)

        // Use MediaStore to save the image
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "chart_screenshot.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
        }

        val contentResolver = context.contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            val outputStream: OutputStream? = contentResolver.openOutputStream(imageUri)
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // Show success toast
            Toast.makeText(context, "Screenshot saved successfully!", Toast.LENGTH_SHORT).show()
        } else {
            // Show error toast
            Toast.makeText(context, "Error saving screenshot!", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareChartScreenshot(context: Context, spectrumChart: View, tableContainer: View) {
        // Create a Bitmap including both spectrumChart and tableContainer
        val combinedHeight = spectrumChart.height + tableContainer.height
        val bitmap = createBitmap(spectrumChart.width, combinedHeight)
        val canvas = Canvas(bitmap)

        // Draw both views onto the Canvas
        spectrumChart.draw(canvas)
        canvas.translate(0f, spectrumChart.height.toFloat()) // Position table below the chart
        tableContainer.draw(canvas)

        // Save the image temporarily using MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "chart_screenshot.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
        }

        val contentResolver = context.contentResolver
        val imageUri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // Create an intent for sharing the image
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start the sharing activity
            context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sharedPreferences = context.getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE)
    }

    // Function to save calibration data
    private fun saveCalibrationData() {
        sharedPreferences.edit {

            // Convert the calibration data list to JSON using kotlinx.serialization
            val serializedData = Json.encodeToString(verticalCalibrationLineList.map {
                CalibrationData(
                    limitLineValue = it.first.limit,
                    limitLineLabel = it.first.label,
                    channel = it.second.first,
                    emissionSource = it.second.second
                )
            })

            putString(calibrationPreferencesKey+serialNumber, serializedData)
        } // Commit changes asynchronously
    }

    // Function to load calibration data
    private fun loadCalibrationData() {

        // the calibration data has been already loaded from savedInstanceState
        if(verticalCalibrationLineList.isNotEmpty()){
            return
        }

        val serializedData = sharedPreferences.getString(calibrationPreferencesKey+serialNumber, null) ?: return

        // Deserialize the JSON back into the calibration list using kotlinx.serialization
        val calibrationDataList: List<CalibrationData> = Json.decodeFromString(serializedData)

        calibrationDataList.forEach {
            val limitLine = LimitLine(it.limitLineValue, it.limitLineLabel)
            limitLine.apply {
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
                yOffset = 40.0f

            }
            val emissionSource = it.emissionSource
            verticalCalibrationLineList.add(Pair(limitLine, Pair(it.channel, emissionSource)))
        }
    }

    private fun showCalibrationLimitLines() {
        val xAxis = spectrumChart.xAxis
        xAxis.removeAllLimitLines()
        // Add all calibration limit lines back to the xAxis
        verticalCalibrationLineList.forEach { pair ->
            val limitLine = LimitLine(pair.first.limit, pair.first.label).apply {
                lineColor = Color.MAGENTA
                textColor = resources.getColor(R.color.colorPrimaryText, null)
                lineWidth = 2f
                enableDashedLine(3f, 3f, 0f)
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
                yOffset = 40.0f

            }
            xAxis.addLimitLine(limitLine)
        }
        spectrumChart.invalidate()
    }

    private fun hideCalibrationLimitLines() {
        val xAxis = spectrumChart.xAxis
        // Remove all calibration limit lines from the xAxis
        verticalCalibrationLineList.forEach { pair ->
            xAxis.removeLimitLine(pair.first)
        }
        spectrumChart.invalidate() // Refresh the chart
    }

    override fun onCalibrationRemove(calibrationPointIndex: Int) {
        //remove calibration point
        val xAxis = spectrumChart.xAxis
        val pair = verticalCalibrationLineList[calibrationPointIndex]
        val limitLine = xAxis.limitLines.find { pair.first.limit == it.limit }
        xAxis.removeLimitLine(limitLine)
        pair.let { verticalCalibrationLineList.remove(it) }
        // Recalculate chart data (if necessary)
        if(verticalCalibrationLineList.size < 2){
            setupChart()
        }
        spectrumChart.invalidate()
    }

    override fun onCalibrationUpdate(calibrationPointIndex: Int) {
        showCalibrationDialog(calibrationPointIndex, verticalCalibrationLineList[calibrationPointIndex])
    }

    override fun onCalibrationCompleted(
        calibrationPointIndex: Int,
        peakChannel: Double,
        peakEnergy: Double,
        isotope: Isotope?
    ) {

        if(!isValid(peakChannel = peakChannel, peakEnergy = peakEnergy)){
            return
        }

        if (calibrationPointIndex < 0) {
            // new calibration point creation
            addVerticalCalibrationLine(peakChannel, peakEnergy, isotope)
            updateChartWithCombinedXAxis()
        } else {
            // update an existing calibration point
            updateVerticalCalibrationLine(calibrationPointIndex, peakChannel, peakEnergy, isotope)
        }
    }

    private fun isValidChannel(channel: Double): Boolean {
        // Replace with your actual valid channel range (example: 0 to 4096)
        val minChannel = 0.0
        val maxChannel = spectrumDataSet.entryCount
        return channel in minChannel..maxChannel.toDouble()
    }

    private fun isValidEnergy(energy: Double): Boolean {
        // Replace with your actual valid energy range (example: greater than 0)
        val minEnergy = 0.1
        val maxEnergy = 10000.0 // Arbitrary upper limit for energy
        return energy in minEnergy..maxEnergy
    }

    private fun isValid(peakChannel: Double, peakEnergy: Double): Boolean{

        if (!isValidChannel(peakChannel)) {
            val error = "Invalid channel value: $peakChannel. The channel must be within the valid range."
            val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
            errorDialog.show(childFragmentManager, "error_dialog_fragment")
            return false
        }

        if (!isValidEnergy(peakEnergy)) {
            val error = "Invalid energy value: $peakEnergy. The energy must be within the valid range."
            val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
            errorDialog.show(childFragmentManager, "error_dialog_fragment")
            return false
        }

        if (!isChannelEnergyPairValid(peakChannel, peakEnergy)) {
            val error = "Channel-energy pair ($peakChannel, $peakEnergy) conflicts with existing ranges."
            val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
            errorDialog.show(childFragmentManager, "error_dialog_fragment")
            return false
        }
        return true
    }

    private fun isChannelEnergyPairValid(channel: Double, energy: Double): Boolean {
        // Iterate through the existing verticalCalibrationLineList
        verticalCalibrationLineList.forEach { pair ->
            val existingChannel = pair.second.first
            val existingEnergy = pair.second.second.energy

            // Case 1: New channel (c2) > existing channel (c1)
            if (channel > existingChannel && energy <= existingEnergy) {
                println("Invalid pair: New channel $channel is greater than existing channel $existingChannel, but energy $energy is not greater than existing energy $existingEnergy.")
                return false
            }

            // Case 2: New channel (c2) < existing channel (c1)
            if (channel < existingChannel && energy >= existingEnergy) {
                println("Invalid pair: New channel $channel is less than existing channel $existingChannel, but energy $energy is not less than existing energy $existingEnergy.")
                return false
            }

            // Case 3: New channel matches existing channel exactly (c2 == c1)
            if (channel == existingChannel) {
                println("Invalid pair: New channel $channel matches an existing channel $existingChannel. Duplicates are not allowed.")
                return false
            }
        }

        // If no conflicting pairs are found, return true
        return true
    }

    override fun onReconnect() {
        super.onReconnect(SpectrumFragment(), "spectrum")
    }
}