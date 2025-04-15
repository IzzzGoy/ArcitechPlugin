package io.github.izzzgoy.plugin.models

import kotlinx.serialization.Serializable

@Serializable
data class ConfigSchema(
    val parameters: Map<String, ParametersDefinition> = emptyMap(),
    val projection: List<ProjectionDefinition> = emptyList(),
    val events: Map<String, EventDefinition> = emptyMap(),
    val general: List<String> = emptyList(),
    val types: List<TypeDefinition> = emptyList()
)

@Serializable
data class ParametersDefinition(
    val type: String,
    val initial: String?,
    val intents: Map<String, IntentsDefinition>,
)

@Serializable
data class IntentsDefinition(
    val args: Map<String, ArgDefinition>?
)

@Serializable
data class ArgDefinition(
    val type: String,
)

@Serializable
data class ProjectionDefinition(
    val name: String,
    val type: String,
    val initial: String? = null,
    val sources: List<ProjectionSource>
)

@Serializable
data class ProjectionSource(
    val type: ProjectionSourceType,
    val name: String,
)

@Serializable
enum class ProjectionSourceType {
    Param, Projection
}

@Serializable
data class EventDefinition(
    val args: Map<String, ArgDefinition>?,
    val returns: List<ReturnsDefinition>
)

@Serializable
data class ReturnsDefinition(
    val type: EventReturnsType,
    val name: String
)

@Serializable
enum class EventReturnsType {
    Param, Event
}