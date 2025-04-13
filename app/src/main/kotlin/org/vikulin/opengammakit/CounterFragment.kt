package org.vikulin.opengammakit

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

import android.media.MediaPlayer
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.formatter.ValueFormatter

class CounterFragment : Fragment() {

    private lateinit var currentRateTextView: TextView
    private lateinit var thresholdEditText: EditText
    private lateinit var setThresholdButton: Button
    private lateinit var currentTimeTextView: TextView
    private lateinit var rateLineChart: LineChart

    private var threshold = 100
    private var isBlinking = false
    private var alarmJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var lineDataSet: LineDataSet


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val entriesArray = lineDataSet.values.map { entry ->
            floatArrayOf(entry.x, entry.y)
        }.toTypedArray()
        outState.putSerializable("GRAPH_ENTRIES", entriesArray)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_counter, container, false)

        currentRateTextView = view.findViewById(R.id.currentRateTextView)
        thresholdEditText = view.findViewById(R.id.thresholdEditText)
        setThresholdButton = view.findViewById(R.id.setThresholdButton)
        currentTimeTextView = view.findViewById(R.id.currentTimeTextView)
        rateLineChart = view.findViewById(R.id.rateLineChart)

        setThresholdButton.setOnClickListener {
            threshold = thresholdEditText.text.toString().toIntOrNull() ?: 100
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

        startUpdatingRate()

        return view
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
            setTouchEnabled(false)
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
    }

    private fun startUpdatingRate() {
        lifecycleScope.launch {
            while (true) {
                val rate = (50..99).random()
                updateRate(rate)
                delay(1000)
            }
        }
    }

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
        currentRateTextView.text = "$rate cps"
        currentTimeTextView.text = SimpleDateFormat("YYYY-mm-dd", Locale.getDefault()).format(Date())

        if (rate > threshold) {
            triggerAlert(rate)
        } else {
            resetAlert()
        }
    }


    private fun triggerAlert(rate: Int) {
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

        val exceedLine = LimitLine(rate.toFloat(), "Exceeded")
        exceedLine.lineColor = Color.RED
        exceedLine.lineWidth = 2f
        rateLineChart.axisLeft.removeAllLimitLines()
        rateLineChart.axisLeft.addLimitLine(exceedLine)
    }

    private fun resetAlert() {
        currentRateTextView.setTextColor(Color.GREEN)
        isBlinking = false
        alarmJob?.cancel()
        currentRateTextView.visibility = View.VISIBLE

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        rateLineChart.axisLeft.removeAllLimitLines()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alarmJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
    }
}
