package com.airgradient.android.data.models.monitors

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlin.math.abs

internal fun JsonElement?.asDoubleOrNull(): Double? {
    if (this == null || this is JsonNull) return null
    return when {
        this.isJsonNull -> null
        this.isJsonPrimitive -> {
            val primitive = this.asJsonPrimitive
            when {
                primitive.isNumber -> primitive.asNumber.toDouble()
                primitive.isString -> primitive.asString.toDoubleOrNull()
                primitive.isBoolean -> if (primitive.asBoolean) 1.0 else 0.0
                else -> null
            }
        }
        this.isJsonObject -> {
            val obj = this.asJsonObject
            val prioritizedKeys = listOf("value", "reading", "avg", "mean", "index", "data")
            prioritizedKeys.firstNotNullOfOrNull { key ->
                obj.get(key).asDoubleOrNull()
            } ?: obj.entrySet().firstNotNullOfOrNull { it.value.asDoubleOrNull() }
        }
        else -> null
    }
}

internal fun JsonElement?.asBooleanOrNull(): Boolean? {
    if (this == null || this is JsonNull) return null
    return when {
        this.isJsonNull -> null
        this.isJsonPrimitive -> {
            val primitive = this.asJsonPrimitive
            when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isString -> primitive.asString.toBooleanStrictOrNull()
                primitive.isNumber -> primitive.asNumber.toInt() != 0
                else -> null
            }
        }
        else -> null
    }
}

internal fun JsonElement?.asLongOrNull(): Long? {
    val asDouble = asDoubleOrNull() ?: return null
    return asDouble.toLong()
}

internal fun JsonObject?.firstAvailable(vararg keys: String): JsonElement? {
    if (this == null) return null
    keys.forEach { key ->
        if (has(key)) return get(key)
    }
    return null
}

internal fun JsonElement?.asIntOrNull(): Int? = asDoubleOrNull()?.let { value ->
    val rounded = value.toInt()
    if (abs(value - rounded) < 0.0001) rounded else rounded
}
