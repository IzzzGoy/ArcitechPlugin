package io.github.izzzgoy.plugin.generator

import io.github.izzzgoy.plugin.models.ConfigSchema
import com.squareup.kotlinpoet.FileSpec

interface Generator {
    fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec>
}