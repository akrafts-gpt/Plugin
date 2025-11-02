package io.github.remote.konfig.debug

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.parseToJsonElement
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

    val remoteElement = remember(sanitizedRemoteJson) {
        sanitizedRemoteJson?.let { payload ->
            runCatching { json.parseToJsonElement(payload) }.getOrNull()
        }
    }
    val overrideElement = remember(activeOverrideJson) {
        activeOverrideJson?.let { payload ->
            runCatching { json.parseToJsonElement(payload) }.getOrNull()
        }
    }
    val currentSnapshot = remember(currentState, showRawEditor) {
        runCatching { json.encodeToJsonElement(serializer, currentState) }.getOrNull()
    }

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

    EditorLayout(
        title = title,
        actionEnabled = actionEnabled,
        onDismiss = onDismiss,
        onShare = { onShare(sharePayload) },
        onSave = onSaveClick,
        headerContent = {
            Text(
                modifier = Modifier.testTag("config_key"),
                text = "Key: $configKey",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val hasOverride = activeOverrideJson != null
            Text(
                modifier = Modifier.testTag("config_source"),
                text = if (hasOverride) "Source: Override" else "Source: Remote",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RemoteConfigModeToggle(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("raw_json_toggle_button"),
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
    ) {
        if (showRawEditor) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
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
                            ReadOnlyField(
                                modifier = Modifier.testTag("active_value"),
                                label = "Active Value",
                                value = activeJson,
                                jsonElement = runCatching { json.parseToJsonElement(activeJson) }.getOrNull()
                            )
                            if (activeOverrideJson != null) {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reset_to_remote_button"),
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
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

                        else -> ComplexField(
                            modifier = Modifier.fillMaxWidth(),
                            field = field,
                            state = currentState,
                            element = currentSnapshot?.jsonObject?.get(field.name)
                        )
                    }
                }

                item("form_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sanitizedRemoteJson?.let { payload ->
                            ReadOnlyField(
                                modifier = Modifier.testTag("remote_value"),
                                label = "Remote Value",
                                value = payload,
                                jsonElement = remoteElement
                            )
                        }
                        activeOverrideJson?.let { payload ->
                            ReadOnlyField(
                                modifier = Modifier.testTag("override_value"),
                                label = "Override Value",
                                value = payload,
                                jsonElement = overrideElement
                            )
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_to_remote_button"),
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
@Composable
private fun EditorLayout(
    title: String,
    actionEnabled: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                modifier = Modifier.testTag("cancel_button"),
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .testTag("title"),
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.testTag("share_button"),
                    onClick = onShare,
                    enabled = actionEnabled
                ) {
                    Text("Share")
                }
                Button(
                    modifier = Modifier.testTag("save_button"),
                    onClick = onSave,
                    enabled = actionEnabled
                ) {
                    Text("Save")
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            headerContent()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            content()
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
    jsonElement: JsonElement? = null,
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
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (jsonElement != null) {
                    JsonTree(element = jsonElement)
                } else {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                        overflow = TextOverflow.Visible,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
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
private fun <T : Any> ComplexField(
    modifier: Modifier,
    field: EditorField<T>,
    state: T,
    element: JsonElement?,
) {
    val summary = remember(field, state) {
        field.getter(state)?.toString() ?: "—"
    }
    var expanded by remember(field.name) { mutableStateOf(true) }
    Column(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .animateContentSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = field.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Type: ${field.type}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            if (element != null) {
                JsonTree(element = element)
            } else {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun JsonTree(
    element: JsonElement,
    depth: Int = 0,
) {
    val startPadding = (depth * 12).dp
    when (element) {
        is JsonObject -> {
            element.entries.sortedBy { it.key }.forEach { (key, value) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = startPadding, top = 4.dp)
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    JsonTree(element = value, depth = depth + 1)
                }
            }
        }
        is JsonArray -> {
            element.forEachIndexed { index, value ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = startPadding, top = 4.dp)
                ) {
                    Text(
                        text = "[$index]",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    JsonTree(element = value, depth = depth + 1)
                }
            }
        }
        is JsonPrimitive -> {
            val content = if (element.isString) \"${element.content}\" else element.content
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 4.dp),
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        JsonNull -> {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 4.dp),
                text = "null",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
