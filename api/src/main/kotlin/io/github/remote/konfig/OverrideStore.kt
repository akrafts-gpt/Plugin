package io.github.remote.konfig

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores override values for remote configuration keys.
 */
class OverrideStore private constructor(private val preferences: SharedPreferences) {

    private val overrides = ConcurrentHashMap<String, String>()

    init {
        preferences.all
            .filterValues { it is String }
            .forEach { (key, value) -> overrides[key] = value as String }
    }

    /**
     * Returns the override value for [key] if present.
     */
    fun getOverride(key: String): String? = overrides[key]

    /**
     * Persists the override [value] for [key].
     */
    fun setOverride(key: String, value: String) {
        overrides[key] = value
        preferences.edit().putString(key, value).apply()
    }

    /**
     * Removes the override entry for [key].
     */
    fun clearOverride(key: String) {
        overrides.remove(key)
        preferences.edit().remove(key).apply()
    }

    /**
     * Returns a snapshot of all stored overrides.
     */
    fun getOverrides(): Map<String, String> = overrides.toMap()

    companion object {
        private const val STORE_NAME = "remote-konfig-overrides"

        /**
         * Creates a new [OverrideStore] backed by a private [SharedPreferences] instance.
         */
        fun create(context: Context): OverrideStore =
            OverrideStore(context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE))

        /**
         * Creates an in-memory [OverrideStore] that does not persist values to disk.
         */
        fun inMemory(): OverrideStore = OverrideStore(InMemoryPreferences())
    }
}

private class InMemoryPreferences : SharedPreferences {
    private val data = ConcurrentHashMap<String, String>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = key?.let { data[it] } ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = key?.let { data.containsKey(it) } ?: false

    override fun edit(): SharedPreferences.Editor = Editor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Editor(private val data: ConcurrentHashMap<String, String>) : SharedPreferences.Editor {
        private val pending = ConcurrentHashMap<String, String?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyChange(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

        override fun remove(key: String?): SharedPreferences.Editor = applyChange(key, null)

        override fun clear(): SharedPreferences.Editor {
            data.clear()
            pending.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            pending.forEach { (key, value) ->
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
            pending.clear()
        }

        private fun applyChange(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }
    }
}
