package io.github.remote.konfig

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds in-memory overrides for remote configuration payloads.
 */
public object OverrideStore {
    private val overrides = ConcurrentHashMap<String, String>()

    /** Returns the override payload for [key], or `null` if none exists. */
    @JvmStatic
    public fun getOverride(key: String): String? = overrides[key]

    /** Sets or clears the override for [key]. */
    @JvmStatic
    public fun setOverride(key: String, json: String?) {
        if (json == null) {
            overrides.remove(key)
        } else {
            overrides[key] = json
        }
    }

    /** Removes the override for [key], if present. */
    @JvmStatic
    public fun clearOverride(key: String) {
        overrides.remove(key)
    }

    /** Clears all overrides currently stored in memory. */
    @JvmStatic
    public fun clearAll() {
        overrides.clear()
    }

    /** Loads overrides from [preferences], replacing existing in-memory values. */
    @JvmStatic
    public fun loadFromPreferences(preferences: SharedPreferences) {
        overrides.clear()
        preferences.all.forEach { (key, value) ->
            if (value is String) {
                overrides[key] = value
            }
        }
    }

    /** Persists the current in-memory overrides into [preferences]. */
    @JvmStatic
    public fun persistToPreferences(preferences: SharedPreferences) {
        val editor = preferences.edit()
        editor.clear()
        overrides.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
    }

    /** Returns a snapshot of the currently registered overrides. */
    @JvmStatic
    public fun snapshot(): Map<String, String> = overrides.toMap()
}
