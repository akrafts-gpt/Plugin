package io.github.remote.konfig.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import kotlinx.serialization.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * Base dialog fragment that renders a Compose-powered editor for a remote config entry.
 */
abstract class RemoteConfigDialogFragment<T : Any> : DialogFragment() {

    protected open val logTag: String = "RemoteConfigDialog"

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

        Log.d(logTag, "Loaded remote config for '$configKey': ${remoteJson?.takeIf { it.isNotBlank() } ?: "<empty>"}")
        Log.d(logTag, "Loaded override for '$configKey': ${overrideJson?.takeIf { it.isNotBlank() } ?: "<empty>"}")
        val dialogJson = createJson()
        val context = requireContext()

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
                            Log.d(logTag, "Persisting override for '$configKey': $updatedJson")
                            overrideStore.setOverride(configKey, updatedJson)
                            Toast.makeText(context, "Override saved", Toast.LENGTH_SHORT).show()
                            dismissAllowingStateLoss()
                        },
                        onShare = { payload ->
                            Log.d(logTag, "Sharing payload for '$configKey'")
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share config JSON"))
                        },
                        onReset = {
                            Log.d(logTag, "Clearing override for '$configKey'")
                            overrideStore.clearOverride(configKey)
                            Toast.makeText(context, "Override cleared", Toast.LENGTH_SHORT).show()
                            dismissAllowingStateLoss()
                        },
                        logTag = logTag,
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
    logTag: String,
    onDismiss: () -> Unit,
) {
    val defaultState = remember(editor) { editor.defaultInstance() }
    val initialJson = overrideJson?.takeIf { it.isNotBlank() }
        ?: remoteJson?.takeIf { it.isNotBlank() }
    val (startingState, initialError) = remember(initialJson, json, serializer) {
        if (initialJson == null) {
            defaultState to null
        } else {
            runCatching { json.decodeFromString(serializer, initialJson) }
                .fold(
                    onSuccess = { it to null },
                    onFailure = { defaultState to it }
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
    var overrideDisplayValue by remember(overrideJson) {
        mutableStateOf(overrideJson?.takeIf { it.isNotBlank() })
    }

    val fields: List<EditorField<T>> = remember(editor) { editor.fields() }

    val localLogTag = remember(logTag) { logTag }

    val onRawValidated: (T) -> Unit = { decoded ->
        currentState = decoded
        pendingFieldValues.clear()
        fieldErrors.clear()
        showRawEditor = false
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
                    Log.d(localLogTag, "[$configKey] Raw JSON validated successfully")
                    onRawValidated(decoded)
                    onSave(rawJson)
                    overrideDisplayValue = rawJson
                }
                .onFailure { throwable ->
                    Log.e(localLogTag, "[$configKey] Failed to decode raw JSON", throwable)
                    rawErrorMessage = throwable.message
                }
        } else {
            val serialized = json.encodeToString(serializer, currentState)
            Log.d(localLogTag, "[$configKey] Saving form state")
            onSave(serialized)
            overrideDisplayValue = serialized
        }
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
                    val overrideSource = overrideDisplayValue != null
                    Text(
                        text = if (overrideSource) "Source: Override" else "Source: Remote",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RemoteConfigModeToggle(
                        modifier = Modifier.fillMaxWidth(),
                        showRaw = showRawEditor,
                        canSwitchToForm = rawErrorMessage == null,
                        onToggle = { shouldShowRaw ->
                            if (shouldShowRaw) {
                                Log.d(localLogTag, "[$configKey] Switching to raw JSON editor")
                                rawJson = json.encodeToString(serializer, currentState)
                                rawErrorMessage = null
                                showRawEditor = true
                            } else {
                                runCatching { json.decodeFromString(serializer, rawJson) }
                                    .onSuccess { decoded ->
                                        Log.d(localLogTag, "[$configKey] Returning to form editor")
                                        currentState = decoded
                                        pendingFieldValues.clear()
                                        fieldErrors.clear()
                                        rawErrorMessage = null
                                        showRawEditor = false
                                    }
                                    .onFailure { throwable ->
                                        Log.e(localLogTag, "[$configKey] Unable to parse raw JSON when switching to form", throwable)
                                        rawErrorMessage = throwable.message
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
                            if (rawErrorMessage == null) {
                                Log.d(localLogTag, "[$configKey] Raw JSON input is valid")
                            }
                        }
                    )
                }

                rawJson.takeIf { it.isNotBlank() }?.let { activeJson ->
                    item("raw_active_values") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadOnlyField(label = "Active Value", value = activeJson)
                            if (overrideDisplayValue?.isNotBlank() == true) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = {
                                        Log.d(localLogTag, "[$configKey] Clearing override from raw editor")
                                        overrideDisplayValue = null
                                        onReset()
                                    }
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

                        else -> JsonPreviewField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            state = currentState,
                            json = json,
                            serializer = serializer
                        )
                    }
                }
            }

            if (!showRawEditor) {
                item("form_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        remoteJson?.takeIf { it.isNotBlank() }?.let { payload ->
                            ReadOnlyField(label = "Remote Value", value = payload)
                        }
                        overrideDisplayValue?.takeIf { it.isNotBlank() }?.let { payload ->
                            ReadOnlyField(label = "Override Value", value = payload)
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                onClick = {
                                    Log.d(localLogTag, "[$configKey] Clearing override from footer")
                                    overrideDisplayValue = null
                                    onReset()
                                }
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
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                softWrap = true
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
private fun <T : Any> JsonPreviewField(
    modifier: Modifier,
    field: EditorField<T>,
    state: T,
    json: Json,
    serializer: KSerializer<T>,
) {
    val previewValue = remember(state, json) {
        fieldJsonValue(json, serializer, state, field.name)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadOnlyField(
            label = field.name,
            value = previewValue ?: "Value unavailable"
        )
        Text(
            text = "Editing ${field.type} is not supported in form mode. Use JSON mode for changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun <T : Any> fieldJsonValue(
    json: Json,
    serializer: KSerializer<T>,
    state: T,
    fieldName: String,
): String? {
    return runCatching {
        val encoded = json.encodeToJsonElement(serializer, state)
        val jsonObject = encoded as? JsonObject ?: return null
        val property = jsonObject[fieldName] ?: return null
        json.encodeToString(JsonElement.serializer(), property)
    }.getOrNull()
}
