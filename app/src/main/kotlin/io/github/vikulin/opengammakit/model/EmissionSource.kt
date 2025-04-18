package io.github.vikulin.opengammakit.model

import java.io.Serializable

@kotlinx.serialization.Serializable
data class EmissionSource(val name: String, val energy: Double): Serializable