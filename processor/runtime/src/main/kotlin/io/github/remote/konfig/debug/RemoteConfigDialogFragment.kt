package io.github.remote.konfig.debug

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val LOG_TAG = "RemoteConfigDialog"

/**
 * Base dialog fragment that renders a Compose-powered editor for a remote config entry.
 */
abstract class RemoteConfigDialogFragment<T : Any> : DialogFragment() {

    @Inject
    lateinit var overrideStore: OverrideStore

    @Inject
    lateinit var remoteConfigProvider: RemoteConfigProvider

    protected abstract val configKey: String

    protected abstract val screenTitle: String

    protected abstract val serializer: KSerializer<T>

    protected abstract val editor: RemoteConfigEditor<T>

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
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        val remoteJson = remoteConfigProvider.getRemoteConfig(configKey)
        val overrideJson = overrideStore.getOverride(configKey)
        val dialogJson = createJson()
        val context = requireContext()

        Log.d(
            LOG_TAG,
            "Opening editor for key=$configKey remoteLoaded=${remoteJson != null} overrideLoaded=${overrideJson != null}"
        )

        return ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    RemoteConfigDialogContent(
                        title = screenTitle,
                        configKey = configKey,
                        remoteJson = remoteJson,
                        overrideJson = overrideJson,
                        editor = editor,
                        serializer = serializer,
                        json = dialogJson,
                        onSave = { updatedJson ->
                            Log.d(LOG_TAG, "Persisting override for key=$configKey")
                            overrideStore.setOverride(configKey, updatedJson)
                            Toast.makeText(context, "Override saved", Toast.LENGTH_SHORT).show()
                            dismissAllowingStateLoss()
                        },
                        onShare = { payload ->
                            Log.d(LOG_TAG, "Sharing payload for key=$configKey (${payload.length} chars)")
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share config JSON"))
                        },
                        onReset = {
                            Log.d(LOG_TAG, "Clearing override for key=$configKey")
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
}

@Composable
internal fun <T : Any> RemoteConfigDialogContent(
    title: String,
    configKey: String,
    remoteJson: String?,
    overrideJson: String?,
    editor: RemoteConfigEditor<T>,
    serializer: KSerializer<T>,
    json: Json,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultState = remember(editor) { editor.defaultInstance() }
    val sanitizedRemoteJson = remoteJson?.takeIf { it.isNotBlank() }
    var activeOverrideJson by remember(overrideJson) {
        mutableStateOf(overrideJson?.takeIf { it.isNotBlank() })
    }
    val initialJson = activeOverrideJson ?: sanitizedRemoteJson
    val (startingState, initialError) = remember(initialJson, json, serializer) {
        if (initialJson == null) {
            defaultState to null
        } else {
            runCatching { json.decodeFromString(serializer, initialJson) }
                .fold(
                    onSuccess = { it to null },
                    onFailure = {
                        Log.e(LOG_TAG, "Failed to decode initial JSON for $configKey", it)
                        defaultState to it
                    }
                )
        }
    }

    var currentState by remember { mutableStateOf(startingState) }
    var rawJson by remember {
        mutableStateOf(
            initialJson ?: json.encodeToString(serializer, startingState)
        )
    }
    var rawErrorMessage by remember { mutableStateOf(initialError?.message) }
    var showRawEditor by remember { mutableStateOf(initialError != null) }
    val pendingFieldValues = remember { mutableStateMapOf<String, String>() }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }

    val fields: List<EditorField<T>> = remember(editor) { editor.fields() }

    val onRawValidated: (T) -> Unit = { decoded ->
        currentState = decoded
        pendingFieldValues.clear()
        fieldErrors.clear()
        showRawEditor = false
        Log.d(LOG_TAG, "Validated raw JSON for $configKey")
    }

    val actionEnabled = if (showRawEditor) {
        rawErrorMessage == null
    } else {
        fieldErrors.isEmpty()
    }

    val sharePayload = remember(showRawEditor, rawJson, currentState) {
        if (showRawEditor) {
            rawJson
        } else {
            json.encodeToString(serializer, currentState)
        }
    }

    val onSaveClick: () -> Unit = {
        if (showRawEditor) {
            runCatching { json.decodeFromString(serializer, rawJson) }
                .onSuccess { decoded ->
                    onRawValidated(decoded)
                    activeOverrideJson = rawJson
                    onSave(rawJson)
                    Log.d(LOG_TAG, "Saved override from raw editor for $configKey")
                }
                .onFailure { throwable ->
                    rawErrorMessage = throwable.message
                    Log.e(LOG_TAG, "Failed to save raw JSON for $configKey", throwable)
                }
        } else {
            val updatedJson = json.encodeToString(serializer, currentState)
            activeOverrideJson = updatedJson
            rawJson = updatedJson
            onSave(updatedJson)
            Log.d(LOG_TAG, "Saved override from form editor for $configKey")
        }
    }

    val handleReset: () -> Unit = {
        activeOverrideJson = null
        rawJson = sanitizedRemoteJson ?: json.encodeToString(serializer, defaultState)
        onReset()
        Log.d(LOG_TAG, "Override cleared from dialog for $configKey")
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            RemoteConfigDialogTopBar(
                title = title,
                onDismiss = onDismiss
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                RemoteConfigDialogActionBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    enabled = actionEnabled,
                    onShare = { onShare(sharePayload) },
                    onSave = onSaveClick
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Key: $configKey",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hasOverride = activeOverrideJson != null
                    Text(
                        text = if (hasOverride) "Source: Override" else "Source: Remote",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RemoteConfigModeToggle(
                        modifier = Modifier.fillMaxWidth(),
                        showRaw = showRawEditor,
                        canSwitchToForm = rawErrorMessage == null,
                        onToggle = { shouldShowRaw ->
                            if (shouldShowRaw) {
                                rawJson = json.encodeToString(serializer, currentState)
                                rawErrorMessage = null
                                showRawEditor = true
                                Log.d(LOG_TAG, "Switched to raw editor for $configKey")
                            } else {
                                runCatching { json.decodeFromString(serializer, rawJson) }
                                    .onSuccess { decoded ->
                                        currentState = decoded
                                        pendingFieldValues.clear()
                                        fieldErrors.clear()
                                        rawErrorMessage = null
                                        showRawEditor = false
                                        Log.d(LOG_TAG, "Switched to form editor for $configKey")
                                    }
                                    .onFailure { throwable ->
                                        rawErrorMessage = throwable.message
                                        Log.e(LOG_TAG, "Failed to parse JSON while toggling editor for $configKey", throwable)
                                    }
                            }
                        }
                    )
                }
                Divider(modifier = Modifier.padding(top = 8.dp))
            }

            if (showRawEditor) {
                item("raw_editor") {
                    RawJsonEditor(
                        modifier = Modifier.fillMaxWidth(),
                        value = rawJson,
                        errorMessage = rawErrorMessage,
                        onValueChange = { newValue ->
                            rawJson = newValue
                            rawErrorMessage = runCatching {
                                json.decodeFromString(serializer, newValue)
                            }.fold(
                                onSuccess = { null },
                                onFailure = { failure -> failure.message }
                            )
                        }
                    )
                }

                rawJson.takeIf { it.isNotBlank() }?.let { activeJson ->
                    item("raw_active_values") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadOnlyField(label = "Active Value", value = activeJson)
                            if (activeOverrideJson != null) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = handleReset
                                ) {
                                    Text("Remove Override")
                                }
                            }
                        }
                    }
                }
            } else {
                items(items = fields, key = { it.name }) { field ->
                    when (field.type) {
                        "kotlin.String" -> StringField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            state = currentState,
                            onStateChange = { updated -> currentState = updated }
                        )

                        "kotlin.Boolean" -> BooleanField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            state = currentState,
                            onStateChange = { updated -> currentState = updated }
                        )

                        "kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Float" -> NumericField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            type = field.type,
                            state = currentState,
                            pendingValues = pendingFieldValues,
                            fieldErrors = fieldErrors,
                            onStateChange = { updated, error ->
                                if (error == null) {
                                    currentState = updated
                                }
                            }
                        )

                        else -> UnsupportedField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            state = currentState
                        )
                    }
                }
            }

            if (!showRawEditor) {
                item("form_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sanitizedRemoteJson?.let { payload ->
                            ReadOnlyField(label = "Remote Value", value = payload)
                        }
                        activeOverrideJson?.let { payload ->
                            ReadOnlyField(label = "Override Value", value = payload)
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                onClick = handleReset
                            ) {
                                Text("Remove Override")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteConfigDialogTopBar(
    title: String,
    onDismiss: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun RemoteConfigDialogActionBar(
    modifier: Modifier,
    enabled: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = onShare,
            enabled = enabled
        ) {
            Text("Share")
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSave,
            enabled = enabled
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun RemoteConfigModeToggle(
    modifier: Modifier = Modifier,
    showRaw: Boolean,
    canSwitchToForm: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = {
            if (showRaw) {
                onToggle(false)
            } else {
                onToggle(true)
            }
        },
        enabled = if (showRaw) canSwitchToForm else true
    ) {
        Text(if (showRaw) "View as Form" else "View as JSON")
    }
}

@Composable
private fun RawJsonEditor(
    modifier: Modifier,
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier.heightIn(min = 160.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text("Raw JSON") },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let { Text(it) }
        }
    )
}

@Composable
private fun ReadOnlyField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 1.dp
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
                softWrap = true,
                overflow = TextOverflow.Visible,
                maxLines = Int.MAX_VALUE
            )
        }
    }
}

@Composable
private fun <T : Any> StringField(
    modifier: Modifier,
    field: EditorField<T>,
    state: T,
    onStateChange: (T) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = field.getter(state) as? String ?: "",
        onValueChange = { updated ->
            onStateChange(field.setter(state, updated))
        },
        label = { Text(field.name) }
    )
}

@Composable
private fun <T : Any> BooleanField(
    modifier: Modifier,
    field: EditorField<T>,
    state: T,
    onStateChange: (T) -> Unit,
) {
    val value = field.getter(state) as? Boolean ?: false
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = field.name, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Switch(
            checked = value,
            onCheckedChange = { checked ->
                onStateChange(field.setter(state, checked))
            }
        )
    }
}

@Composable
private fun <T : Any> NumericField(
    modifier: Modifier,
    field: EditorField<T>,
    type: String,
    state: T,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    onStateChange: (T, String?) -> Unit,
) {
    val currentDisplay = pendingValues[field.name]
        ?: (field.getter(state)?.toString() ?: "")
    OutlinedTextField(
        modifier = modifier,
        value = currentDisplay,
        onValueChange = { updated ->
            pendingValues[field.name] = updated
            val parsed = when (type) {
                "kotlin.Int" -> updated.toIntOrNull()
                "kotlin.Long" -> updated.toLongOrNull()
                "kotlin.Double" -> updated.toDoubleOrNull()
                "kotlin.Float" -> updated.toFloatOrNull()
                else -> null
            }
            if (parsed != null) {
                pendingValues.remove(field.name)
                fieldErrors.remove(field.name)
                onStateChange(field.setter(state, parsed), null)
            } else {
                fieldErrors[field.name] = "Invalid number"
                onStateChange(state, "Invalid number")
            }
        },
        label = { Text(field.name) },
        isError = fieldErrors.containsKey(field.name),
        supportingText = {
            fieldErrors[field.name]?.let { Text(it) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun <T : Any> UnsupportedField(
    modifier: Modifier,
    field: EditorField<T>,
    state: T,
) {
    val renderedValue = remember(field, state) {
        field.getter(state)?.toString() ?: "—"
    }
    ReadOnlyField(
        modifier = modifier,
        label = field.name,
        value = buildString {
            append(renderedValue)
            append("\nType: ")
            append(field.type)
        }
    )
}
