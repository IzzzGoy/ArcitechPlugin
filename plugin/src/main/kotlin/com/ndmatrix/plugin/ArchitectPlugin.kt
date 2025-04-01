package com.ndmatrix.plugin

import com.ndmatrix.plugin.generator.ParametersGenerator
import com.ndmatrix.plugin.models.ConfigSchema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class GenerateExtension {
    @get:Input
    abstract val packageName: Property<String>

}

class ArchitectPlugin : Plugin<Project> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun apply(project: Project) {
        val extension = project.extensions.create("architect", GenerateExtension::class.java)

        project.tasks.create("generate") {
            doLast {
                val configFolder = arrayOf(
                    project.layout.projectDirectory.asFile.toPath() / "src/commonMain/config",
                    project.layout.projectDirectory.asFile.toPath() / "src/main/config"
                ).firstOrNull { it.exists() }
                if (configFolder == null) {
                    return@doLast
                }
                val configSchema = configFolder.toFile().listFiles()?.first { it.extension == "json" } ?: return@doLast
                val decodedSchema = Json.decodeFromStream<ConfigSchema>(configSchema.inputStream())
                ParametersGenerator().generate(decodedSchema, extension.packageName.get()).forEach {
                    it.writeTo((project.layout.buildDirectory.asFile.get().toPath() / "generated/architect"))
                }
            }
        }
    }
}