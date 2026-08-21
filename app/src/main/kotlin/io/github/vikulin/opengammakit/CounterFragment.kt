package io.github.vikulin.opengammakit

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

import android.media.MediaPlayer
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import io.github.vikulin.opengammakit.view.CounterThresholdDialogFragment

class CounterFragment : SerialConnectionFragment(),
    CounterThresholdDialogFragment.SaveThresholdDialogListener,
    CounterThresholdDialogFragment.ChooseThresholdDialogListener{

    private lateinit var currentRateTextView: TextView
    private lateinit var btnThreshold: ImageButton
    private lateinit var currentTimeTextView: TextView
    private lateinit var rateLineChart: LineChart

    private val PREF_NAME = "counter_preferences"
    private val KEY_THRESHOLD = "threshold_"
    private var threshold: Int = 9999999 // Default value
    private var isBlinking = false
    private var alarmJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector
    private lateinit var lineDataSet: LineDataSet
    private var chooseThreshold = false

    // No push-based counts/events stream exists in the JSON protocol (see
    // REQUIREMENTS.md 6.2's message types), so the live rate is derived from
    // polling request_status's "cps" field instead.
    private var statusPollJob: Job? = null
    private val statusPollIntervalMs = 1000L

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        loadThreshold()
        setCounterThreshold(threshold)
        super.setDtr(true)
        startStatusPolling()
    }

    override fun onConnectionFailed() {
        super.onConnectionFailed()
        // Add a message here
    }

    private fun startStatusPolling() {
        stopStatusPolling()
        statusPollJob = lifecycleScope.launch {
            while (isActive) {
                send(OpenGammaKitCommands().requestStatus().toByteArray())
                delay(statusPollIntervalMs)
            }
        }
    }

    private fun stopStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    override fun onDisconnected() {
        super.onDisconnected()
        stopStatusPolling()
    }

    private fun saveThreshold() {
        val sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_THRESHOLD+serialNumber, threshold)
        editor.apply() // Asynchronously save the value
    }

    private fun loadThreshold() {
        val sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        threshold = sharedPreferences.getInt(KEY_THRESHOLD+serialNumber, 9999999) // Default to 9999999 if not set
    }

    private val buffer = StringBuilder()
    private var openBraces = 0

    /**
     * Parses a JSON "status" message (Core/Src/protocol.c Protocol_SerializeStatus())
     * and drives the counts/sec chart from its "cps" field.
     */
    override fun receive(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val inputString = bytes.toString(Charsets.UTF_8)
        for (char in inputString) {
            when (char) {
                '{' -> {
                    openBraces++
                    buffer.append(char)
                }
                '}' -> {
                    openBraces--
                    buffer.append(char)
                }
                '\r', '\n' -> {} // Ignore these
                else -> {
                    if (openBraces > 0) {
                        buffer.append(char)
                    }
                }
            }
            if (openBraces == 0 && buffer.isNotEmpty()) {
                try {
                    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(buffer.toString()).jsonObject
                    if (root["type"]?.jsonPrimitive?.contentOrNull == "status") {
                        root["cps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { rate ->
                            requireActivity().runOnUiThread {
                                updateRate(rate)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CounterFragment", "Failed to parse status message: ${e.message}")
                } finally {
                    buffer.clear()
                }
            }
            if (openBraces < 0) {
                buffer.clear()
                openBraces = 0
            }
        }
    }

    override fun status(str: String) {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val entriesArray = lineDataSet.values.map { entry ->
            floatArrayOf(entry.x, entry.y)
        }.toTypedArray()
        outState.putSerializable("GRAPH_ENTRIES", entriesArray)
        outState.putInt("COUNTER_THRESHOLD", threshold)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_counter, container, false)

        currentRateTextView = view.findViewById(R.id.currentRateTextView)
        btnThreshold = view.findViewById(R.id.btnThreshold)
        currentTimeTextView = view.findViewById(R.id.currentTimeTextView)
        rateLineChart = view.findViewById(R.id.rateLineChart)

        btnThreshold.setOnClickListener {
            //set limit line for the threshold
            val counterThresholdDialog = CounterThresholdDialogFragment.Companion.newInstance(threshold)
            counterThresholdDialog.show(childFragmentManager, "counter_threshold_dialog_fragment")
        }

        setupLineChart()

        // Restore graph data from savedInstanceState
        savedInstanceState?.getSerializable("GRAPH_ENTRIES")?.let { data ->
            val entriesArray = data as Array<FloatArray>
            val restoredEntries = entriesArray.map { entryData ->
                Entry(entryData[0], entryData[1]) // Create Entry objects
            }
            lineDataSet.values = restoredEntries // Update the LineDataSet directly
            rateLineChart.data.notifyDataChanged()
            rateLineChart.notifyDataSetChanged()
            rateLineChart.invalidate()
        }

        savedInstanceState?.getInt("COUNTER_THRESHOLD")?.let {
            threshold = it
        } ?: run { }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.keepScreenOn = true
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if(chooseThreshold) {
                    val touchPoint = getTouchPoint(e.y)
                    threshold = touchPoint.toInt()
                    setCounterThreshold(threshold)
                    saveThreshold()
                    chooseThreshold = false
                }
                return true
            }
        })
    }

    private fun setCounterThreshold(threshold: Int){
        val exceedLine = LimitLine(threshold.toFloat(), "Max ${threshold.toInt()}")
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        exceedLine.apply {
            lineColor = Color.RED
            lineWidth = 2f
            textColor = primaryColor
            labelPosition = LimitLine.LimitLabelPosition.LEFT_TOP
        }
        rateLineChart.axisLeft.removeAllLimitLines()
        rateLineChart.axisLeft.addLimitLine(exceedLine)
    }

    private fun setupChartTouchListener() {
        rateLineChart.setOnTouchListener { v, event ->
            // Let gestureDetector process it
            gestureDetector.onTouchEvent(event)
            // Important: allow LineChart to handle other gestures (zoom, drag)
            rateLineChart.onTouchEvent(event)
        }
    }

    private fun setupLineChart() {

        val currentTime = System.currentTimeMillis() / 1000f
        val fiveMinutesInSeconds = 5 * 60

        // Generate initial 0-valued entries for the past 5 minutes
        val initialEntries = mutableListOf<Entry>()
        for (i in (-fiveMinutesInSeconds) until 0) {
            val timestamp = currentTime + i
            initialEntries.add(Entry(i.toFloat(), 0f))
        }

        lineDataSet = LineDataSet(initialEntries, "Counts/sec").apply {
            color = Color.GREEN
            lineWidth = 2f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        val primaryColor = resources.getColor(R.color.colorPrimaryText, null)
        rateLineChart.apply {
            data = LineData(lineDataSet)
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            legend.isEnabled = true
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                textColor = primaryColor
                granularity = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(((currentTime+value) * 1000).toLong()))
                    }
                }
            }
            axisLeft.apply {
                setDrawGridLines(true)
                textColor = primaryColor
                axisMinimum = 0f
                axisMaximum = 1000f
            }
            axisRight.isEnabled = false // Hide right Y-axis
            // Set the description and refresh the chart
            description = Description().apply {
                text = "C/sec vs Time"
                textColor = primaryColor
            }
            legend.textColor = primaryColor
            invalidate()
        }
        setupChartTouchListener()
    }

//    private fun startUpdatingRate() {
//        lifecycleScope.launch {
//            while (true) {
//                val rate = (50..99).random()
//                updateRate(rate)
//                delay(1000)
//            }
//        }
//    }

    private fun updateRate(rate: Int) {
        val newEntry = Entry(lineDataSet.xMax+1, rate.toFloat())
        lineDataSet.removeFirst()
        lineDataSet.addEntry(newEntry)
        // Update the X-axis to reflect the sliding window
        rateLineChart.xAxis.apply {
            axisMinimum = lineDataSet.xMin
            axisMaximum = lineDataSet.xMax
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val timestamp = System.currentTimeMillis() + ((value - lineDataSet.xMax) * 1000).toLong()
                    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }

        // Refresh chart
        rateLineChart.data.notifyDataChanged()
        rateLineChart.notifyDataSetChanged()
        rateLineChart.invalidate()

        // UI updates
        currentRateTextView.text = "$rate"
        currentTimeTextView.text = SimpleDateFormat("YYYY-MM-dd", Locale.getDefault()).format(Date())

        if (rate > threshold) {
            triggerAlert()
        } else {
            resetAlert()
        }
    }

    private fun getTouchPoint(tapY: Float): Double {
        val tapX = rateLineChart.height / 2f
        val transformer = rateLineChart.getTransformer(YAxis.AxisDependency.LEFT)
        val touchPoint = transformer.getValuesByTouchPoint(tapX, tapY)
        val tappedY = touchPoint.y
        return tappedY
    }

    private fun triggerAlert() {
        currentRateTextView.setTextColor(Color.RED)

        if (!isBlinking) {
            isBlinking = true
            alarmJob = lifecycleScope.launch {
                while (isBlinking) {
                    handler.post { currentRateTextView.visibility = View.INVISIBLE }
                    delay(500)
                    handler.post { currentRateTextView.visibility = View.VISIBLE }
                    delay(500)
                }
            }
        }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.severe_warning_alarm)
            mediaPlayer?.isLooping = true
        }
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    private fun resetAlert() {
        currentRateTextView.setTextColor(Color.GREEN)
        isBlinking = false
        alarmJob?.cancel()
        currentRateTextView.visibility = View.VISIBLE

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alarmJob?.cancel()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: IllegalStateException) {
                // MediaPlayer was not in a valid state
                e.printStackTrace()
            } finally {
                it.release()
            }
        }
        mediaPlayer = null
        view?.keepScreenOn = false
    }

    override fun onSave(counterThreshold: Int) {
        threshold = counterThreshold
        setCounterThreshold(threshold)
        saveThreshold()
    }

    override fun onChoose() {
        chooseThreshold = true
    }

    override fun onReconnect() {
        super.onReconnect(CounterFragment(), "counter")
    }
}
