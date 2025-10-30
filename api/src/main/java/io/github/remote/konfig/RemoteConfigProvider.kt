package io.github.remote.konfig

/**
 * Abstraction for fetching remote configuration JSON payloads.
 */
public fun interface RemoteConfigProvider {
    /**
     * Returns the JSON payload for [key], or `null` if no value is available.
     */
    public fun getRemoteConfig(key: String): String?
}
