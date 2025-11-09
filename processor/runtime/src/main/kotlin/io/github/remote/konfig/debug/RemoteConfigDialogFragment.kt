package io.github.remote.konfig.debug

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Base64
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.menuAnchor
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

    val fields: List<FieldEditor> = remember(editor) { editor.fields() }

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
                    onRawValidated(decoded)
                    onSave(rawJson)
                }
                .onFailure { throwable ->
                    rawErrorMessage = throwable.message
                }
        } else {
            onSave(json.encodeToString(serializer, currentState))
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
                    val overrideSource = overrideJson?.takeIf { it.isNotBlank() } != null
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
                                rawJson = json.encodeToString(serializer, currentState)
                                rawErrorMessage = null
                                showRawEditor = true
                            } else {
                                runCatching { json.decodeFromString(serializer, rawJson) }
                                    .onSuccess { decoded ->
                                        currentState = decoded
                                        pendingFieldValues.clear()
                                        fieldErrors.clear()
                                        rawErrorMessage = null
                                        showRawEditor = false
                                    }
                                    .onFailure { throwable ->
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
                        }
                    )
                }

                rawJson.takeIf { it.isNotBlank() }?.let { activeJson ->
                    item("raw_active_values") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadOnlyField(label = "Active Value", value = activeJson)
                            if (overrideJson?.isNotBlank() == true) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = onReset
                                ) {
                                    Text("Remove Override")
                                }
                            }
                        }
                    }
                }
            } else {
                item("form_fields") {
                    FieldEditorsColumn(
                        modifier = Modifier.fillMaxWidth(),
                        fieldEditors = fields,
                        state = currentState,
                        onStateChange = { updated ->
                            @Suppress("UNCHECKED_CAST")
                            currentState = updated as T
                        },
                        pendingValues = pendingFieldValues,
                        fieldErrors = fieldErrors,
                        path = ""
                    )
                }
            }

            if (!showRawEditor) {
                item("form_footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        remoteJson?.takeIf { it.isNotBlank() }?.let { payload ->
                            ReadOnlyField(label = "Remote Value", value = payload)
                        }
                        overrideJson?.takeIf { it.isNotBlank() }?.let { payload ->
                            ReadOnlyField(label = "Override Value", value = payload)
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                onClick = onReset
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
        TextButton(
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
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private enum class NumericType { INT, LONG, FLOAT, DOUBLE }

@Composable
private fun FieldEditorsColumn(
    modifier: Modifier,
    fieldEditors: List<FieldEditor>,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fieldEditors.forEachIndexed { index, fieldEditor ->
            FieldEditorItem(
                modifier = Modifier.fillMaxWidth(),
                fieldEditor = fieldEditor,
                state = state,
                onStateChange = onStateChange,
                pendingValues = pendingValues,
                fieldErrors = fieldErrors,
                path = buildFieldPath(path, fieldEditor.label, index)
            )
        }
    }
}

@Composable
private fun FieldEditorItem(
    modifier: Modifier,
    fieldEditor: FieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    when (fieldEditor) {
        is StringFieldEditor -> StringField(modifier, fieldEditor, state, onStateChange)
        is BooleanFieldEditor -> BooleanField(modifier, fieldEditor, state, onStateChange)
        is IntFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path, NumericType.INT)
        is LongFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path, NumericType.LONG)
        is FloatFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path, NumericType.FLOAT)
        is DoubleFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path, NumericType.DOUBLE)
        is EnumFieldEditor -> EnumField(modifier, fieldEditor, state, onStateChange)
        is ByteArrayFieldEditor -> ByteArrayField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path)
        is ClassFieldEditor -> ClassField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path)
        is ListFieldEditor -> ListField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path)
        is PolymorphicFieldEditor -> PolymorphicField(modifier, fieldEditor, state, onStateChange, pendingValues, fieldErrors, path)
    }
}

@Composable
private fun StringField(
    modifier: Modifier,
    field: StringFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = field.getter(state) as? String ?: "",
        onValueChange = { updated ->
            onStateChange(field.setter(state, updated))
        },
        label = { Text(field.label) }
    )
}

@Composable
private fun BooleanField(
    modifier: Modifier,
    field: BooleanFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    val value = field.getter(state) as? Boolean ?: false
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = field.label, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Switch(
            checked = value,
            onCheckedChange = { checked ->
                onStateChange(field.setter(state, checked))
            }
        )
    }
}

@Composable
private fun NumericField(
    modifier: Modifier,
    field: FieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
    type: NumericType,
) {
    val key = path
    val currentText = pendingValues[key] ?: (field.getter(state)?.toString() ?: "")
    OutlinedTextField(
        modifier = modifier,
        value = currentText,
        onValueChange = { updated ->
            pendingValues[key] = updated
            val parsed = when (type) {
                NumericType.INT -> updated.toIntOrNull()
                NumericType.LONG -> updated.toLongOrNull()
                NumericType.FLOAT -> updated.toFloatOrNull()
                NumericType.DOUBLE -> updated.toDoubleOrNull()
            }
            if (parsed != null) {
                pendingValues.remove(key)
                fieldErrors.remove(key)
                onStateChange(field.setter(state, parsed))
            } else {
                fieldErrors[key] = "Invalid number"
            }
        },
        label = { Text(fieldLabel(path, field)) },
        isError = fieldErrors.containsKey(key),
        supportingText = {
            fieldErrors[key]?.let { Text(it) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumField(
    modifier: Modifier,
    field: EnumFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = field.values
    val selected = field.getter(state) as? Enum<*>

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = selected?.name ?: "",
            onValueChange = {},
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onStateChange(field.setter(state, option))
                    }
                )
            }
        }
    }
}

@Composable
private fun ByteArrayField(
    modifier: Modifier,
    field: ByteArrayFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    val key = path
    val existing = pendingValues[key] ?: ((field.getter(state) as? ByteArray)?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
    OutlinedTextField(
        modifier = modifier,
        value = existing,
        onValueChange = { updated ->
            pendingValues[key] = updated
            if (updated.isBlank()) {
                pendingValues.remove(key)
                fieldErrors.remove(key)
                onStateChange(field.setter(state, ByteArray(0)))
            } else {
                val decoded = runCatching { Base64.decode(updated, Base64.DEFAULT) }.getOrNull()
                if (decoded != null) {
                    pendingValues.remove(key)
                    fieldErrors.remove(key)
                    onStateChange(field.setter(state, decoded))
                } else {
                    fieldErrors[key] = "Invalid Base64"
                }
            }
        },
        label = { Text(fieldLabel(path, field)) },
        isError = fieldErrors.containsKey(key),
        supportingText = {
            fieldErrors[key]?.let { Text(it) }
        }
    )
}

@Composable
private fun ClassField(
    modifier: Modifier,
    field: ClassFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    val nestedState = field.getter(state) ?: return UnsupportedField(modifier, field::class.java.simpleName)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = field.label, style = MaterialTheme.typography.titleMedium)
            FieldEditorsColumn(
                modifier = Modifier.fillMaxWidth(),
                fieldEditors = field.nestedFieldEditors,
                state = nestedState,
                onStateChange = { updated ->
                    onStateChange(field.setter(state, updated))
                },
                pendingValues = pendingValues,
                fieldErrors = fieldErrors,
                path = path
            )
        }
    }
}

@Composable
private fun ListField(
    modifier: Modifier,
    field: ListFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    var items by remember(state) {
        mutableStateOf((field.getter(state) as? List<*>)?.map { it ?: field.defaultItemProvider() } ?: emptyList())
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = field.label, style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { index, item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Item ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = {
                                val updated = items.toMutableList().apply { removeAt(index) }
                                items = updated
                                val itemPath = buildFieldPath(path, field.label, index)
                                clearFieldStateForPath(itemPath, pendingValues, fieldErrors)
                                onStateChange(field.setter(state, updated))
                            }) {
                                Text("Remove")
                            }
                        }
                        FieldEditorItem(
                            modifier = Modifier.fillMaxWidth(),
                            fieldEditor = field.itemEditor,
                            state = item ?: field.defaultItemProvider().orEmptyDefault(),
                            onStateChange = { updatedItem ->
                                val updated = items.toMutableList().apply { this[index] = updatedItem }
                                items = updated
                                onStateChange(field.setter(state, updated))
                            },
                            pendingValues = pendingValues,
                            fieldErrors = fieldErrors,
                            path = buildFieldPath(path, field.label, index)
                        )
                    }
                }
            }
            OutlinedButton(onClick = {
                val newItem = field.defaultItemProvider() ?: return@OutlinedButton
                val updated = items + newItem
                items = updated
                onStateChange(field.setter(state, updated))
            }) {
                Text("Add Item")
            }
        }
    }
}

@Composable
private fun PolymorphicField(
    modifier: Modifier,
    field: PolymorphicFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>,
    path: String,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedClass by remember(state) {
        mutableStateOf(field.getter(state)?.let { it::class } ?: field.subclasses.firstOrNull())
    }
    var selectedValue by remember(state) { mutableStateOf(field.getter(state)) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = field.label, style = MaterialTheme.typography.titleMedium)

            if (field.subclasses.isEmpty()) {
                Text("No subclasses available", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedClass?.simpleName ?: "",
                    onValueChange = {},
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    field.subclasses.forEach { subclass ->
                        DropdownMenuItem(
                            text = { Text(subclass.simpleName ?: subclass.toString()) },
                            onClick = {
                                expanded = false
                                selectedClass = subclass
                                val newInstance = field.defaultInstanceProvider(subclass)
                                selectedValue = newInstance
                                if (newInstance != null) {
                                    onStateChange(field.setter(state, newInstance))
                                }
                            }
                        )
                    }
                }
            }

            val clazz = selectedClass
            val value = selectedValue
            if (clazz != null && value != null) {
                FieldEditorsColumn(
                    modifier = Modifier.fillMaxWidth(),
                    fieldEditors = field.nestedFieldEditorsProvider(clazz),
                    state = value,
                    onStateChange = { updated ->
                        selectedValue = updated
                        onStateChange(field.setter(state, updated))
                    },
                    pendingValues = pendingValues,
                    fieldErrors = fieldErrors,
                    path = buildFieldPath(path, clazz.simpleName ?: "type")
                )
            }
        }
    }
}

@Composable
private fun UnsupportedField(modifier: Modifier, reason: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Text(
            text = "Unsupported field ($reason). Edit using JSON mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun buildFieldPath(parent: String, label: String, index: Int? = null): String {
    val sanitized = label.replace(" ", "_")
    val withIndex = index?.let { "$sanitized#$it" } ?: sanitized
    return if (parent.isBlank()) withIndex else "$parent.$withIndex"
}

private fun fieldLabel(path: String, field: FieldEditor): String {
    return field.label.ifBlank { path.substringAfterLast('.') }
}

private fun Any?.orEmptyDefault(): Any = this ?: ""

private fun clearFieldStateForPath(
    prefix: String,
    pendingValues: MutableMap<String, String>,
    fieldErrors: MutableMap<String, String>
) {
    val pendingKeys = pendingValues.keys.filter { it.startsWith(prefix) }
    pendingKeys.forEach { pendingValues.remove(it) }
    val errorKeys = fieldErrors.keys.filter { it.startsWith(prefix) }
    errorKeys.forEach { fieldErrors.remove(it) }
}
