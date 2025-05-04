package io.github.izzzgoy.plugin.generator

import com.ndmatrix.parameter.AbstractEventHandler
import com.ndmatrix.parameter.Message
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
import kotlin.coroutines.CoroutineContext

class EventGenerator : Generator {
    override fun generate(configSchema: ConfigSchema, packageName: String): List<FileSpec> {
        return configSchema.events.map { (eventName, definition) ->
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
                                                castType(def.type).copy(nullable = def.nullable),
                                            ).also {
                                                if (def.initial != null) {
                                                    it.defaultValue(def.initial)
                                                }
                                            }
                                                .build()
                                        }
                                    )
                                    .build()
                            )
                            .addProperties(
                                definition.args.map { (paramName, def) ->
                                    PropertySpec.builder(paramName, castType(def.type).copy(nullable = def.nullable))
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
}