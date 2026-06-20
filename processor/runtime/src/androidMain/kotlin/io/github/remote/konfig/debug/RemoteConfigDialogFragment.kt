package io.github.remote.konfig.debug

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val DEFAULT_EDITOR_TITLE = "Edit Config"

@Composable
internal fun RemoteConfigAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val surfaceColor = colorScheme.surface.toArgb()
        window.statusBarColor = surfaceColor
        window.navigationBarColor = surfaceColor
        WindowCompat.getInsetsController(window, view)?.let { controller ->
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = colorScheme.surface) {
            content()
        }
    }
}

abstract class RemoteConfigDialogFragment<T : Any> : DialogFragment() {

    private val overrideStore: OverrideStore
        get() = obtainOverrideStore(requireContext().applicationContext)

    @Inject
    lateinit var remoteConfigProvider: RemoteConfigProvider

    protected abstract val configKey: String
    protected abstract val screenTitle: String
    protected abstract val configTypeName: String
    protected abstract val serializer: KSerializer<T>
    protected abstract val editor: RemoteConfigEditor<T>

    private fun obtainOverrideStore(appContext: Context): OverrideStore {
        sharedOverrideStore?.let { return it }
        return synchronized(overrideStoreLock) {
            sharedOverrideStore ?: OverrideStore().also { created ->
                sharedOverrideStore = created
            }
        }
    }

    protected open fun createJson(): Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        serializersModule = editor.serializersModule
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        val remoteJson = remoteConfigProvider.getRemoteConfig(configKey)
        val overrideJson = overrideStore.getOverride(configKey)
        val dialogJson = createJson()
        val context = requireContext()

        return ComposeView(context).apply {
            setContent {
                RemoteConfigAndroidTheme {
                    RemoteConfigEditorScreen(
                        title = DEFAULT_EDITOR_TITLE,
                        configTypeName = configTypeName,
                        configKey = configKey,
                        remoteJson = remoteJson,
                        overrideJson = overrideJson,
                        editor = editor,
                        serializer = serializer,
                        json = dialogJson,
                        onSave = { updatedJson ->
                            overrideStore.setOverride(configKey, updatedJson)
                            Toast.makeText(context, "Override saved", Toast.LENGTH_SHORT).show()
                            dismissAllowingStateLoss()
                        },
                        onShare = { payload ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share config JSON"))
                        },
                        onReset = {
                            overrideStore.clearOverride(configKey)
                            Toast.makeText(context, "Override cleared", Toast.LENGTH_SHORT).show()
                            dismissAllowingStateLoss()
                        },
                        onDismiss = { dismissAllowingStateLoss() }
                    )
                }
            }
        }
    }

    companion object {
        @Volatile
        private var sharedOverrideStore: OverrideStore? = null
        private val overrideStoreLock = Any()

        @VisibleForTesting
        internal fun resetOverrideStore() {
            synchronized(overrideStoreLock) {
                sharedOverrideStore = null
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
