package org.vikulin.opengammakit.model

import kotlinx.serialization.Serializable

// Data class to serialize calibration data
@Serializable
data class CalibrationData(
        val limitLineValue: Float,
        val limitLineLabel: String,
        val channel: Double,
        val emissionSource: EmissionSource
    )
