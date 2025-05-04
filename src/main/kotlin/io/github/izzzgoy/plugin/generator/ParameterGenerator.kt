package io.github.izzzgoy.plugin.generator

import com.ndmatrix.parameter.Message
import com.ndmatrix.parameter.ParameterHolder
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
import io.github.izzzgoy.plugin.models.ConfigSchema
import io.github.izzzgoy.plugin.utils.castType

class ParameterGenerator : Generator {

    override fun generate(configSchema: ConfigSchema, packageName: String) =
        configSchema.parameters.map { (classname, definition) ->

            val intentsType = TypeSpec.interfaceBuilder(
                "${classname}Intents"
            )
                .addModifiers(KModifier.SEALED)
                .addSuperinterface(Message.Intent::class)
                .addTypes(
                    definition.intents.map {
                        if (it.value.args == null) {
                            TypeSpec.objectBuilder(it.key)
                        } else {
                            TypeSpec.classBuilder(it.key)
                                .primaryConstructor(
                                    FunSpec.constructorBuilder()
                                        .also { build ->
                                            it.value.args?.forEach { (paramName, def) ->
                                                build.addParameter(
                                                    ParameterSpec.builder(
                                                        paramName,
                                                        castType(def.type).copy(nullable = def.nullable)
                                                    ).also {
                                                        if (def.initial != null) {
                                                            it.defaultValue(def.initial)
                                                        }
                                                    }
                                                        .build()
                                                )
                                            }
                                        }
                                        .build()
                                )
                                .addProperties(
                                    it.value.args?.map { (paramName, def) ->
                                        PropertySpec.builder(paramName, castType(def.type).copy(nullable = def.nullable))
                                            .initializer(paramName)
                                            .build()
                                    } ?: emptyList()
                                )
                        }
                            .addModifiers(KModifier.DATA)
                            .addSuperinterface(ClassName(packageName, "${classname}Intents"))
                            .build()
                    }
                )
                .build()

            FileSpec.builder(packageName, classname)
                .addType(intentsType)
                .addType(
                    TypeSpec.classBuilder("${classname}ParameterHolder")
                        .addSuperclassConstructorParameter(
                            if (definition.initial == null) {
                                "initialValue"
                            } else if (definition.type == "string") {
                                "\"${definition.initial}\""
                            } else {
                                definition.initial
                            }
                        )
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            ParameterHolder::class.asTypeName().parameterizedBy(
                                ClassName(packageName, "${classname}Intents"),
                                castType(definition.type),
                            )
                        ).also {
                            if (definition.initial == null) {
                                it.primaryConstructor(
                                    FunSpec.constructorBuilder()
                                        .addParameter(
                                            ParameterSpec.builder(
                                                "initialValue",
                                                castType(definition.type)
                                            ).build()
                                        ).build()
                                )
                            }
                        }.addFunctions(
                            definition.intents.keys.map { intentName ->
                                FunSpec.builder("handle$intentName")
                                    .addModifiers(KModifier.ABSTRACT, KModifier.PROTECTED)
                                    .returns(castType(definition.type))
                                    .addParameter(
                                        ParameterSpec
                                            .builder(
                                                "intent",
                                                ClassName(
                                                    packageName,
                                                    "${classname}Intents.$intentName"
                                                )
                                            )
                                            .build()
                                    )
                                    .addParameter(
                                        "state", castType(definition.type)
                                    )
                                    .build()
                            }
                        )
                        .addFunction(
                            FunSpec.builder("handle")
                                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                                .addParameter(
                                    ParameterSpec.builder(
                                        "e",
                                        ClassName(packageName, "${classname}Intents")
                                    )
                                        .build()
                                )
                                .beginControlFlow("when(e)")
                                .addCode(
                                    definition.intents.map { (intent, name) ->
                                        "is ${classname}Intents.$intent -> update(handle${intent}(e, value))"
                                    }.joinToString(separator = "\n")
                                )
                                .endControlFlow()
                                .build()

                        )
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
                                        .beginControlFlow("if (e is ${classname}Intents)")
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
                                            "_postMetadata.emit(%M(e, it, %M[%M.CallMetadataKey]!!.parentId, %M[%M.CallMetadataKey]!!.currentId))",
                                            MemberName(
                                                "com.ndmatrix.parameter",
                                                "PostExecMetadata"
                                            ),
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
        }
}