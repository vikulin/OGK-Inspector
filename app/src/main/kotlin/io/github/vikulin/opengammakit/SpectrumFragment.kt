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
import io.github.vikulin.opengammakit.view.SpectrumRecordingTimeDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.text.iterator
import androidx.core.net.toUri
import com.github.mikephil.charting.components.Legend
import io.github.vikulin.opengammakit.math.SpectrumModifier
import io.github.vikulin.opengammakit.math.SpectrumModifier.smartPeakDetect
import io.github.vikulin.opengammakit.model.EnergySpectrum
import io.github.vikulin.opengammakit.model.GammaKitEntry
import io.github.vikulin.opengammakit.view.FwhmSpectrumSelectionDialogFragment
import io.github.vikulin.opengammakit.view.SaveSelectedSpectrumDialogFragment
import io.github.vikulin.opengammakit.view.SpectrumFileChooserDialogFragment
import kotlin.math.log10

class SpectrumFragment : SerialConnectionFragment(),
    CalibrationUpdateOrRemoveDialogFragment.CalibrationDialogListener,
    CalibrationDialogFragment.CalibrationDialogListener,
    SpectrumRecordingTimeDialogFragment.ChooseSpectrumRecordingTimeDialogListener,
    FwhmSpectrumSelectionDialogFragment.ChooseSpectrumDialogListener,
    SpectrumFileChooserDialogFragment.ChooseFileDialogListener,
    SaveSelectedSpectrumDialogFragment.ChooseSpectrumDialogListener{

    private lateinit var spectrumChart: LineChart
    private lateinit var measureTimer: Chronometer
    private lateinit var elapsedTime: TextView
    private lateinit var btnCalibration: ImageButton
    private lateinit var btnFwhm: ImageButton
    private lateinit var btnScreenshot: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnLive: ImageButton
    private lateinit var btnSchedule: ImageButton
    private lateinit var btnAddSpectrum: ImageButton
    private lateinit var btnSaveSpectrum: ImageButton
    private lateinit var btnToggleFilter: ImageButton
    private lateinit var btnToggleDetectPeak: ImageButton
    private lateinit var btnToggleLogScale: ImageButton
    private lateinit var clockProgressView: ClockProgressView
    private lateinit var spectrumDataSet: OpenGammaKitData
    // Indicates which graph is currently active for measurements such as Calibration and FWHM
    private var selectedCalibrationMeasurementIndex = 0
    private var selectedFwhmMeasurementIndex = 0
    // This index is always 0 indicates which position the device spectrum writes to
    private val deviceSpectrumIndex = 0
    private var pauseOffset: Long = 0
    private var verticalLimitLine: LimitLine? = null
    private var horizontalLimitLine: LimitLine? = null
    private var isPeakDetected = false
    private lateinit var gestureDetector: GestureDetector
    private var verticalCalibrationLineList = mutableListOf<Pair<LimitLine, Pair<Double, EmissionSource>>>()
    private val calibrationPreferencesKey = "calibration_data_"
    private lateinit var sharedPreferences: SharedPreferences

    private val zeroedData: String by lazy {
        requireContext().assets.open("spectrum_zeroed.json").bufferedReader().use { it.readText() }
    }

    @Throws(Exception::class) // Annotation indicates this method can throw exceptions
    fun readAndParseFile(context: Context, uri: Uri): OpenGammaKitData {
        // Read the file content
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Unable to open input stream for URI: $uri")

        val buffer = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                buffer.append(line)
            }
        }

        // Parse the JSON content
        val json = Json { ignoreUnknownKeys = true } // Customize JSON parser if necessary
        return json.decodeFromString(buffer.toString())
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
        measureTimer = view.findViewById(R.id.measureTimer)
        elapsedTime = view.findViewById(R.id.remainingTime)
        btnCalibration = view.findViewById(R.id.btnCalibration)
        btnFwhm = view.findViewById(R.id.btnFwhm)
        btnScreenshot = view.findViewById(R.id.btnScreenshot)
        btnShare = view.findViewById(R.id.btnShare)
        btnLive = view.findViewById(R.id.btnLive)
        btnSchedule = view.findViewById(R.id.btnSchedule)
        btnAddSpectrum = view.findViewById(R.id.btnAddSpectrumFile)
        btnSaveSpectrum = view.findViewById(R.id.btnSaveSpectrumFile)
        btnToggleFilter = view.findViewById(R.id.btnToggleFilter)
        btnToggleDetectPeak = view.findViewById(R.id.btnToggleDetectPeak)
        btnToggleLogScale = view.findViewById(R.id.btnToggleLogScale)
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
                        val closestEntry = getValuesByTouchPoint(selectedCalibrationMeasurementIndex, e.x)
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

                    SpectrumMeasureMode.ReadSpectrumFromDevice -> {}
                    SpectrumMeasureMode.ReadSpectrumFromFile -> {}
                }
                return true
            }
        })

        // Try to restore from savedInstanceState first
        val restoredDataSet = savedInstanceState?.getSerializable("GRAPH_ENTRIES") as? OpenGammaKitData
        if (restoredDataSet != null) {
            initChart(savedInstanceState)
            setupChart()
        } else {
            // Fallback to arguments
            val fileUri = arguments?.getString("file_spectrum_uri")?.toUri()
            if (fileUri != null) {
                try {
                    measureMode = SpectrumMeasureMode.ReadSpectrumFromFile
                    val parsedData = readAndParseFile(requireContext(), fileUri)
                    spectrumDataSet = parsedData

                    Log.d(
                        "SpectrumFragment",
                        "Parsed input file $fileUri. Spectrum number: ${parsedData.data.size}"
                    )

                    val outState = Bundle().apply {
                        putSerializable("GRAPH_ENTRIES", parsedData)
                        putString("measure_mode", measureMode.name)
                    }

                    initChart(outState)
                    setupChart()
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorDialog = ErrorDialogFragment.newInstance(e.message ?: "Unknown error")
                    errorDialog.show(childFragmentManager, "error_dialog_fragment")
                }
            } else {
                // If no URI and no saved state, fallback to defaults
                initChart(savedInstanceState)
                setupChart()
            }
        }

        initCalibration(savedInstanceState)

        updateChartWithCombinedXAxis(selectedCalibrationMeasurementIndex)

        showCalibrationLimitLines()

        updateDetectedPeaks()

        btnCalibration.setOnClickListener {
            measureMode = SpectrumMeasureMode.Calibration
            measureTimer.stop()
            val spectrumCommand = OpenGammaKitCommands().setOut("off").toByteArray()
            super.send(spectrumCommand)
        }

        btnFwhm.setOnClickListener {
            if(spectrumDataSet.data.size>1){
                // show graph selection dialog for FWhm measurement
                val selectSpectrumDialog = FwhmSpectrumSelectionDialogFragment.Companion.newInstance(spectrumDataSet)
                selectSpectrumDialog.show(childFragmentManager, "select_spectrum_dialog_fragment")
            } else {
                measureMode = SpectrumMeasureMode.Fwhm
                measureTimer.stop()
                val spectrumCommand = OpenGammaKitCommands().setOut("off").toByteArray()
                super.send(spectrumCommand)
            }
        }

        btnLive.setOnClickListener {
            val fileUri = arguments?.getString("file_spectrum_uri")?.toUri()
            fileUri?.let { uri ->
                val error = "Live mode isn't allowed"
                val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
                errorDialog.show(childFragmentManager, "error_dialog_fragment")
            }?: run {
                measureMode = SpectrumMeasureMode.Live
                elapsedTime.text = ""
                // **Reset & Start Chronometer**
                measureTimer.base = SystemClock.elapsedRealtime() // Reset timer initially
                measureTimer.start()
                val resetCommand = OpenGammaKitCommands().resetSpectrum().toByteArray()
                super.send(resetCommand)
                val spectrumCommand = OpenGammaKitCommands().setOut("spectrum").toByteArray()
                super.send(spectrumCommand)
            }
        }

        btnSchedule.setOnClickListener {
            val fileUri = arguments?.getString("file_spectrum_uri")?.toUri()
            fileUri?.let { uri ->
                val error = "Scheduled mode isn't allowed"
                val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
                errorDialog.show(childFragmentManager, "error_dialog_fragment")
            }?: run {
                val spectrumRecordingTimeDialog =
                    SpectrumRecordingTimeDialogFragment.newInstance(60)
                spectrumRecordingTimeDialog.show(childFragmentManager, "spectrum_recording_time")
            }
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }

        btnShare.setOnClickListener {
            shareChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }

        btnAddSpectrum.setOnClickListener {
            val spectrumFileChooserDialog = SpectrumFileChooserDialogFragment()
            spectrumFileChooserDialog.show(childFragmentManager, "spectrum_file_chooser_dialog_fragment")
        }

        btnSaveSpectrum.setOnClickListener {
            if(spectrumDataSet.data.size>1){
                // show select spectrum to save into a file
                val spectrumFileChooserDialog = SaveSelectedSpectrumDialogFragment.newInstance(spectrumDataSet)
                spectrumFileChooserDialog.show(childFragmentManager, "save_spectrum_file_dialog_fragment")
            } else {
                saveGammaKitDataAsJson(requireContext(), spectrumDataSet)
            }
        }

        btnToggleFilter.setOnClickListener {
            toggleSavitzkyGolayFilter()
            updateChartSpectrumData()
        }

        btnToggleDetectPeak.setOnClickListener {
            if(!isPeakDetected) {
                applySavitzkyGolayFilter(apply = true)
                smartPeakDetect(spectrumDataSet, listOf(0))
                drawPeakLimitLines()
                isPeakDetected = true
            } else {
                applySavitzkyGolayFilter(apply = false)
                val xAxis = spectrumChart.xAxis
                xAxis.limitLines.removeIf { it.label.startsWith("P@") }
                isPeakDetected = false
            }
            updateChartSpectrumData()
        }

        btnToggleLogScale.setOnClickListener {
            toggleLogScaleFilter()
            updateChartSpectrumData()
        }
    }

    fun drawPeakLimitLines() {
        val xAxis = spectrumChart.xAxis
        xAxis.limitLines.removeIf { it.label.startsWith("P@") }

        // Assume you are working with spectrum index = 0
        val energySpectrum = spectrumDataSet.data[0].resultData.energySpectrum
        val peaks = energySpectrum.peaks
        if (peaks.isEmpty()) return

        val sortedCalibrationList = verticalCalibrationLineList.sortedBy { it.second.first }

        for (peak in peaks) {
            val labelText = if (verticalCalibrationLineList.size > 1) {
                // Use calibrated energy
                val energy = interpolateEnergy(sortedCalibrationList, peak.channel.toDouble()).toLong()
                "P@$energy keV"
            } else {
                "P@${peak.channel}"
            }

            val limitLine = LimitLine(peak.channel.toFloat())
            limitLine.apply {
                lineColor = Color.GRAY
                lineWidth = 1f
                label = labelText
                enableDashedLine(5f, 5f, 0f)
                textColor = resources.getColor(R.color.colorPrimaryText, null)
            }
            xAxis.addLimitLine(limitLine)
        }

        spectrumChart.invalidate() // Refresh the chart
    }

    private fun updateRecordingProgress(progress: Float, remainingSeconds: Long) {
        when {
            progress <= 0f -> {
                btnSchedule.alpha = 0.5f
                clockProgressView.setProgress(0f)
                elapsedTime.text = ""
            }
            progress in 0.01f..99.99f -> {
                btnSchedule.alpha = 0.5f
                clockProgressView.setProgress(progress)
                // Update the remaining time TextView
                val remainingTime = formatTimeSkipZeros(remainingSeconds)
                elapsedTime.text = "$remainingTime left"
            }
            progress >= 100f -> {
                btnSchedule.alpha = 1f
                clockProgressView.setProgress(0f)
                elapsedTime.text = ""
            }
        }
    }

    private fun startProgressUpdate(durationSeconds: Long) {
        val handler = Handler(Looper.getMainLooper())
        val totalSteps = durationSeconds * 20
        var step = 0

        val runnable = object : Runnable {
            override fun run() {
                if (step <= totalSteps) {
                    val progress = (step.toFloat() / totalSteps) * 100f
                    val remainingSeconds = durationSeconds - (step / 20) // Convert steps back to seconds

                    updateRecordingProgress(progress, remainingSeconds)

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
        updateChartWithCombinedXAxis(selectedCalibrationMeasurementIndex)
        showCalibrationLimitLines()
        updateDetectedPeaks()
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
        val restoredEntries: OpenGammaKitData = savedInstanceState?.getSerializable("GRAPH_ENTRIES")?.let { data ->
            data as? OpenGammaKitData
        } ?: run {
            // There is no initial data so it's starting from zeroed spectrum data
            // Load zeroed data if no saved state exists
            Json.decodeFromString<OpenGammaKitData>(zeroedData)
        }

        // Initialize the dataset with restored or zeroed entries
        spectrumDataSet = restoredEntries
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
        selectedFwhmMeasurementIndex =
            savedInstanceState?.getInt("selected_fwhm_measurement_index", 0) ?: run {
                0
            }
        isPeakDetected =
            savedInstanceState?.getBoolean("is_peak_detected", false) ?: run {
                false
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("GRAPH_ENTRIES", spectrumDataSet)
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
        outState.putInt("selected_fwhm_measurement_index", selectedFwhmMeasurementIndex)
        outState.putBoolean("is_peak_detected", isPeakDetected)
    }

    fun formatTimeSkipZeros(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val components = mutableListOf<String>()
        if (hours > 0) components.add("${hours}h")
        if (minutes > 0) components.add("${minutes}m")
        if (secs > 0) components.add("${secs}s")

        return components.joinToString("")
    }

    private fun getSpectrumLabel(index: Int, entry: GammaKitEntry): String{
        val ct = entry.resultData.energySpectrum.validPulseCount
        val mt = entry.resultData.energySpectrum.measurementTime
        val ch = entry.resultData.energySpectrum.numberOfChannels
        val t = formatTimeSkipZeros(mt)
        return (entry.deviceData.deviceName?:"Spectrum ${index + 1}")+" Ch $ch Ct $ct T $t"
    }

    private fun setupChart() {
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        //copy data to outputSpectrum
        spectrumDataSet.data.mapIndexed { index, entry ->
            if(entry.resultData.energySpectrum.filters.isEmpty()){
                resetSpectrumData(entry.resultData.energySpectrum)
            }
        }
        // Create LineDataSets from each GammaKitEntry in spectrumDataSet
        val dataSets = spectrumDataSet.data.mapIndexed { index, entry ->
            val spectrum = entry.resultData.energySpectrum.outputSpectrum
            val entries = spectrum.mapIndexed { ch, count ->
                Entry(ch.toFloat(), count.toFloat())
            }
            val label = getSpectrumLabel(index, entry)
            LineDataSet(entries, label).apply {
                mode = LineDataSet.Mode.CUBIC_BEZIER
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawValues(false)
                color = getLineColor(requireContext(), index)
            }
        }

        // Set up chart
        spectrumChart.apply {
            data = LineData(dataSets)
            xAxis.apply {
                labelRotationAngle = 0f
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
            axisRight.textColor = primaryColor
            description = Description().apply {
                text = "Channel vs Counts"
                textColor = primaryColor
            }
            legend.apply {
                isWordWrapEnabled = true
                form = Legend.LegendForm.LINE
                textColor = resources.getColor(R.color.colorPrimaryText, null)
                textSize = 12f
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                xEntrySpace = 20f
            }
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

    private fun updateChartSpectrumData() {

        val dataSets = spectrumDataSet.data.mapIndexed { index, entry ->
            val spectrum = entry.resultData.energySpectrum.outputSpectrum
            val entries = spectrum.mapIndexed { ch, count ->
                Entry(ch.toFloat(), count.toFloat())
            }
            val label = getSpectrumLabel(index, entry)
            LineDataSet(entries, label).apply {
                mode = LineDataSet.Mode.CUBIC_BEZIER
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawValues(false)
                color = getLineColor(requireContext(), index)
            }
        }

        // Set up chart
        spectrumChart.apply {
            data = LineData(dataSets)
        }
        spectrumChart.legend.apply {
            isWordWrapEnabled = true
            form = Legend.LegendForm.LINE
            textColor = resources.getColor(R.color.colorPrimaryText, null)
            textSize = 12f
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            xEntrySpace = 20f
        }
        spectrumChart.data.notifyDataChanged()
        spectrumChart.notifyDataSetChanged()
        spectrumChart.invalidate()
    }

    private fun updateChartWithCombinedXAxis(selectedIndex: Int) {
        if (verticalCalibrationLineList.size < 2) {
            println("Insufficient calibration data!")
            return
        }

        // Sort calibration lines
        val sortedCalibrationList = verticalCalibrationLineList.sortedBy { it.second.first }

        // Get the selected spectrum
        val selectedSpectrum = spectrumDataSet.data.getOrNull(selectedIndex)?.resultData?.energySpectrum
            ?: Json.decodeFromString<OpenGammaKitData>(zeroedData).data.firstOrNull()?.resultData?.energySpectrum
            ?: return

        // Interpolate channel -> energy and create entries
        val energyEntries = selectedSpectrum.spectrum.mapIndexed { index, count ->
            val energy = interpolateEnergy(sortedCalibrationList, index.toDouble())
            Entry(energy.toFloat(), count.toFloat())
        }
        val label = getSpectrumLabel(selectedIndex, spectrumDataSet.data[selectedIndex])
        val calibratedDataSet = LineDataSet(energyEntries, label).apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            color = getLineColor(requireContext(), selectedIndex)
        }

        val calibrationLegendDataSet = LineDataSet(listOf(Entry(0f, 0f)), "Calibration Points").apply {
            color = Color.MAGENTA
            lineWidth = 2f
            setDrawValues(false)
            setDrawCircles(false)
        }

        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)

        spectrumChart.apply {
            data = LineData(calibratedDataSet, calibrationLegendDataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = primaryColor
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        val energy = interpolateEnergy(sortedCalibrationList, value.toDouble())
                        return "%.1f keV".format(energy)
                    }
                }
            }
            axisLeft.textColor = primaryColor
            axisRight.textColor = primaryColor
            description = Description().apply {
                text = "Counts vs Energy"
                textColor = primaryColor
            }
            legend.apply {
                isWordWrapEnabled = true
                form = Legend.LegendForm.LINE
                textColor = resources.getColor(R.color.colorPrimaryText, null)
                textSize = 12f
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                xEntrySpace = 20f
            }
            invalidate()
        }
        // Update entries after axis transformation
        updateChartSpectrumData()
    }

    private fun updateDetectedPeaks() {
        if(isPeakDetected){
            applySavitzkyGolayFilter(apply = true)
            smartPeakDetect(spectrumDataSet, listOf(0))
            drawPeakLimitLines()
        }
    }

    private fun interpolateEnergy(sortedCalibrationList:  List<Pair<LimitLine, Pair<Double, EmissionSource>>>, channel: Double): Double {
        return when {
            channel <= sortedCalibrationList.first().second.first -> {
                val (c1, e1) = sortedCalibrationList.first().second
                val (c2, e2) = sortedCalibrationList[1].second
                val ratio = (channel - c1) / (c2 - c1)
                e1.energy + ratio * (e2.energy - e1.energy)
            }

            channel >= sortedCalibrationList.last().second.first -> {
                val (c1, e1) = sortedCalibrationList[sortedCalibrationList.size - 2].second
                val (c2, e2) = sortedCalibrationList.last().second
                val ratio = (channel - c1) / (c2 - c1)
                e1.energy + ratio * (e2.energy - e1.energy)
            }

            else -> {
                for (i in 0 until sortedCalibrationList.size - 1) {
                    val (c1, e1) = sortedCalibrationList[i].second
                    val (c2, e2) = sortedCalibrationList[i + 1].second
                    if (channel in c1..c2) {
                        val ratio = (channel - c1) / (c2 - c1)
                        return e1.energy + ratio * (e2.energy - e1.energy)
                    }
                }
                channel // fallback
            }
        }
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
        Live, Scheduled, Fwhm, Calibration, ReadSpectrumFromDevice, ReadSpectrumFromFile
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
            //Log.d("Test", ": $inputString")
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
                                var energySpectrum = spectrumDataSet.data[deviceSpectrumIndex].
                                resultData.energySpectrum
                                energySpectrum.spectrum = spectrum
                                energySpectrum.validPulseCount =
                                    spectrum.fold(0L) { acc, num -> acc + num }
                                energySpectrum.numberOfChannels = spectrum.size
                                energySpectrum.measurementTime =
                                    (SystemClock.elapsedRealtime() - measureTimer.base)/1000
                                // copy spectrum. TODO apply filters and peaks detection
                                resetSpectrumData(energySpectrum)

                                updateChartSpectrumData()
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
                    SpectrumMeasureMode.ReadSpectrumFromDevice -> {
                        when (char) {
                            '{' -> {
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
                                        "Parsed scheduled spectrum number: ${openGammaKitData.data.size}"
                                    )
                                    spectrumDataSet.data[deviceSpectrumIndex] = openGammaKitData.data[deviceSpectrumIndex]
                                    var energySpectrum = spectrumDataSet.data[deviceSpectrumIndex].
                                    resultData.energySpectrum
                                    // copy spectrum. TODO apply filters and peaks detection
                                    resetSpectrumData(energySpectrum)
                                    updateChartSpectrumData()
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

                    SpectrumMeasureMode.ReadSpectrumFromFile -> {}
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
        when(measureMode) {
            SpectrumMeasureMode.Live -> {startTimer()}
            SpectrumMeasureMode.Scheduled -> {startTimer()}
            SpectrumMeasureMode.Fwhm -> {}
            SpectrumMeasureMode.Calibration -> {}
            SpectrumMeasureMode.ReadSpectrumFromDevice -> {}
            SpectrumMeasureMode.ReadSpectrumFromFile -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        when(measureMode) {
            SpectrumMeasureMode.Live, SpectrumMeasureMode.Scheduled  -> {
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
            SpectrumMeasureMode.Fwhm -> {}
            SpectrumMeasureMode.Calibration -> {}
            SpectrumMeasureMode.ReadSpectrumFromDevice -> {}
            SpectrumMeasureMode.ReadSpectrumFromFile -> {}
        }
        saveCalibrationData()
    }

    private fun showPeakResolution(tapX: Float) {
        val closestEntry = getValuesByTouchPoint(selectedFwhmMeasurementIndex, tapX)
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
                refreshContent(Entry(pickX, halfHeight), Highlight(pickX, halfHeight, selectedFwhmMeasurementIndex))
            }

            spectrumChart.highlightValue(Highlight(pickX, halfHeight, selectedFwhmMeasurementIndex)) // triggers marker display

        }
    }

    private fun getClosestEntryToX(selectedIndex: Int, xVal: Double): Entry? {
        val dataSet = spectrumChart.data.getDataSetByIndex(selectedIndex)
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
        val dataSet = spectrumChart.data.getDataSetByIndex(selectedFwhmMeasurementIndex)
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

    private fun getValuesByTouchPoint(selectedIndex: Int, tapX: Float): Entry? {
        val tapY = spectrumChart.height / 2f
        val transformer = spectrumChart.getTransformer(YAxis.AxisDependency.LEFT)
        val touchPoint = transformer.getValuesByTouchPoint(tapX, tapY)
        val tappedX = touchPoint.x
        val closestEntry = getClosestEntryToX(selectedIndex, tappedX)
        return closestEntry
    }

    fun saveChartScreenshot(context: Context, spectrumChart: View, tableContainer: View) {
        val width = spectrumChart.width
        val combinedHeight = spectrumChart.height + tableContainer.height

        // Create a bitmap with ARGB_8888 to support alpha (even though we’ll override it)
        val bitmap = Bitmap.createBitmap(width, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill the canvas with black before drawing the views
        canvas.drawColor(Color.BLACK)

        // Draw the spectrum chart and table container
        spectrumChart.draw(canvas)
        canvas.translate(0f, spectrumChart.height.toFloat())
        tableContainer.draw(canvas)

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "chart_screenshot.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
        }

        val contentResolver = context.contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            Toast.makeText(context, "Screenshot saved successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Error saving screenshot!", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveGammaKitDataAsJson(context: Context, gammaKitData: OpenGammaKitData) {
        val jsonFileName = "gamma_data_${System.currentTimeMillis()}.json"

        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, jsonFileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/OpenGammaKit/")
        }

        val contentResolver = context.contentResolver
        val jsonUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        if (jsonUri != null) {
            contentResolver.openOutputStream(jsonUri)?.use { outputStream ->
                val jsonString = Json.encodeToString(gammaKitData)
                outputStream.write(jsonString.toByteArray())
                Toast.makeText(context, "Data saved as JSON!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Failed to save JSON!", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareChartScreenshot(context: Context, spectrumChart: View, tableContainer: View) {
        // Create a Bitmap including both spectrumChart and tableContainer
        val combinedHeight = spectrumChart.height + tableContainer.height
        val bitmap = createBitmap(spectrumChart.width, combinedHeight)
        val canvas = Canvas(bitmap)

        // Fill the canvas with black before drawing the views
        canvas.drawColor(Color.BLACK)

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
        serialNumber.let {
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

        if(!isValid(calibrationPointIndex = calibrationPointIndex, peakChannel = peakChannel, peakEnergy = peakEnergy)){
            return
        }

        if (calibrationPointIndex < 0) {
            // new calibration point creation
            addVerticalCalibrationLine(peakChannel, peakEnergy, isotope)
        } else {
            // update an existing calibration point
            updateVerticalCalibrationLine(calibrationPointIndex, peakChannel, peakEnergy, isotope)
        }

        updateChartWithCombinedXAxis(selectedCalibrationMeasurementIndex)
        updateDetectedPeaks()
    }

    private fun isValidChannel(channel: Double): Boolean {
        // Replace with your actual valid channel range (example: 0 to 4096)
        val minChannel = 0.0
        val maxChannel = spectrumDataSet.data[selectedCalibrationMeasurementIndex].resultData.energySpectrum.numberOfChannels
        return channel in minChannel..maxChannel.toDouble()
    }

    private fun isValidEnergy(energy: Double): Boolean {
        // Replace with your actual valid energy range (example: greater than 0)
        val minEnergy = 0.1
        val maxEnergy = 10000.0 // Arbitrary upper limit for energy
        return energy in minEnergy..maxEnergy
    }

    private fun isValid(calibrationPointIndex: Int, peakChannel: Double, peakEnergy: Double): Boolean{

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

        if (!isChannelEnergyPairValid(calibrationPointIndex, peakChannel, peakEnergy)) {
            val error = "Channel-energy pair ($peakChannel, $peakEnergy) conflicts with existing ranges."
            val errorDialog = ErrorDialogFragment.Companion.newInstance(error)
            errorDialog.show(childFragmentManager, "error_dialog_fragment")
            return false
        }
        return true
    }

    private fun isChannelEnergyPairValid(calibrationPointIndex: Int, channel: Double, energy: Double): Boolean {
        val simulatedList = verticalCalibrationLineList.toMutableList()
        val newPair = channel to EmissionSource("", energy)

        // Check for duplicate channels
        val duplicateChannel = simulatedList.any { it.second.first == channel && it != simulatedList.getOrNull(calibrationPointIndex) }
        if (duplicateChannel) {
            println("Invalid pair: A record with channel $channel already exists.")
            return false
        }

        if (calibrationPointIndex < 0) {
            // Adding new point
            simulatedList.add(LimitLine(channel.toFloat()) to newPair)
        } else {
            // Updating an existing point
            val existingLimitLine = simulatedList[calibrationPointIndex].first
            simulatedList[calibrationPointIndex] = existingLimitLine to newPair
        }

        // Iterate through the simulated list for validation
        for ((_, existingPair) in simulatedList) {
            val existingChannel = existingPair.first
            val existingEnergy = existingPair.second.energy

            // Case 1: New channel (c2) > existing channel (c1) but energy is not greater
            if (channel > existingChannel && energy <= existingEnergy) {
                println("Invalid pair: New channel $channel is greater than existing channel $existingChannel, but energy $energy is not greater than existing energy $existingEnergy.")
                return false
            }

            // Case 2: New channel (c2) < existing channel (c1) but energy is not less
            if (channel < existingChannel && energy >= existingEnergy) {
                println("Invalid pair: New channel $channel is less than existing channel $existingChannel, but energy $energy is not less than existing energy $existingEnergy.")
                return false
            }
        }

        // If no conflicting pairs are found, return true
        return true
    }

    override fun onReconnect() {
        super.onReconnect(SpectrumFragment(), "spectrum")
    }

    override fun onRunError(e: Exception?) {
        super.onRunError(e)
        measureTimer.stop()
    }

    override fun onSpectrumRecordingTime(time: Long) {
        measureMode = SpectrumMeasureMode.Scheduled
        // **Reset & Start Chronometer**
        measureTimer.base = SystemClock.elapsedRealtime() // Reset timer initially
        measureTimer.start()

        val resetCommand = OpenGammaKitCommands().resetSpectrum().toByteArray()
        super.send(resetCommand)
        val spectrumCommand = OpenGammaKitCommands().setOut("spectrum").toByteArray()
        super.send(spectrumCommand)

        val command = OpenGammaKitCommands().recordStart(time, "test").toByteArray()
        super.send(command)
        // Start tracking progress separately
        startProgressUpdate(time)

        lifecycleScope.launch {

            delay(time * 1000L+200L) // Wait for recording time (handled by coroutine)
            // After recording ends
            val spectrumOffCommand = OpenGammaKitCommands().setOut("off").toByteArray()
            super.send(spectrumOffCommand)

            measureMode = SpectrumMeasureMode.ReadSpectrumFromDevice
            measureTimer.stop() // Stop the Chronometer

            measureTimer.base = SystemClock.elapsedRealtime() - (time * 1000L)

            delay(500L) // Wait for command output

            val readCommand = OpenGammaKitCommands().readSpectrum().toByteArray()
            super.send(readCommand)

        }
    }

    override fun onChoose(spectrumIndex: Int) {
        selectedFwhmMeasurementIndex = spectrumIndex
        measureMode = SpectrumMeasureMode.Fwhm
        measureTimer.stop()
        val spectrumCommand = OpenGammaKitCommands().setOut("off").toByteArray()
        super.send(spectrumCommand)
    }

    override fun onChoose(uri: String) {
        val openGammaKitData = readAndParseFile(requireContext(), uri.toUri())
        spectrumDataSet.data.addAll(openGammaKitData.data)
        updateChartSpectrumData()
    }

    override fun onChooseMultiple(selectedIndexes: List<Int>) {
        // Filter entries based on selected indexes
        val selectedEntries = spectrumDataSet.data.filterIndexed { index, _ ->
            index in selectedIndexes
        }.toMutableList()

        // Create a new OpenGammaKitData with the same schema version and filtered data
        val filteredData = OpenGammaKitData(
            schemaVersion = spectrumDataSet.schemaVersion,
            data = selectedEntries
        )

        // Call save method with the filtered data
        saveGammaKitDataAsJson(requireContext(), filteredData)
    }

    private fun toggleSavitzkyGolayFilter(){
        for (entry in spectrumDataSet.data) {
            val energy = entry.resultData.energySpectrum
            if (!energy.filters.contains("SavitzkyGolay")) {
                // Apply filter and add tag
                val inputSpectrum = if(energy.filters.isNotEmpty()) {
                    entry.resultData.energySpectrum.outputSpectrum
                } else {
                    entry.resultData.energySpectrum.spectrum.map { it.toDouble() }
                }
                SpectrumModifier.applySavitzkyGolayFilter(inputSpectrum, entry)
                entry.resultData.energySpectrum.filters.add("SavitzkyGolay")
            } else {
                energy.filters.clear()
                resetSpectrumData(energy)
            }
        }
    }

    private fun applySavitzkyGolayFilter(apply: Boolean) {
        for (entry in spectrumDataSet.data) {
            val energy = entry.resultData.energySpectrum
            if (apply) {
                if (!energy.filters.contains("SavitzkyGolay")) {
                    // Apply filter and add tag
                    val inputSpectrum = if(energy.filters.isNotEmpty()) {
                            entry.resultData.energySpectrum.outputSpectrum
                        } else {
                            entry.resultData.energySpectrum.spectrum.map { it.toDouble() }
                        }
                    SpectrumModifier.applySavitzkyGolayFilter(inputSpectrum, entry)
                    entry.resultData.energySpectrum.filters.add("SavitzkyGolay")
                }
            } else {
                energy.filters.clear()
                resetSpectrumData(energy)
            }
        }
    }

    private fun resetSpectrumData(energySpectrum: EnergySpectrum){
        energySpectrum.outputSpectrum =
            energySpectrum.spectrum.map { count ->
                count.toDouble()
            }.toMutableList()
    }

    private fun toggleLogScaleFilter(){
        for (entry in spectrumDataSet.data) {
            val energy = entry.resultData.energySpectrum
            if (!energy.filters.contains("LogScale")) {
                // Apply filter and add tag
                applyLogScale(entry, true)
            } else {
                applyLogScale(entry, false)
            }
        }
    }

    fun applyLogScale(entry: GammaKitEntry, apply: Boolean) {
        entry.resultData.energySpectrum.applyLogScale(apply)
    }

    fun EnergySpectrum.applyLogScale(apply: Boolean) {
        if (apply) {
            if (!filters.contains("LogScale")) {
                val inputSpectrum = if(filters.isNotEmpty()) {
                    outputSpectrum
                } else {
                    spectrum.map { it.toDouble() }
                }
                outputSpectrum = inputSpectrum.map { count ->
                    val adjusted = if (count > 1L) count.toDouble() else 1.0
                    log10(adjusted)
                }.toMutableList()
                filters.add("LogScale")
            }
        } else {
            filters.clear()
            resetSpectrumData(this)
        }
    }

    companion object {
        fun getLineColor(context: Context, index: Int): Int {
            val colors = listOf(
                context.resources.getColor(android.R.color.holo_blue_light, null),
                Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.YELLOW
            )
            return colors[index % colors.size]
        }
    }
}