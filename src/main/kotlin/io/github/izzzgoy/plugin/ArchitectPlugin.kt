package io.github.izzzgoy.plugin

import io.github.izzzgoy.plugin.generator.GeneratorCommon
import io.github.izzzgoy.plugin.generator.ProjectionGenerator
import io.github.izzzgoy.plugin.generator.TypesGenerator
import io.github.izzzgoy.plugin.models.ConfigSchema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


abstract class GenerateExtension {
    @get:Input
    abstract val packageName: Property<String>
}

class ArchitectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("architect", GenerateExtension::class.java)

        project.tasks.register("generate", GenerateTask::class.java) {
            this.packageName.set(extension.packageName)
            this.configDirectory.set(
                project.layout.projectDirectory.dir("src/commonMain/config")
            )
            this.generatedOutputDir.set(
                project.layout.buildDirectory.dir("generated/architect")
            )
        }
    }
}

abstract class GenerateTask : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedOutputDir: DirectoryProperty

    @OptIn(ExperimentalSerializationApi::class)
    @TaskAction
    fun generateFiles() {
        val configFolder = configDirectory.get().asFile
        configFolder.listFiles()?.filter { it.extension == "json" }
            ?.onEach { configSchema ->
                logger.lifecycle("process: -> ${configSchema.name}")

                val decodedSchema = Json.decodeFromStream<ConfigSchema>(configSchema.inputStream())
                val outputDir = generatedOutputDir.get().asFile.toPath()

                GeneratorCommon().generate(decodedSchema, packageName.get() + ".${configSchema.name.lowercase()}").forEach {
                    it.writeTo(outputDir)
                }
                TypesGenerator().generate(decodedSchema, packageName.get() + ".${configSchema.name.lowercase()}").forEach {
                    it.writeTo(outputDir)
                }
                ProjectionGenerator().generate(decodedSchema, packageName.get() + ".${configSchema.name.lowercase()}").forEach {
                    it.writeTo(outputDir)
                }
            }
    }
}
