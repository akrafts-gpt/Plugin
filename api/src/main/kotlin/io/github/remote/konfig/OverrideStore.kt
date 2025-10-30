package io.github.remote.konfig

import java.util.concurrent.ConcurrentHashMap

/**
 * Stores override values for remote configuration keys in memory.
 */
class OverrideStore {
    private val overrides = ConcurrentHashMap<String, String>()

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
}
