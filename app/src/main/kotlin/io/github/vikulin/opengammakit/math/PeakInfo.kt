package io.github.vikulin.opengammakit.math

data class PeakInfo(
        val channel: Int,
        val spectrumIndex: Int,
        val snr: Double,
        val intensity: Double,
        val scale: Int
    )