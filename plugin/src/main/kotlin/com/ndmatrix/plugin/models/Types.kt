package com.ndmatrix.plugin.models

import kotlinx.serialization.Serializable

@Serializable
data class TypeDefinition(
    val name: String,
    val fields: List<PropsDefinition>
)

@Serializable
data class PropsDefinition(
    val name: String,
    val type: String,
    val default: String,
)