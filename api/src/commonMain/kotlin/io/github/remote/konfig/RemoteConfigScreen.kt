package io.github.remote.konfig

/**
 * Runtime contract for screens generated from remote configs.
 */
expect interface RemoteConfigScreen {
    val id: String
    val title: String
}

interface RemoteConfigScreenProvider {
    fun screens(): Set<RemoteConfigScreen>
}
