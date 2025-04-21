package io.github.vikulin.opengammakit.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import io.github.vikulin.opengammakit.R

class SpectrumRecordingTimeDialogFragment() : DialogFragment() {

    private var recordingTime: Int = 0
    private lateinit var editHours: EditText
    private lateinit var editMinutes: EditText
    private lateinit var editSeconds: EditText
    private lateinit var btnIncrementHours: ImageButton
    private lateinit var btnDecrementHours: ImageButton
    private lateinit var btnIncrementMinutes: ImageButton
    private lateinit var btnDecrementMinutes: ImageButton
    private lateinit var btnIncrementSeconds: ImageButton
    private lateinit var btnDecrementSeconds: ImageButton

    companion object {
        private const val SPECTRUM_RECORDING_TIME = "spectrum_recording_time"

        fun newInstance(recordingTime: Int): SpectrumRecordingTimeDialogFragment {
            val fragment = SpectrumRecordingTimeDialogFragment()
            val args = Bundle()
            args.putInt(SPECTRUM_RECORDING_TIME, recordingTime)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Try to attach the listener to the host activity or parent fragment
        chooseSpectrumRecordingTime = when {
            parentFragment is ChooseSpectrumRecordingTimeDialogListener -> parentFragment as ChooseSpectrumRecordingTimeDialogListener
            context is ChooseSpectrumRecordingTimeDialogListener -> context
            else -> throw IllegalStateException("Host must implement ChooseSpectrumRecordingTimeDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        chooseSpectrumRecordingTime = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_spectrum_recording_time, container, false)

        // Initialize fields
        editHours = view.findViewById(R.id.editHours)
        editMinutes = view.findViewById(R.id.editMinutes)
        editSeconds = view.findViewById(R.id.editSeconds)

        btnIncrementHours = view.findViewById(R.id.btnIncrementHours)
        btnDecrementHours = view.findViewById(R.id.btnDecrementHours)
        btnIncrementMinutes = view.findViewById(R.id.btnIncrementMinutes)
        btnDecrementMinutes = view.findViewById(R.id.btnDecrementMinutes)
        btnIncrementSeconds = view.findViewById(R.id.btnIncrementSeconds)
        btnDecrementSeconds = view.findViewById(R.id.btnDecrementSeconds)

        recordingTime = arguments?.getInt(SPECTRUM_RECORDING_TIME) ?: 0

        convertRecordingTime(recordingTime)
        // Set click listeners
        setupListeners()

        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnStart = view.findViewById<Button>(R.id.btnStart)

        // Set up the close button
        btnCancel.setOnClickListener {
            dismiss() // Close the dialog
        }
        btnStart.setOnClickListener {
            val recordingTime = convertFieldsToRecordingTime()
            if(recordingTime < 1 || recordingTime > 259_200){
                editMinutes.error = "Recording time must be between 1 and 259,200 seconds"
            } else {
                chooseSpectrumRecordingTime?.onSpectrumRecordingTime(recordingTime)
                dismiss()
            }
        }

        return view
    }

    private fun convertFieldsToRecordingTime(): Long {
        val hours = editHours.text.toString().toLongOrNull() ?: 0
        val minutes = editMinutes.text.toString().toLongOrNull() ?: 0
        val seconds = editSeconds.text.toString().toLongOrNull() ?: 0

        return (hours * 3600) + (minutes * 60) + seconds
    }

    private fun convertRecordingTime(recordingTime: Int) {
        val hours = recordingTime / 3600
        val minutes = (recordingTime % 3600) / 60
        val seconds = recordingTime % 60

        assignConvertedValues(hours, minutes, seconds)
    }

    private fun assignConvertedValues(hours: Int, minutes: Int, seconds: Int) {
        editHours.setText(hours.toString())
        editMinutes.setText(minutes.toString())
        editSeconds.setText(seconds.toString())
    }

    private fun setupListeners() {
        btnIncrementHours.setOnClickListener { updateValue(editHours, 1) }
        btnDecrementHours.setOnClickListener { updateValue(editHours, -1) }

        btnIncrementMinutes.setOnClickListener { updateValue(editMinutes, 1) }
        btnDecrementMinutes.setOnClickListener { updateValue(editMinutes, -1) }

        btnIncrementSeconds.setOnClickListener { updateValue(editSeconds, 1) }
        btnDecrementSeconds.setOnClickListener { updateValue(editSeconds, -1) }
    }

    private fun updateValue(editText: EditText, delta: Int) {
        val currentValue = editText.text.toString().toIntOrNull() ?: 0
        val newValue = maxOf(0, currentValue + delta)  // Ensure value never goes below 0
        editText.setText(newValue.toString())
    }

    // Define the callback interface
    interface ChooseSpectrumRecordingTimeDialogListener {
        fun onSpectrumRecordingTime(time: Long)
    }

    private var chooseSpectrumRecordingTime: ChooseSpectrumRecordingTimeDialogListener? = null
}