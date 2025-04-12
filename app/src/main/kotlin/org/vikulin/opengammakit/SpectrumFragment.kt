package org.vikulin.opengammakit


import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import org.vikulin.opengammakit.model.GammaKitData
import org.vikulin.opengammakit.model.OpenGammaKitCommands
import org.vikulin.opengammakit.view.ResolutionMarkerView
import org.vikulin.opengammakit.view.CalibrationDialogFragment
import kotlin.math.abs
import androidx.core.graphics.createBitmap
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import org.vikulin.opengammakit.model.CalibrationData
import org.vikulin.opengammakit.model.EmissionSource
import org.vikulin.opengammakit.model.Isotope
import java.io.OutputStream
import androidx.core.content.edit
import org.vikulin.opengammakit.view.CalibrationUpdateOrRemoveDialogFragment

class SpectrumFragment : SerialConnectionFragment() {

    private lateinit var spectrumChart: LineChart
    private lateinit var deviceValue: TextView
    private lateinit var pulseCountValue: TextView
    private lateinit var measureTimer: Chronometer
    private lateinit var btnCalibration: ImageButton
    private lateinit var btnFwhm: ImageButton
    private lateinit var btnScreenshot: ImageButton
    private lateinit var btnShare: ImageButton
    private var spectrumDataSet: LineDataSet? = null
    private var pauseOffset: Long = 0
    private var verticalLimitLine: LimitLine? = null
    private var horizontalLimitLine: LimitLine? = null
    private lateinit var gestureDetector: GestureDetector
    private var fwhm = false
    private var calibration = false
    private var verticalCalibrationLineList = mutableListOf<Pair<LimitLine, Pair<Double, EmissionSource>>>()

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
        deviceValue = view.findViewById(R.id.deviceValue)
        pulseCountValue = view.findViewById(R.id.pulseCountValue)
        measureTimer = view.findViewById(R.id.measureTimer)
        btnCalibration = view.findViewById(R.id.btnCalibration)
        btnFwhm = view.findViewById(R.id.btnFwhm)
        btnScreenshot = view.findViewById(R.id.btnScreenshot)
        btnShare = view.findViewById(R.id.btnShare)

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if(calibration) {
                    spectrumChart.marker = null
                    val closestEntry = getValuesByTouchPoint(e.x)
                    if (closestEntry != null) {
                        val pickX = closestEntry.x
                        // Check if peakChannel already exists in the list
                        val existingPair = verticalCalibrationLineList.find { abs(it.second.first - pickX) < 20 }
                        if(existingPair != null){
                            //update or remove
                            val calibrationDialogFragment = CalibrationUpdateOrRemoveDialogFragment(existingPair)
                            // Set the listener to receive the callback
                            calibrationDialogFragment.setCalibrationDialogListener(object : CalibrationUpdateOrRemoveDialogFragment.CalibrationDialogListener {
                                override fun onCalibrationRemove(calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>) {
                                    //remove calibration point
                                    calibrationPoint.first.let { spectrumChart.xAxis.removeLimitLine(it) }
                                    calibrationPoint.let { verticalCalibrationLineList.remove(it) }
                                    spectrumChart.invalidate()
                                }

                                override fun onCalibrationUpdate(calibrationPoint: Pair<LimitLine, Pair<Double, EmissionSource>>) {
                                    showCalibrationDialog(e.x)
                                }
                            })
                            calibrationDialogFragment.show(childFragmentManager, "calibration_dialog_remove_or_update")
                        } else {
                            showCalibrationDialog(e.x)
                        }
                    }
                }
                if(fwhm) {
                    val marker = ResolutionMarkerView(requireContext(), R.layout.marker_view)
                    spectrumChart.marker = marker
                    showPeakResolution(e.x)
                }
                return true
            }
        })

        setupChart()

        updateChartWithCombinedXAxis()

        showCalibrationLimitLines()

        btnCalibration.setOnClickListener {
            fwhm = false
            calibration = true
        }

        btnFwhm.setOnClickListener {
            fwhm = true
            calibration = false
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }

        btnShare.setOnClickListener {
            shareChartScreenshot(requireContext(), spectrumChart, view.findViewById(R.id.tableContainer))
        }
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
        val spectrum = Json.decodeFromString<GammaKitData>(zeroedData).data.firstOrNull()?.resultData?.energySpectrum ?: return
        val energyEntries = spectrum.spectrum.mapIndexed { index, count ->
            val energy = interpolateEnergy(index.toDouble())
            Entry(energy.toFloat(), count.toFloat())
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
            axisRight.isEnabled = false // Disable vertical axis since it's not required

            // Set the description and refresh the chart
            description = Description().apply {
                text = "Counts vs Energy"
                textColor = primaryColor
            }
            legend.textColor = primaryColor
            invalidate() // Refresh the chart to apply changes
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

    private fun addOrUpdateVerticalCalibrationLine(x: Float, peakChannel: Double, peakEnergy: Double, peakIsotope: Isotope?) {
        val xAxis = spectrumChart.xAxis
        // Create a new LimitLine and add it to the list
        val label = if(peakIsotope != null){
            "%s %.1f keV".format(peakIsotope.name, peakEnergy)
        } else {
            "%.1f keV".format(peakEnergy)
        }
        val verticalCalibrationLine = LimitLine(x, label)
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        verticalCalibrationLine.apply {
            lineColor = Color.MAGENTA
            textColor = primaryColor
            lineWidth = 2f
            enableDashedLine(3f, 3f, 0f)
        }
        xAxis.addLimitLine(verticalCalibrationLine)
        val calibrationIsotope = if(peakIsotope != null){
            EmissionSource(peakIsotope.name, peakEnergy)
        } else {
            EmissionSource("", peakEnergy)
        }
        verticalCalibrationLineList.add(Pair(verticalCalibrationLine, Pair(peakChannel, calibrationIsotope)))

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

    private fun showCalibrationDialog(tapX: Float) {
        val existing = childFragmentManager.findFragmentByTag("calibration_dialog")
        if (existing == null) {
            val closestEntry = getValuesByTouchPoint(tapX)
            if (closestEntry != null) {
                val pickX = closestEntry.x
                val calibrationDialogFragment = CalibrationDialogFragment(pickX)

                // Set the listener to receive the callback
                calibrationDialogFragment.setCalibrationDialogListener(object : CalibrationDialogFragment.CalibrationDialogListener {
                    override fun onCalibrationCompleted(peakChannel: Double, peakEnergy: Double, isotope: Isotope?) {
                        // Handle the values returned by the dialog
                        // For example, you can update UI or save the calibration data
                        // Do something with peakChannel and peakEnergy
                        //val marker = ResolutionMarkerView(requireContext(), R.layout.marker_view)
                        //spectrumChart.marker = marker
                        addOrUpdateVerticalCalibrationLine(pickX, peakChannel, peakEnergy, isotope)
                        if(verticalCalibrationLineList.size>1){
                            updateChartWithCombinedXAxis()
                        }
                    }
                })

                calibrationDialogFragment.show(childFragmentManager, "calibration_dialog")
            }
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
        val canvas = android.graphics.Canvas(bitmap)

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
        val canvas = android.graphics.Canvas(bitmap)

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

    private val calibrationPreferencesKey = "calibration_data"
    private lateinit var sharedPreferences: SharedPreferences

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sharedPreferences = context.getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCalibrationData() // Load calibration data when the fragment is created
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

            putString(calibrationPreferencesKey, serializedData)
        } // Commit changes asynchronously
    }

    // Function to load calibration data
    private fun loadCalibrationData() {
        val serializedData = sharedPreferences.getString(calibrationPreferencesKey, null) ?: return

        // Deserialize the JSON back into the calibration list using kotlinx.serialization
        val calibrationDataList: List<CalibrationData> = Json.decodeFromString(serializedData)

        // Reconstruct the verticalCalibrationLineList
        verticalCalibrationLineList.clear()
        calibrationDataList.forEach {
            val limitLine = LimitLine(it.limitLineValue, it.limitLineLabel)
            val emissionSource = it.emissionSource
            verticalCalibrationLineList.add(Pair(limitLine, Pair(it.channel, emissionSource)))
        }
    }

    private fun showCalibrationLimitLines() {
        val xAxis = spectrumChart.xAxis
        // Add all calibration limit lines back to the xAxis
        verticalCalibrationLineList.forEach { pair ->
            val limitLine = LimitLine(pair.first.limit, pair.first.label).apply {
                lineColor = Color.MAGENTA
                textColor = resources.getColor(R.color.colorPrimaryText, null)
                lineWidth = 2f
                enableDashedLine(3f, 3f, 0f)
            }
            xAxis.addLimitLine(limitLine)
        }
        spectrumChart.invalidate() // Refresh the chart
    }

    private fun hideCalibrationLimitLines() {
        val xAxis = spectrumChart.xAxis
        // Remove all calibration limit lines from the xAxis
        verticalCalibrationLineList.forEach { pair ->
            xAxis.removeLimitLine(pair.first)
        }
        spectrumChart.invalidate() // Refresh the chart
    }
}