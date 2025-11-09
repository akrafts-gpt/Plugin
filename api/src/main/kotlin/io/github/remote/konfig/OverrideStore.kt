package io.github.remote.konfig

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.asMap
import androidx.datastore.preferences.preferencesDataStoreFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Stores override values for remote configuration keys. When constructed with a [Context],
 * overrides are persisted to a preferences DataStore so they survive process restarts.
 *
 * The class maintains an in-memory cache for synchronous access to values and mirrors updates to
 * the underlying DataStore when available.
 */
class OverrideStore @JvmOverloads constructor(
    context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val overrides = ConcurrentHashMap<String, String>()

    private val dataStore: DataStore<Preferences>? = context?.let { appContext ->
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { appContext.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
        ).also { store ->
            runBlocking {
                val initial = withContext(Dispatchers.IO) { store.data.first() }
                applySnapshot(initial)
            }
            scope.launch {
                store.data.collect { prefs ->
                    applySnapshot(prefs)
                }
            }
        }
    }

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
        dataStore?.let { store ->
            scope.launch {
                store.edit { prefs ->
                    prefs[stringPreferencesKey(key)] = value
                }
            }
        }
    }

    fun setOverride(key: String, value: String) {
        put(key, value)
    }

    /**
     * Removes the override entry for [key].
     */
    fun remove(key: String) {
        overrides.remove(key)
        dataStore?.let { store ->
            scope.launch {
                store.edit { prefs ->
                    prefs.remove(stringPreferencesKey(key))
                }
            }
        }
    }

    fun clearOverride(key: String) {
        remove(key)
    }

    /**
     * Clears all stored overrides.
     */
    fun clear() {
        overrides.clear()
        dataStore?.let { store ->
            scope.launch {
                store.edit { prefs ->
                    prefs.clear()
                }
            }
        }
    }

    private fun applySnapshot(preferences: Preferences) {
        val persisted = preferences.asMap()
            .mapNotNull { entry ->
                val value = entry.value as? String ?: return@mapNotNull null
                entry.key.name to value
            }
        overrides.clear()
        overrides.putAll(persisted)
    }

    private companion object {
        private const val DATASTORE_FILE_NAME = "remote_config_overrides"
    }
}
