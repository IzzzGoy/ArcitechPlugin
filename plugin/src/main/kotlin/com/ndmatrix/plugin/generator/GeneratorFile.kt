package com.ndmatrix.plugin.generator

import com.ndmatrix.parameter.AbstractEventHandler
import com.ndmatrix.parameter.EventChain
import com.ndmatrix.parameter.Message
import com.ndmatrix.parameter.PostExecMetadata
import com.ndmatrix.parameter.Projection
import com.ndmatrix.plugin.models.ConfigSchema
import com.ndmatrix.plugin.models.EventDefinition
import com.ndmatrix.plugin.models.ProjectionSource
import com.ndmatrix.plugin.models.ProjectionSourceType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.io.path.fileVisitor

class GeneratorCommon {
    private val parameterGenerator = ParameterGenerator()
    fun generate(config: ConfigSchema, packageName: String): List<FileSpec> {

        println(buildEventChains(config))
        println(extractOrderedEvents(config))
        println(calculateTree(config.general, config.events))
        return parameterGenerator.generate(config, packageName) + config.events.map { (eventName, definition) ->
            FileSpec.builder(
                packageName, "Event$eventName"
            )
                .addType(
                    if (definition.args == null) {
                        TypeSpec.objectBuilder(
                            "Event$eventName",
                        )
                            .addSuperinterface(Message.Event::class)
                    } else {
                        TypeSpec.classBuilder(
                            "Event$eventName",
                        )
                            .addSuperinterface(Message.Event::class)
                            .primaryConstructor(
                                FunSpec.constructorBuilder()
                                    .addParameters(
                                        definition.args.map { (paramName, def) ->
                                            ParameterSpec.builder(
                                                paramName,
                                                castType(def.type)
                                            )
                                                .build()
                                        }
                                    )
                                    .build()
                            )
                            .addProperties(
                                definition.args.map { (paramName, def) ->
                                    PropertySpec.builder(paramName, castType(def.type))
                                        .initializer(paramName)
                                        .build()
                                }
                            )
                    }
                        .addModifiers(KModifier.DATA)
                        .build()
                )
                .addType(
                    TypeSpec.classBuilder(
                        "Event${eventName}Handler"
                    )
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            AbstractEventHandler::class.asTypeName()
                                .parameterizedBy(ClassName(packageName, "Event${eventName}"))
                        )
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter(
                                    ParameterSpec.builder(
                                        "coroutineContext",
                                        CoroutineContext::class
                                    ).build()
                                ).build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "_postMetadata",
                                MutableSharedFlow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "Event$eventName")
                                            )
                                    )
                            )
                                .initializer("MutableSharedFlow()")
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "postMetadata",
                                Flow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "Event$eventName")
                                            )
                                    )
                            ).addModifiers(KModifier.OVERRIDE)
                                .initializer("_postMetadata")
                                .build()
                        )
                        .addSuperclassConstructorParameter("coroutineContext")
                        .addFunction(
                            FunSpec.builder("process")
                                .addAnnotation(ClassName("kotlin.uuid", "ExperimentalUuidApi"))
                                .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
                                .addParameter(
                                    ParameterSpec.builder("e", Message::class)
                                        .build()
                                )
                                .addCode(
                                    CodeBlock.builder()
                                        .beginControlFlow("if (e is Event${eventName})")
                                        .addStatement(
                                            "%M {",
                                            MemberName("kotlin.time", "measureTime")
                                        )
                                        .indent()
                                        .addStatement("handle(e)")
                                        .unindent()
                                        .addStatement("}.also {")
                                        .indent()
                                        .addStatement(
                                            "_postMetadata.emit(PostExecMetadata(e, it, %M[%M.CallMetadataKey]!!.parentId, %M[%M.CallMetadataKey]!!.currentId))",
                                            MemberName(
                                                "kotlin.coroutines",
                                                "coroutineContext"
                                            ),
                                            MemberName(
                                                "com.ndmatrix.parameter",
                                                "CallMetadata"
                                            ),
                                            MemberName(
                                                "kotlin.coroutines",
                                                "coroutineContext"
                                            ),
                                            MemberName(
                                                "com.ndmatrix.parameter",
                                                "CallMetadata"
                                            ),
                                        )
                                        .unindent()
                                        .addStatement("}")
                                        .endControlFlow()
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        } + calculateTree(config.general, config.events).map { (it, chain) ->
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



    private fun extractDefaultValue(
        it: ProjectionSource,
        config: ConfigSchema
    ) = if (it.type == ProjectionSourceType.Param) {
        extractParamDefaultValue(config, it)
    } else {
        "ProjectionModel${it.name}()"
    }

    private fun extractParamDefaultValue(
        config: ConfigSchema,
        it: ProjectionSource
    ) = if (config.parameters[it.name]!!.type == "string") {
        "\"${config.parameters[it.name]!!.initial}\""
    } else {
        config.parameters[it.name]!!.initial
    }


}

fun castType(type: String) = when (type) {
    "integer" -> Int::class.asClassName()
    "string" -> String::class.asClassName()
    "boolean" -> Boolean::class.asClassName()
    "double" -> Double::class.asClassName()
    "long" -> Long::class.asClassName()
    else -> {
        val meta = type.split('.')
        ClassName(meta.dropLast(1).joinToString("."), meta.last())
    }
}

fun buildEventChains(metadata: ConfigSchema): List<List<String>> {
    val chains = mutableListOf<List<String>>()

    metadata.events.keys.forEach { eventName ->
        buildChainsRecursive(eventName, metadata, mutableListOf(), chains)
    }

    return chains
}

private fun buildChainsRecursive(
    currentHandler: String,
    metadata: ConfigSchema,
    currentChain: MutableList<String>,
    resultChains: MutableList<List<String>>
) {
    currentChain.add(
        if (currentHandler in metadata.events) "Event.$currentHandler"
        else currentHandler
    )

    // Если это событие, продолжаем цепочку для каждого returns
    if (currentHandler in metadata.events) {
        val event = metadata.events[currentHandler]!!
        event.returns.forEach { handler ->
            buildChainsRecursive(handler.name, metadata, currentChain, resultChains)
        }
        // Удаляем последний элемент, если это событие (чтобы избежать дублирования)
        currentChain.removeLast()
    }
    // Если это Param, сохраняем цепочку
    else {
        resultChains.add(currentChain.toList())
        currentChain.removeLast() // Откатываемся назад для других веток
    }
}

fun extractOrderedEvents(config: ConfigSchema): Map<String, List<String>> {
    val events = config.events
    val parameters = config.parameters

    val orderedEventsMap = mutableMapOf<String, List<String>>()

    fun processEvent(eventName: String, seen: MutableSet<String> = mutableSetOf()): List<String> {
        if (!seen.add(eventName)) return emptyList()

        val orderedEvents = mutableListOf(eventName)

        events[eventName]?.returns?.forEach { action ->
            val paramName = action.name.substringBefore(".")
            val intent = action.name.substringAfter(".", "")

            if (parameters[paramName]?.intents?.containsKey(intent) == true) {
                orderedEvents += processEvent(action.name, seen)
            }
        }
        return orderedEvents
    }

    config.general.forEach { rootEvent ->
        orderedEventsMap[rootEvent] = processEvent(rootEvent).distinct()
    }

    return orderedEventsMap
}

fun calculateTree(general: List<String>, events: Map<String, EventDefinition>): Map<String, List<String>> {

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

    }
}