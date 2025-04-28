package io.github.vikulin.opengammakit.math

import io.github.vikulin.opengammakit.model.GammaKitEntry
import io.github.vikulin.opengammakit.model.OpenGammaKitData
import io.github.vikulin.opengammakit.model.PeakInfo
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.LUDecomposition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

object SpectrumModifier {

    private fun savitzkyGolay(
        y: DoubleArray,
        windowSize: Int,
        polyOrder: Int
    ): DoubleArray {
        require(windowSize % 2 == 1) { "Window size must be odd." }
        require(polyOrder < windowSize) { "Polynomial order must be less than window size." }

        val halfWin = windowSize / 2

        // Build Vandermonde matrix for window
        val vandermonde = Array(windowSize) { i ->
            DoubleArray(polyOrder + 1) { j -> (i - halfWin).toDouble().pow(j) }
        }

        val A = Array2DRowRealMatrix(vandermonde)
        val ATA = A.transpose().multiply(A)
        val ATAInv = LUDecomposition(ATA).solver.inverse
        val pseudoinv = ATAInv.multiply(A.transpose())
        val coeffs = pseudoinv.getRow(0) // smoothing coefficients

        // Mirror padding
        val padded = DoubleArray(y.size + 2 * halfWin)
        for (i in 0 until halfWin) {
            padded[i] = y[halfWin - i]             // start mirror
            padded[padded.size - 1 - i] = y[y.size - 2 - i] // end mirror
        }
        for (i in y.indices) {
            padded[i + halfWin] = y[i]
        }

        // Apply convolution
        val result = DoubleArray(y.size)
        for (i in y.indices) {
            var acc = 0.0
            for (j in 0 until windowSize) {
                acc += padded[i + j] * coeffs[j]
            }
            result[i] = acc
        }

        return result
    }

    fun applySavitzkyGolayFilter(entry: GammaKitEntry) {
            val spectrum = entry.resultData.energySpectrum.spectrum.map { it.toDouble() }
            if (spectrum.size < 31) return // Too small for filtering
            val smoothed = savitzkyGolay(spectrum.toDoubleArray(), windowSize = 31, polyOrder = 3)
            entry.resultData.energySpectrum.outputSpectrum = smoothed.map { it }.toMutableList()
    }

    // Public method to detect peaks in selected spectrums of a dataset
    fun detectCWTPeaks(
        dataSet: OpenGammaKitData,
        indexesToAnalyze: List<Int>,
        estimateBaseline: Boolean = false,
        waveletWidths: IntRange = 1..10,
        minSignalToNoise: Double = 3.0,
        peakProximityThreshold: Int = 3
    ): MutableList<PeakInfo> {
        val detectedPeaks = mutableListOf<PeakInfo>()

        for (spectrumIndex in indexesToAnalyze) {
            val spectrum = dataSet.data[spectrumIndex].resultData.energySpectrum.outputSpectrum.map { it.toDouble() }
            val correctedSpectrum = if (estimateBaseline) removeBaseline(spectrum) else spectrum

            val waveletWidthList = waveletWidths.toList()
            val cwtMatrix = Array(waveletWidthList.size) { DoubleArray(correctedSpectrum.size) }

            // Generate wavelet transforms
            for ((scaleIndex, width) in waveletWidthList.withIndex()) {
                val wavelet = rickerWavelet(2 * width + 1, width.toDouble())

                // Normalize wavelet
                val norm = sqrt(wavelet.sumOf { it * it })
                for (i in wavelet.indices) wavelet[i] /= norm

                cwtMatrix[scaleIndex] = convolve(correctedSpectrum, wavelet)
            }

            // Compute the maximum CWT response across all scales for each position
            val maxAcrossScales = DoubleArray(correctedSpectrum.size) { i ->
                waveletWidthList.indices.maxOfOrNull { k -> abs(cwtMatrix[k][i]) } ?: 0.0
            }

            // Estimate noise (standard deviation of the first 10% of the spectrum)
            val noiseRegion = maxAcrossScales.take((0.1 * maxAcrossScales.size).toInt())
            val noiseStdDev = sqrt(noiseRegion.map { it * it }.average())

            // Find local maxima and group based on proximity and scale
            val localMaxima = mutableListOf<PeakInfo>()
            for (i in 1 until maxAcrossScales.lastIndex) {
                val snr = maxAcrossScales[i] / noiseStdDev
                if (isLocalMax(maxAcrossScales, i) && snr > minSignalToNoise) {
                    // Track each peak with its channel, scale, and intensity
                    localMaxima.add(PeakInfo(i, snr, maxAcrossScales[i], getPeakScale(cwtMatrix, i)))
                }
            }

            // Group peaks by proximity (channel-based) and return one peak per group
            val groupedPeaks = groupPeaksByProximity(localMaxima, peakProximityThreshold)

            detectedPeaks.addAll(groupedPeaks)
            dataSet.data[spectrumIndex].resultData.energySpectrum.peaks.addAll(groupedPeaks)
        }

        return detectedPeaks
    }

    // Group peaks by proximity (within a given number of channels)
    private fun groupPeaksByProximity(peaks: List<PeakInfo>, proximityThreshold: Int): List<PeakInfo> {
        val groupedPeaks = mutableListOf<PeakInfo>()
        val sortedPeaks = peaks.sortedBy { it.channel }

        var currentGroup = mutableListOf<PeakInfo>()
        for (i in sortedPeaks.indices) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(sortedPeaks[i])
            } else {
                val lastPeak = currentGroup.last()
                if (abs(lastPeak.channel - sortedPeaks[i].channel) <= proximityThreshold) {
                    currentGroup.add(sortedPeaks[i])
                } else {
                    // Add grouped peak (based on maximum intensity in group)
                    val peakWithMaxIntensity = currentGroup.maxByOrNull { it.intensity }
                    groupedPeaks.add(peakWithMaxIntensity!!)
                    currentGroup = mutableListOf(sortedPeaks[i])
                }
            }
        }
        // Add any remaining peaks
        if (currentGroup.isNotEmpty()) {
            val peakWithMaxIntensity = currentGroup.maxByOrNull { it.intensity }
            groupedPeaks.add(peakWithMaxIntensity!!)
        }

        return groupedPeaks
    }

    // Helper to determine the scale of the peak (which wavelet width provided the max response)
    private fun getPeakScale(cwtMatrix: Array<DoubleArray>, peakChannel: Int): Int {
        val maxScaleIndex = cwtMatrix.indices.maxByOrNull { abs(cwtMatrix[it][peakChannel]) }
        return maxScaleIndex ?: 0
    }

    // Optional baseline removal method using simple rolling median subtraction
    private fun removeBaseline(spectrum: List<Double>, windowSize: Int = 25): List<Double> {
        val halfWin = windowSize / 2
        return spectrum.mapIndexed { i, value ->
            val window = spectrum.subList(
                maxOf(0, i - halfWin),
                minOf(spectrum.size, i + halfWin + 1)
            )
            value - window.sorted()[window.size / 2]
        }
    }

    // Ricker (Mexican Hat) wavelet
    private fun rickerWavelet(length: Int, a: Double): DoubleArray {
        val wavelet = DoubleArray(length)
        val half = length / 2
        val factor = 2.0 / (sqrt(3.0 * a) * cbrt(PI))
        for (i in 0 until length) {
            val t = (i - half).toDouble()
            val valExp = exp(-t * t / (2 * a * a))
            wavelet[i] = factor * (1 - t * t / (a * a)) * valExp
        }
        return wavelet
    }

    // Convolution (mirror padding at boundaries)
    private fun convolve(signal: List<Double>, kernel: DoubleArray): DoubleArray {
        val half = kernel.size / 2
        return DoubleArray(signal.size) { i ->
            var sum = 0.0
            for (k in kernel.indices) {
                val j = i + k - half
                val v = when {
                    j < 0 -> signal[0]
                    j >= signal.size -> signal.last()
                    else -> signal[j]
                }
                sum += kernel[k] * v
            }
            sum
        }
    }

    // Detect if point is a local maximum
    private fun isLocalMax(values: DoubleArray, i: Int): Boolean {
        return (values[i] > values[i - 1]) && (values[i] > values[i + 1])
    }

    fun smartPeakDetect(
        dataSet: OpenGammaKitData,
        indexesToAnalyze: List<Int>){
        val peaksWithEstimatedBaseline = detectCWTPeaks(dataSet, indexesToAnalyze, estimateBaseline = true, minSignalToNoise=3.0)
        if(peaksWithEstimatedBaseline.isEmpty()){
            detectCWTPeaks(dataSet, indexesToAnalyze, estimateBaseline = false, minSignalToNoise=2.0)
        }
    }
}
