package io.github.remote.konfig

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stores override values for remote configuration keys.
 * Basic implementation for KMP.
 */
class OverrideStore {
    private val overrides = mutableMapOf<String, String>()

    /**
     * Returns the override value for [key] if present.
     */
    fun get(key: String): String? = overrides[key]

    fun getOverride(key: String): String? = get(key)

    /**
     * Adds or replaces the override [value] for [key].
     */
    fun put(key: String, value: String) {
        overrides[key] = value
    }

    fun setOverride(key: String, value: String) {
        put(key, value)
    }

    /**
     * Removes the override entry for [key].
     */
    fun remove(key: String) {
        overrides.remove(key)
    }

    fun clearOverride(key: String) {
        remove(key)
    }

    /**
     * Clears all stored overrides.
     */
    fun clear() {
        overrides.clear()
    }
    
    // Support for parsing/encoding if needed
    fun parseOverrides(json: String) {
        runCatching {
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            jsonObject.forEach { (key, value) ->
                overrides[key] = value.jsonPrimitive.content
            }
        }
    }

    fun encodeOverrides(): String {
        return buildJsonObject {
            overrides.forEach { (key, value) ->
                put(key, value)
            }
        }.toString()
    }
}
