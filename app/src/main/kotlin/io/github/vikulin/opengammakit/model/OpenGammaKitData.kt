package io.github.vikulin.opengammakit.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable
data class OpenGammaKitData(
    val schemaVersion: String,
    var data: MutableList<GammaKitEntry>
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
    var validPulseCount: Long,
    var outputSpectrum: MutableList<Double> = mutableListOf<Double>(), // transformed data for display
    var filters: MutableList<String> = mutableListOf<String>(), // list of applied filters (like "LogScale")
    var peaks: MutableList<PeakInfo> = mutableListOf<PeakInfo>() // detected peaks
) : JavaSerializable

@Serializable
data class PeakInfo(
    val channel: Int,
    val snr: Double,
    val intensity: Double,
    val scale: Int
) : JavaSerializable
