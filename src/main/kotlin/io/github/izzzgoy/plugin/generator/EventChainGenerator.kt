package io.github.izzzgoy.plugin.generator

import com.ndmatrix.parameter.EventChain
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import io.github.izzzgoy.plugin.models.ConfigSchema
import io.github.izzzgoy.plugin.models.EventDefinition
import kotlin.coroutines.CoroutineContext

class EventChainGenerator : Generator {
    override fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec> {
        return calculateTree(configSchema.general, configSchema.events).map { (it, chain) ->
            FileSpec.builder(packageName, "${it}Chain")
                .addType(
                    TypeSpec.classBuilder("${it}Chain")
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            EventChain::class.asTypeName()
                                .parameterizedBy(ClassName(packageName, "Event$it"))
                        )
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("coroutineContext", CoroutineContext::class)
                                .addParameters(
                                    chain
                                        .filter { '.' !in it }
                                        .map {
                                            ParameterSpec.builder(
                                                "${it}Handler",
                                                ClassName(packageName, "Event${it}Handler")
                                            ).build()
                                        }
                                )
                                .addParameters(
                                    chain
                                        .filter { '.' in it }
                                        .map {
                                            it.split(".")[0]
                                        }
                                        .map {
                                            ParameterSpec.builder(
                                                "${it}ParameterHolder",
                                                ClassName(packageName, "${it}ParameterHolder")
                                            ).build()
                                        }
                                )
                                .addParameter(
                                    ParameterSpec.builder("isDebug", Boolean::class)
                                        .defaultValue("true").build()
                                )
                                .build()
                        )
                        .addSuperclassConstructorParameter("coroutineContext = coroutineContext")
                        .addSuperclassConstructorParameter("isDebug = isDebug")
                        .addSuperclassConstructorParameter("intentsHandlers = listOf(${
                            chain
                                .filter { '.' in it }
                                .joinToString(", ") {
                                    it.split(".")[0] + "ParameterHolder"
                                }
                        })"
                        )
                        .addSuperclassConstructorParameter(
                            "eventsSender = listOf(${
                                chain
                                    .filter { '.' !in it }
                                    .joinToString(", ") {
                                        "${it}Handler"
                                    }
                            })"
                        )
                        .build()
                )
                .build()
        }
    }

    private fun calculateTree(general: List<String>, events: Map<String, EventDefinition>): Map<String, List<String>> {

        return general.associateWith { root ->

            val aggregator = mutableListOf<String>()
            fun visit(node: String) {
                aggregator.add(node)
                (events[node]?.returns ?: emptyList()).forEach {
                    visit(it.name)
                }
            }
            visit(root)
            aggregator

        }.mapValues {
            it.value.distinct()
        }
    }
}