package io.github.izzzgoy.plugin.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

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