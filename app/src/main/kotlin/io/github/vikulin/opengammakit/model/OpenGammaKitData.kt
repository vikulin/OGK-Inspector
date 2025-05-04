package io.github.vikulin.opengammakit.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable
data class OpenGammaKitData(
    val schemaVersion: String,
    var data: MutableList<GammaKitEntry>,
    var derivedSpectra: MutableMap<Int, DerivedSpectrumEntry> = mutableMapOf()
) : JavaSerializable

@Serializable
data class DerivedSpectrumEntry(
    val name: String, // e.g., "Subtraction - Background"
    val resultSpectrum: List<Double>,
    val modifiers: MutableList<ModifierInfo> = mutableListOf(), // transformation chain
    var peaks: MutableList<PeakInfo> = mutableListOf<PeakInfo>() // detected peaks
) : JavaSerializable

@Serializable
data class ModifierInfo(
    val modifierName: String, // e.g., "Subtraction", "Smoothing"
    val inputIndexes: List<Int> // points to raw spectra in OpenGammaKitData.data
) : JavaSerializable

@Serializable
data class PeakInfo(
    val channel: Int,
    val snr: Double,
    val intensity: Double,
    val scale: Int
) : JavaSerializable

@Serializable
data class GammaKitEntry(
    val deviceData: DeviceData,
    val resultData: ResultData
) : JavaSerializable

@Serializable
data class DeviceData(
    val softwareName: String,
    val deviceName: String
) : JavaSerializable

@Serializable
data class ResultData(
    val energySpectrum: EnergySpectrum
) : JavaSerializable

@Serializable
data class EnergySpectrum(
    var numberOfChannels: Int,
    var measurementTime: Long,
    var spectrum: List<Long>, // raw integer counts (original measurement)
    var validPulseCount: Long
) : JavaSerializable
