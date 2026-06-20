package io.github.remote.konfig.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.RemoteConfigScreen
import io.github.remote.konfig.debug.RemoteConfigDebugState
import io.github.remote.konfig.debug.RemoteConfigEditorScreen
import io.github.remote.konfig.generated.DeeplyNestedShowcaseConfigRemoteConfigScreen
import io.github.remote.konfig.generated.NotificationShowcaseConfigRemoteConfigScreen
import io.github.remote.konfig.generated.ProfileOptionsConfigRemoteConfigScreen
import io.github.remote.konfig.generated.WelcomeExperienceConfigRemoteConfigScreen
import kotlinx.serialization.json.Json

actual fun createRemoteConfigScreens(
    remoteConfigProvider: RemoteConfigProvider,
    overrideStore: OverrideStore
): List<RemoteConfigScreen> = listOf(
    DeeplyNestedShowcaseConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    NotificationShowcaseConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    ProfileOptionsConfigRemoteConfigScreen(remoteConfigProvider, overrideStore),
    WelcomeExperienceConfigRemoteConfigScreen(remoteConfigProvider, overrideStore)
)

fun main() {
    application {
        val remoteConfigProvider = remember { FakeRemoteConfigProvider() }
        val overrideStore = remember { OverrideStore() }

        Window(onCloseRequest = ::exitApplication, title = "Remote Konfig Sample") {
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
                SampleAppHome(
                    entries = entries,
                    onEntrySelected = { entry ->
                        println("Selected: ${entry.title}")
                        entry.screen.show()
                    }
                )

                // Render the active editor if any
                RemoteConfigDebugState.activeParams?.let { params ->
                    Dialog(
                        onCloseRequest = { RemoteConfigDebugState.dismiss() },
                        title = "Edit ${params.configTypeName}",
                        state = rememberDialogState(width = 600.dp, height = 800.dp)
                    ) {
                        EditorWrapper(params)
                    }
                }
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
            println("Saved override for ${params.configKey}")
            RemoteConfigDebugState.dismiss()
        },
        onShare = { payload ->
            println("Share payload: $payload")
        },
        onReset = {
            params.overrideStore.remove(params.configKey)
            println("Reset override for ${params.configKey}")
            RemoteConfigDebugState.dismiss()
        },
        onDismiss = { RemoteConfigDebugState.dismiss() }
    )
}
