package io.github.remote.konfig

import androidx.fragment.app.FragmentManager

/**
 * Android implementation of [RemoteConfigScreen] that includes [FragmentManager] support.
 */
actual interface RemoteConfigScreen {
    actual val id: String
    actual val title: String

    /**
     * Requests the screen to render itself using the provided [FragmentManager].
     */
    fun show(fragmentManager: FragmentManager)
}
