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
    var spectrum: List<Long>,
    var validPulseCount: Long,
) : JavaSerializable
