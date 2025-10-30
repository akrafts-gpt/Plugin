package io.github.remote.konfig

import androidx.fragment.app.FragmentManager

/**
 * Runtime contract for screens generated from remote configs.
 */
interface RemoteConfigScreen {
    /** Unique identifier for the screen, usually the config key. */
    val id: String

    /** Human readable title to display in the list. */
    val title: String

    /**
     * Requests the screen to render itself using the provided [FragmentManager].
     */
    fun show(fragmentManager: FragmentManager)
}

/**
 * Marker interface used for multibinding sets of [RemoteConfigScreen] implementations.
 */
interface RemoteConfigScreenProvider {
    fun screens(): Set<RemoteConfigScreen>
}
