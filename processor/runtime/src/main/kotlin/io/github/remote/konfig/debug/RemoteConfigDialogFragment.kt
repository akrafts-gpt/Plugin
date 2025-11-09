package io.github.remote.konfig.debug

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import kotlin.reflect.KClass
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
                MaterialTheme(colorScheme = darkColorScheme()) {
                    RemoteConfigEditorScreen(
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
internal fun <T : Any> RemoteConfigEditorScreen(
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
    val defaultInstance = remember(editor) { editor.defaultInstance() }
    val fieldEditors = remember(editor) { editor.fields() }
    val initialRemoteJson = remoteJson.orEmpty()
    val initialOverrideJson = overrideJson.orEmpty()

    var showCreateDialog by remember {
        mutableStateOf(initialRemoteJson.isBlank() && initialOverrideJson.isBlank())
    }
    var proceedWithEditing by remember { mutableStateOf(!showCreateDialog) }

    if (showCreateDialog) {
        CreateNewConfigDialog(
            configKey = configKey,
            onConfirm = {
                proceedWithEditing = true
                showCreateDialog = false
            },
            onDismiss = onDismiss
        )
    }

    if (!proceedWithEditing) {
        return
    }

    val context = LocalContext.current
    val initialJsonFromRepo = initialOverrideJson.ifBlank { initialRemoteJson }

    var initialErrorMessage by remember { mutableStateOf<String?>(null) }
    val initialState: T = remember(initialJsonFromRepo, json, serializer, defaultInstance) {
        if (initialJsonFromRepo.isBlank()) {
            defaultInstance
        } else {
            runCatching { json.decodeFromString(serializer, initialJsonFromRepo) }
                .onFailure { throwable ->
                    initialErrorMessage = throwable.message?.substringBefore(" at path:")
                }
                .getOrElse { defaultInstance }
        }
    }

    var currentState by remember { mutableStateOf(initialState) }
    var showRawJson by remember { mutableStateOf(initialErrorMessage != null) }
    var rawJsonString by remember {
        mutableStateOf(
            if (initialJsonFromRepo.isNotBlank()) initialJsonFromRepo
            else json.encodeToString(serializer, initialState)
        )
    }
    var rawErrorMessage by remember { mutableStateOf(initialErrorMessage) }

    val isCurrentRawJsonValid = remember(rawJsonString, showRawJson) {
        if (!showRawJson) {
            true
        } else {
            runCatching { json.decodeFromString(serializer, rawJsonString) }
                .fold(
                    onSuccess = {
                        rawErrorMessage = null
                        true
                    },
                    onFailure = { throwable ->
                        rawErrorMessage = throwable.message?.substringBefore(" at path:")
                        false
                    }
                )
        }
    }

    val getCurrentJson: () -> String? = {
        rawErrorMessage = null
        if (showRawJson) {
            runCatching { json.decodeFromString(serializer, rawJsonString) }
                .onFailure { throwable ->
                    rawErrorMessage = throwable.message?.substringBefore(" at path:")
                }
                .getOrNull()
                ?.let { rawJsonString }
        } else {
            runCatching { json.encodeToString(serializer, currentState) }
                .onFailure { throwable ->
                    rawErrorMessage = throwable.message?.substringBefore(" at path:")
                }
                .getOrNull()
        }
    }

    val onToggleRawMode: () -> Unit = toggle@{
        val newMode = !showRawJson
        rawErrorMessage = null
        if (newMode) {
            rawJsonString = json.encodeToString(serializer, currentState)
        } else {
            val result = runCatching { json.decodeFromString(serializer, rawJsonString) }
            if (result.isFailure) {
                rawErrorMessage = result.exceptionOrNull()?.message?.substringBefore(" at path:")
                return@toggle
            }
            currentState = result.getOrThrow()
        }
        showRawJson = newMode
    }

    EditorDialog(
        title = title,
        onDismiss = onDismiss,
        onSave = {
            val payload = getCurrentJson()
            if (payload != null) {
                onSave(payload)
            }
        },
        onShare = {
            val payload = getCurrentJson()
            if (payload != null) {
                onShare(payload)
            }
        },
        isRawMode = showRawJson,
        isConfirmEnabled = if (showRawJson) isCurrentRawJsonValid else true,
        headerContent = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    modifier = Modifier.testTag("config_key"),
                    text = "Key: $configKey",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    modifier = Modifier.testTag("config_source"),
                    text = if (initialOverrideJson.isNotBlank()) "Source: Override" else "Source: Remote",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isCurrentRawJsonValid && showRawJson) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discard_and_create_new_button"),
                        onClick = {
                            val newInstance = editor.defaultInstance()
                            currentState = newInstance
                            rawJsonString = json.encodeToString(serializer, newInstance)
                            showRawJson = false
                            rawErrorMessage = null
                            Toast.makeText(context, "Created new from default", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Discard and create new")
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raw_json_toggle_button"),
                        onClick = onToggleRawMode,
                        enabled = if (showRawJson) isCurrentRawJsonValid else true
                    ) {
                        Text(if (showRawJson) "View as Form" else "View as JSON")
                    }
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showRawJson) {
                item("raw_json") {
                    OutlinedTextField(
                        value = rawJsonString,
                        onValueChange = { newValue ->
                            rawJsonString = newValue
                            rawErrorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raw_json_editor"),
                        label = { Text("Raw JSON") },
                        minLines = 5,
                        isError = !isCurrentRawJsonValid,
                        supportingText = {
                            if (!isCurrentRawJsonValid) {
                                Text(rawErrorMessage ?: "Invalid JSON")
                            }
                        }
                    )
                }

                val activeJson = initialJsonFromRepo.takeIf { it.isNotBlank() }
                if (activeJson != null) {
                    item("active_value") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadOnlyField(
                                label = if (initialOverrideJson.isNotBlank()) {
                                    "Active Value (Overridden on device)"
                                } else {
                                    "Active Value (From Remote)"
                                },
                                value = activeJson
                            )
                            if (initialOverrideJson.isNotBlank()) {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reset_to_remote_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = onReset
                                ) {
                                    Text("Remove Local Value")
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(fieldEditors, key = { index, field -> field.label.ifBlank { index.toString() } }) { _, field ->
                    FieldEditorItem(
                        modifier = Modifier.fillMaxWidth(),
                        fieldEditor = field,
                        state = currentState,
                        onStateChange = { updated ->
                            @Suppress("UNCHECKED_CAST")
                            currentState = updated as T
                        }
                    )
                }

                if (initialRemoteJson.isNotBlank()) {
                    item("remote_value") {
                        ReadOnlyField(label = "Remote Value", value = initialRemoteJson)
                    }
                }
                if (initialOverrideJson.isNotBlank()) {
                    item("override_value") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadOnlyField(label = "Override Value", value = initialOverrideJson)
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_override_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                onClick = onReset
                            ) {
                                Text("Remove Local Value")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNewConfigDialog(
    configKey: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Config?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "No value found for key \"$configKey\".\n\nDo you want to create a new one using the default values?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("create_confirm_button")) {
                Text("Yes, Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("create_cancel_button")) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    isRawMode: Boolean,
    isConfirmEnabled: Boolean,
    headerContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_button")) {
                Text("Cancel")
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("title"))
            if (isRawMode) {
                Button(onClick = onShare, enabled = isConfirmEnabled, modifier = Modifier.testTag("share_button")) {
                    Text("Share")
                }
            } else {
                Button(onClick = onSave, enabled = isConfirmEnabled, modifier = Modifier.testTag("save_button")) {
                    Text("Save")
                }
            }
        }

        headerContent()

        Box(modifier = Modifier.weight(1f)) {
            mainContent()
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
        .padding(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FieldEditorItem(
    modifier: Modifier,
    fieldEditor: FieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    when (fieldEditor) {
        is StringFieldEditor -> StringField(modifier, fieldEditor, state, onStateChange)
        is BooleanFieldEditor -> BooleanField(modifier, fieldEditor, state, onStateChange)
        is IntFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange) { it.toIntOrNull() }
        is LongFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange) { it.toLongOrNull() }
        is FloatFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange) { it.toFloatOrNull() }
        is DoubleFieldEditor -> NumericField(modifier, fieldEditor, state, onStateChange) { it.toDoubleOrNull() }
        is EnumFieldEditor -> EnumField(modifier, fieldEditor, state, onStateChange)
        is ByteArrayFieldEditor -> ByteArrayField(modifier, fieldEditor, state, onStateChange)
        is ClassFieldEditor -> ClassField(modifier, fieldEditor, state, onStateChange)
        is ListFieldEditor -> ListField(modifier, fieldEditor, state, onStateChange)
        is PolymorphicFieldEditor -> PolymorphicField(modifier, fieldEditor, state, onStateChange)
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
        modifier = modifier
            .fillMaxWidth()
            .testTag(field.label),
        value = field.getter(state) as? String ?: "",
        onValueChange = { updated -> onStateChange(field.setter(state, updated)) },
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Switch(checked = value, onCheckedChange = { onStateChange(field.setter(state, it)) })
        Text(text = field.label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumericField(
    modifier: Modifier,
    field: FieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
    parser: (String) -> Number?,
) {
    var text by remember(state) { mutableStateOf(field.getter(state)?.toString().orEmpty()) }
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .testTag(field.label),
        value = text,
        onValueChange = { updated ->
            text = updated
            parser(updated)?.let { number ->
                onStateChange(field.setter(state, number))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        label = { Text(field.label) }
    )
}

@Composable
private fun EnumField(
    modifier: Modifier,
    field: EnumFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = field.getter(state) as? Enum<*>
    Box(modifier = modifier
        .fillMaxWidth()
        .clickable { expanded = true }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            label = { Text(field.label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(field.label),
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.name) },
                    onClick = {
                        expanded = false
                        onStateChange(field.setter(state, value))
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
) {
    var text by remember(state) {
        mutableStateOf(
            (field.getter(state) as? ByteArray)?.let { Base64.encodeToString(it, Base64.DEFAULT) } ?: ""
        )
    }
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .testTag(field.label),
        value = text,
        onValueChange = { updated ->
            text = updated
            val decoded = if (updated.isBlank()) {
                ByteArray(0)
            } else {
                runCatching { Base64.decode(updated, Base64.DEFAULT) }.getOrNull()
            }
            decoded?.let { onStateChange(field.setter(state, it)) }
        },
        label = { Text(field.label) },
        supportingText = {
            Text("Value is encoded as Base64")
        }
    )
}

@Composable
private fun ClassField(
    modifier: Modifier,
    field: ClassFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    val nestedState = field.getter(state)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = field.label, style = MaterialTheme.typography.bodySmall)
        field.nestedFieldEditors.forEach { nestedEditor ->
            FieldEditorItem(
                modifier = Modifier.fillMaxWidth(),
                fieldEditor = nestedEditor,
                state = nestedState ?: return@forEach,
                onStateChange = { updated ->
                    val updatedContainer = field.setter(state, updated)
                    onStateChange(updatedContainer)
                }
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
) {
    val items = (field.getter(state) as? List<Any?>).orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = field.label, style = MaterialTheme.typography.bodySmall)
        if (items.isEmpty()) {
            Button(onClick = {
                val newItem = field.defaultItemProvider() ?: return@Button
                onStateChange(field.setter(state, listOf(newItem)))
            }) {
                Text("Add first item")
            }
        } else {
            items.forEachIndexed { index, item ->
                key("list-${'$'}index-${'$'}{System.identityHashCode(item)}") {
                    ListItemView(
                        index = index,
                        item = item,
                        field = field,
                        parentState = state,
                        onStateChange = onStateChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ListItemView(
    index: Int,
    item: Any?,
    field: ListFieldEditor,
    parentState: Any,
    onStateChange: (Any) -> Unit,
) {
    val resolvedItem = item ?: field.defaultItemProvider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Item[$index]", style = MaterialTheme.typography.titleSmall)
            Row {
                IconButton(onClick = {
                    val newItem = field.defaultItemProvider() ?: return@IconButton
                    val updated = (field.getter(parentState) as? List<Any?>).orEmpty().toMutableList().apply {
                        add(index + 1, newItem)
                    }
                    onStateChange(field.setter(parentState, updated))
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item after")
                }
                IconButton(onClick = {
                    val updated = (field.getter(parentState) as? List<Any?>).orEmpty().toMutableList().apply {
                        removeAt(index)
                    }
                    onStateChange(field.setter(parentState, updated))
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove item")
                }
            }
        }

        if (resolvedItem != null) {
            FieldEditorItem(
                modifier = Modifier.fillMaxWidth(),
                fieldEditor = field.itemEditor,
                state = resolvedItem,
                onStateChange = { updatedItem ->
                    val updated = (field.getter(parentState) as? List<Any?>).orEmpty().toMutableList().apply {
                        this[index] = updatedItem
                    }
                    onStateChange(field.setter(parentState, updated))
                }
            )
        } else {
            Text(
                text = "Unable to render item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PolymorphicField(
    modifier: Modifier,
    field: PolymorphicFieldEditor,
    state: Any,
    onStateChange: (Any) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedClass by remember(state) {
        mutableStateOf(field.getter(state)?.let { it::class } ?: field.subclasses.firstOrNull())
    }
    var selectedValue by remember(state) { mutableStateOf(field.getter(state)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = field.label, style = MaterialTheme.typography.bodySmall)
        Box(modifier = Modifier.clickable { expanded = true }) {
            OutlinedTextField(
                value = selectedClass?.simpleName ?: "Select Type",
                onValueChange = {},
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                field.subclasses.forEach { subclass ->
                    DropdownMenuItem(
                        text = { Text(subclass.simpleName ?: subclass.toString()) },
                        onClick = {
                            expanded = false
                            selectedClass = subclass
                            val instance = field.defaultInstanceProvider(subclass)
                            selectedValue = instance
                            onStateChange(field.setter(state, instance))
                        }
                    )
                }
            }
        }

        val clazz: KClass<*>? = selectedClass
        val value = selectedValue
        if (clazz != null && value != null) {
            field.nestedFieldEditorsProvider(clazz).forEach { nested ->
                FieldEditorItem(
                    modifier = Modifier.fillMaxWidth(),
                    fieldEditor = nested,
                    state = value,
                    onStateChange = { updated ->
                        selectedValue = updated
                        onStateChange(field.setter(state, updated))
                    }
                )
            }
        }
    }
}
