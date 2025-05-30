package io.github.izzzgoy.plugin.generator

import com.ndmatrix.parameter.Projection
import io.github.izzzgoy.plugin.models.ConfigSchema
import io.github.izzzgoy.plugin.models.ProjectionSourceType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import io.github.izzzgoy.plugin.utils.castType
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class ProjectionGenerator : Generator {
    override fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec> {
        return configSchema.projection.map { projection ->
            FileSpec.builder(packageName, "Projection${projection.name}")
                .addType(
                    TypeSpec.classBuilder("Projection${projection.name}")
                        .addModifiers(KModifier.ABSTRACT)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameters(
                                    projection.sources.map { parameter ->
                                        ParameterSpec.builder(
                                            parameter.name.replaceFirstChar { it.lowercase(Locale.getDefault()) },
                                            when (parameter.type) {
                                                ProjectionSourceType.Param -> {
                                                    ClassName(
                                                        packageName,
                                                        "${parameter.name}ParameterHolder"
                                                    )
                                                }

                                                ProjectionSourceType.Projection -> {
                                                    ClassName(
                                                        packageName,
                                                        "Projection${parameter.name}"
                                                    )
                                                }
                                            }
                                        ).build()
                                    }
                                ).addParameter("coroutineContext", CoroutineContext::class)
                                .apply {
                                    if (projection.initial == null) {
                                        addParameter(
                                            ParameterSpec.builder(
                                                "initialValue",
                                                configSchema.types.find { it.name == projection.type }?.let {
                                                    ClassName(packageName, it.name).copy(nullable = projection.nullable)
                                                } ?: castType(projection.type).copy(nullable = projection.nullable)
                                            ).build()
                                        )
                                    }
                                }
                                .build()
                        )
                        .superclass(
                            Projection::class.asClassName().parameterizedBy(
                                castType(projection.type).copy(nullable = projection.nullable)
                            )
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "flow",
                                StateFlow::class.asTypeName().parameterizedBy(
                                    castType(projection.type).copy(nullable = projection.nullable)
                                )
                            )
                                .addModifiers(KModifier.OVERRIDE)
                                .initializer(
                                    CodeBlock.builder()
                                        .also {
                                            if (projection.sources.size == 1) {
                                                val source = projection.sources.first()
                                                it.addStatement(
                                                    "${
                                                        source.name.replaceFirstChar {
                                                            it.lowercase(
                                                                Locale.getDefault()
                                                            )
                                                        }
                                                    }.flow"
                                                ).addStatement(
                                                    ".%M { t0 ->",
                                                    MemberName("kotlinx.coroutines.flow", "map")
                                                )
                                            } else {
                                                it.addStatement(
                                                    "%M(",
                                                    MemberName("kotlinx.coroutines.flow", "combine")
                                                )
                                                    .indent()
                                                    .addStatement(
                                                        projection.sources.joinToString(", ") {
                                                            "${
                                                                it.name.replaceFirstChar {
                                                                    it.lowercase(
                                                                        Locale.getDefault()
                                                                    )
                                                                }
                                                            }.flow"
                                                        }
                                                    )
                                                    .unindent()
                                                    .addStatement(
                                                        ") { ${
                                                            projection.sources.indices.joinToString(
                                                                separator = ", "
                                                            ) { "t$it" }
                                                        } ->"
                                                    )
                                            }
                                        }


                                        .indent()
                                        .addStatement(
                                            "project(${
                                                projection.sources.indices.joinToString(
                                                    separator = ", "
                                                ) { "t$it" }
                                            })"
                                        )
                                        .unindent()
                                        .addStatement("}")
                                        .addStatement(
                                            ".%M(",
                                            MemberName("kotlinx.coroutines.flow", "stateIn")
                                        )
                                        .indent()
                                        .addStatement(
                                            "initialValue = ${
                                                if (projection.initial == null) {
                                                    "initialValue"
                                                } else {
                                                    if (projection.type == "string") {
                                                        "\"${projection.initial}\""
                                                    } else {
                                                        projection.initial
                                                    }

                                                }
                                            },"
                                        )
                                        .addStatement(
                                            "started = %M.Eagerly,",
                                            MemberName("kotlinx.coroutines.flow", "SharingStarted")
                                        )
                                        .addStatement(
                                            "scope = %M(coroutineContext),",
                                            MemberName("kotlinx.coroutines", "CoroutineScope"),
                                        )
                                        .unindent()
                                        .addStatement(")")
                                        .build()
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("project")
                                .addModifiers(KModifier.ABSTRACT)
                                .addParameters(
                                    projection.sources.map { parameter ->
                                        ParameterSpec.builder(
                                            parameter.name.replaceFirstChar { it.lowercase(Locale.getDefault()) },
                                            when (parameter.type) {
                                                ProjectionSourceType.Param -> {
                                                    castType(
                                                        configSchema.parameters[parameter.name]!!.type
                                                    )
                                                }

                                                ProjectionSourceType.Projection -> {
                                                    castType(
                                                        configSchema.projection.find { it.name == parameter.name }!!.type
                                                    )
                                                }
                                            }
                                        ).build()
                                    }
                                )
                                .returns(castType(projection.type).copy(nullable = projection.nullable))
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "value",
                                castType(projection.type).copy(nullable = projection.nullable)
                            )
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addCode("return flow.value")
                                        .build()
                                )
                                .addModifiers(KModifier.OVERRIDE)
                                .build()
                        )
                        .build()
                )
                .build()
        }
    }


}