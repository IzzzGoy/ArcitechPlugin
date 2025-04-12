package com.ndmatrix.plugin.generator

import com.ndmatrix.plugin.models.ConfigSchema
import com.squareup.kotlinpoet.FileSpec

interface Generator {
    fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec>
}