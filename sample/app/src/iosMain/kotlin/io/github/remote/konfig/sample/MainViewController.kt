package io.github.remote.konfig.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.debug.RemoteConfigDebugState
import io.github.remote.konfig.debug.RemoteConfigEditorScreen
import kotlinx.serialization.json.Json
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val remoteConfigProvider = remember { FakeRemoteConfigProvider() }
    val overrideStore = remember { OverrideStore() }

    val screens = remember { createRemoteConfigScreens(remoteConfigProvider, overrideStore) }
    val entries = remember(screens) {
        screens.map { screen ->
            val metadata = SampleScreenMetadata[screen.id]!!
            ScreenEntry(
                id = screen.id,
                title = screen.title,
                typeName = metadata.typeName,
                key = metadata.key,
                screen = screen
            )
        }
    }

    SampleAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SampleAppHome(
                entries = entries,
                onEntrySelected = { entry ->
                    entry.screen.show()
                }
            )

            // Render the active editor if any
            RemoteConfigDebugState.activeParams?.let { params ->
                EditorWrapper(params)
            }
        }
    }
}

@Composable
private fun <T : Any> EditorWrapper(params: RemoteConfigDebugState.EditorParams<T>) {
    RemoteConfigEditorScreen(
        title = params.configTypeName,
        configTypeName = params.configTypeName,
        configKey = params.configKey,
        remoteJson = params.remoteConfigProvider.getRemoteConfig(params.configKey),
        overrideJson = params.overrideStore.get(params.configKey),
        editor = params.editor,
        serializer = params.serializer,
        json = Json { prettyPrint = true; ignoreUnknownKeys = true },
        onSave = { updatedJson ->
            params.overrideStore.put(params.configKey, updatedJson)
            RemoteConfigDebugState.dismiss()
        },
        onShare = { payload ->
            // Share logic for iOS could be added here
        },
        onReset = {
            params.overrideStore.remove(params.configKey)
            RemoteConfigDebugState.dismiss()
        },
        onDismiss = { RemoteConfigDebugState.dismiss() }
    )
}
