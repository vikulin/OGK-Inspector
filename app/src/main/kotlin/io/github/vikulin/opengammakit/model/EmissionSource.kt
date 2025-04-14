package io.github.vikulin.opengammakit.model

import kotlinx.serialization.Serializable

@Serializable
data class EmissionSource(val name: String, val energy: Double)