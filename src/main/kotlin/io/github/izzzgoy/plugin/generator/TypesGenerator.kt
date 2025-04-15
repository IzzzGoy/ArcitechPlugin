package io.github.izzzgoy.plugin.generator

import io.github.izzzgoy.plugin.models.ConfigSchema
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

class TypesGenerator : Generator {
    override fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec> {
        return listOf(
            FileSpec
                .builder(packageName, "Types")
                .addTypes(
                    configSchema.types.map { type ->
                        TypeSpec.classBuilder(type.name)
                            .addModifiers(KModifier.DATA)
                            .primaryConstructor(
                                FunSpec.constructorBuilder()
                                    .addParameters(
                                        type.fields.map { field ->
                                            ParameterSpec.builder(
                                                field.name,
                                                castType(field.type)
                                            )
                                                .defaultValue(
                                                    if (field.type == "string") {
                                                        "\"${field.default}\""
                                                    } else {
                                                        field.default
                                                    }
                                                )
                                                .build()
                                        }
                                    )
                                    .build()
                            )
                            .addProperties(
                                type.fields.map { field ->
                                    PropertySpec.builder(field.name, castType(field.type))
                                        .initializer(field.name)
                                        .build()
                                }
                            )
                            .build()
                    }
                )
                .build()
        )
    }
}


