package io.github.vikulin.opengammakit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.*

import android.media.MediaPlayer
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import io.github.vikulin.opengammakit.model.OpenGammaKitCommands
import io.github.vikulin.opengammakit.view.CounterThresholdDialogFragment
import io.github.vikulin.opengammakit.viewmodel.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory

class CounterFragment : SerialConnectionFragment(),
    CounterThresholdDialogFragment.SaveThresholdDialogListener,
    CounterThresholdDialogFragment.ChooseThresholdDialogListener{

    private lateinit var currentRateTextView: TextView
    private lateinit var btnThreshold: ImageButton
    private lateinit var btnMap: ImageButton
    private lateinit var currentTimeTextView: TextView
    private lateinit var rateLineChart: LineChart
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var mapViewModel: MapViewModel
    private lateinit var googleMap: GoogleMap

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
    private var mapType = -1

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        loadThreshold()
        setCounterThreshold(threshold)
        super.setDtr(true)
        val command = OpenGammaKitCommands().setOut("events").toByteArray()
        super.send(command)
    }

    override fun onConnectionFailed() {
        super.onConnectionFailed()
        // Add a message here
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

    override fun receive(bytes: ByteArray) {
        val message = String(bytes).trim()
        val regex = """\[(\d+)]""".toRegex()
        val match = regex.find(message)

        match?.groups?.get(1)?.value?.toIntOrNull()?.let { count ->
            requireActivity().runOnUiThread {
                updateRate(count)
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
        outState.putInt("ENABLE_MAP_VIEW", mapType)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.getInt("COUNTER_THRESHOLD")?.let {
            threshold = it
        } ?: run { }
        savedInstanceState?.getInt("ENABLE_MAP_VIEW")?.let {
            mapType = it
        } ?: run { }
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
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_counter, container, false)

        currentRateTextView = view.findViewById(R.id.currentRateTextView)
        btnThreshold = view.findViewById(R.id.btnThreshold)
        btnMap = view.findViewById(R.id.btnMap)
        currentTimeTextView = view.findViewById(R.id.currentTimeTextView)
        rateLineChart = view.findViewById(R.id.rateLineChart)

        btnThreshold.setOnClickListener {
            //set limit line for the threshold
            val counterThresholdDialog = CounterThresholdDialogFragment.Companion.newInstance(threshold)
            counterThresholdDialog.show(childFragmentManager, "counter_threshold_dialog_fragment")
        }

        btnMap.setOnClickListener {
            when(mapType){
                GoogleMap.MAP_TYPE_SATELLITE -> {
                    mapType = GoogleMap.MAP_TYPE_TERRAIN
                    activateLocationAndShowMap()
                }
                GoogleMap.MAP_TYPE_TERRAIN -> {
                    mapType = -1
                    deactivateLocationAndHideMap()
                }
                -1 -> {
                    mapType = GoogleMap.MAP_TYPE_SATELLITE
                    activateLocationAndShowMap()
                }
            }

        }

        setupLineChart()

        return view
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.keepScreenOn = true

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (chooseThreshold) {
                    val touchPoint = getTouchPoint(e.y)
                    threshold = touchPoint.toInt()
                    setCounterThreshold(threshold)
                    saveThreshold()
                    chooseThreshold = false
                }
                return true
            }
        })

        mapViewModel = ViewModelProvider(this)[MapViewModel::class.java]
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        if (mapType > -1) {
            activateLocationAndShowMap()
        } else {
            deactivateLocationAndHideMap()
        }
    }

    private fun startLocationUpdates() {
        if (!::fusedLocationClient.isInitialized) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).setMinUpdateIntervalMillis(5000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val latLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun activateLocationAndShowMap() {
        mapView.getMapAsync { googleMap ->
            this@CounterFragment.googleMap = googleMap

            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.mapType = mapType

            if (hasLocationPermission()) {
                googleMap.isMyLocationEnabled = true
                mapViewModel.cameraPosition?.let {
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(it))
                }
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        mapView.visibility = View.VISIBLE
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun deactivateLocationAndHideMap() {
        stopLocationUpdates()
        mapView.visibility = View.GONE
    }

    private fun hasLocationPermission(): Boolean {
        val context = requireContext()
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            googleMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
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
        rateLineChart.invalidate()
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

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::googleMap.isInitialized) {
            mapViewModel.cameraPosition = googleMap.cameraPosition
        }
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
