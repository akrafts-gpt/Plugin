package io.github.remote.konfig.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.OverrideStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Global state for tracking the active configuration editor on non-Android platforms.
 */
object RemoteConfigDebugState {
    var activeParams by mutableStateOf<EditorParams<*>?>(null)
        private set

    fun <T : Any> showEditor(
        configKey: String,
        configTypeName: String,
        serializer: KSerializer<T>,
        editor: RemoteConfigEditor<T>,
        remoteConfigProvider: RemoteConfigProvider,
        overrideStore: OverrideStore
    ) {
        activeParams = EditorParams(
            configKey = configKey,
            configTypeName = configTypeName,
            serializer = serializer,
            editor = editor,
            remoteConfigProvider = remoteConfigProvider,
            overrideStore = overrideStore
        )
    }

    fun dismiss() {
        activeParams = null
    }

    data class EditorParams<T : Any>(
        val configKey: String,
        val configTypeName: String,
        val serializer: KSerializer<T>,
        val editor: RemoteConfigEditor<T>,
        val remoteConfigProvider: RemoteConfigProvider,
        val overrideStore: OverrideStore
    )
}
