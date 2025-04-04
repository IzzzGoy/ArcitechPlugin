package com.ndmatrix.plugin.generator

import com.ndmatrix.plugin.models.ConfigSchema
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
import com.squareup.kotlinpoet.asTypeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.ndmatrix.parameter.Message
import com.ndmatrix.parameter.AbstractEventHandler
import com.ndmatrix.parameter.EventChain
import com.ndmatrix.parameter.ParameterHolder
import com.ndmatrix.parameter.PostExecMetadata
import com.ndmatrix.parameter.Projection
import com.ndmatrix.plugin.models.ProjectionSource
import com.ndmatrix.plugin.models.ProjectionSourceType
import kotlin.coroutines.CoroutineContext

class Generator {
    private val parameterGenerator = ParameterGenerator()
    fun generate(config: ConfigSchema, packageName: String): List<FileSpec> {

        println(buildEventChains(config))
        println(extractOrderedEvents(config))
        return parameterGenerator.generate(config, packageName) + generateProjections(
            config,
            packageName
        ) + config.events.map { (eventName, definition) ->
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
                                            "_postMetadata.emit(PostExecMetadata(e, it))",
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
        } + extractOrderedEvents(config).map { (it, chain) ->
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

    private fun generateProjections(
        config: ConfigSchema,
        packageName: String
    ) = config.projection.map { (name, params) ->

        FileSpec.builder(packageName, "Projection$name")
            .addType(
                TypeSpec.classBuilder("ProjectionModel$name")
                    .addModifiers(KModifier.DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(
                                params.map {
                                    ParameterSpec.builder(
                                        it.name,
                                        extractModelClass(it, config, packageName)
                                    ).defaultValue(
                                        extractDefaultValue(it, config) ?: ""
                                    ).build()
                                }
                            )
                            .build()
                    )
                    .addProperties(
                        params.map { s ->
                            PropertySpec.builder(s.name, extractModelClass(s, config, packageName))
                                .initializer(s.name)
                                .build()
                        }
                    )
                    .build()
            )
            .addType(
                TypeSpec.classBuilder("Projection$name")
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(
                                params.map {
                                    ParameterSpec.builder(
                                        it.name, ClassName(
                                            packageName,
                                            if (it.type == ProjectionSourceType.Param) "${it.name}ParameterHolder" else "Projection${it.name}"
                                        )
                                    )
                                        .build()
                                }
                            )
                            .addParameter("coroutineContext", CoroutineContext::class)
                            .build()
                    )
                    .addModifiers(KModifier.ABSTRACT)
                    .superclass(
                        Projection::class.asTypeName().parameterizedBy(
                            ClassName(packageName, "ProjectionModel$name")
                        )
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "flow",
                            StateFlow::class.asTypeName().parameterizedBy(
                                ClassName(packageName, "ProjectionModel$name")
                            )
                        )
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(
                                CodeBlock.builder()
                                    .addStatement(
                                        "%M(",
                                        MemberName("kotlinx.coroutines.flow", "combine")
                                    )
                                    .indent()
                                    .addStatement(
                                        params.joinToString(", ") { "${it.name}.flow" }
                                    )
                                    .unindent()
                                    .addStatement(") { ${params.indices.joinToString(separator = ", ") { "t$it" }} ->")
                                    .indent()
                                    .addStatement("project(${params.indices.joinToString(separator = ", ") { "t$it" }})")
                                    .unindent()
                                    .addStatement("}")
                                    .addStatement(
                                        ".%M(",
                                        MemberName("kotlinx.coroutines.flow", "stateIn")
                                    )
                                    .indent()
                                    .addStatement("initialValue = ProjectionModel$name(),")
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
                                params.map {
                                    ParameterSpec.builder(
                                        it.name,
                                        extractModelClass(it, config, packageName)
                                    ).build()
                                }
                            )
                            .returns(ClassName(packageName, "ProjectionModel$name"))
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "value",
                            ClassName(packageName, "ProjectionModel$name")
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

    private fun extractModelClass(
        it: ProjectionSource,
        config: ConfigSchema,
        packageName: String
    ) = if (it.type == ProjectionSourceType.Param) {
        castType(config.parameters[it.name]!!.type).asTypeName()
    } else {
        ClassName(packageName, "ProjectionModel${it.name}")
    }
}

fun castType(type: String) = when (type) {
    "integer" -> Int::class
    "string" -> String::class
    "boolean" -> Boolean::class
    "double" -> Double::class
    "long" -> Long::class
    else -> throw IllegalArgumentException()
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