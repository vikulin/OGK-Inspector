package io.github.vikulin.opengammakit.model

import java.io.Serializable

@kotlinx.serialization.Serializable
data class CalibrationData(
    val limitLineValue: Float,
    val limitLineLabel: String,
    val channel: Double,
    val emissionSource: EmissionSource
) : Serializable
