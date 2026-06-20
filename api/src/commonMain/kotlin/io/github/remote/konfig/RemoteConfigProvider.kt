package io.github.remote.konfig

/**
 * Provides read access to remote configuration values.
 */
interface RemoteConfigProvider {
    /**
     * Returns the raw configuration payload for the provided [key].
     *
     * @return a JSON string if the value exists, or `null` otherwise.
     */
    fun getRemoteConfig(key: String): String?
}
