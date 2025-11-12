package io.github.remote.konfig.debug

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import javax.inject.Inject

/**
 * Applies the Remote Konfig theme supporting both light and dark color schemes.
 */
@Composable
internal fun RemoteConfigTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.surface) {
            content()
        }
    }
}

/**
 * Base dialog fragment that renders a Compose-powered editor for a remote config entry.
 */
abstract class RemoteConfigDialogFragment<T : Any> : DialogFragment() {

    private val overrideStore: OverrideStore
        get() = obtainOverrideStore(requireContext().applicationContext)

    @Inject
    lateinit var remoteConfigProvider: RemoteConfigProvider

    protected abstract val configKey: String

    protected abstract val screenTitle: String

    protected abstract val serializer: KSerializer<T>

    protected abstract val editor: RemoteConfigEditor<T>

    protected open fun createOverrideStore(appContext: Context): OverrideStore = OverrideStore(appContext)

    private fun obtainOverrideStore(appContext: Context): OverrideStore {
        sharedOverrideStore?.let { return it }
        return synchronized(overrideStoreLock) {
            sharedOverrideStore ?: createOverrideStore(appContext).also { created ->
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
                RemoteConfigTheme {
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

    val displayTitle = title
        .takeUnless { it.equals(configKey, ignoreCase = true) }
        ?.replace(configKey, "", ignoreCase = true)
        ?.let { sanitized ->
            var result = sanitized.trim()
            if (result.equals("for", ignoreCase = true)) {
                result = ""
            } else if (result.endsWith(" for", ignoreCase = true)) {
                result = result.dropLast(4).trimEnd()
            }
            while (result.endsWith(":") || result.endsWith("-")) {
                result = result.dropLast(1).trimEnd()
            }
            result
                .trimStart('-', ':')
                .trim()
        }
        ?.takeUnless { it.isBlank() }
    val shouldShowTitle = !displayTitle.isNullOrBlank()

    EditorDialog(
        title = displayTitle.orEmpty(),
        showTitle = shouldShowTitle,
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

                item("raw_remote_value") {
                    ReadOnlyField(label = "Remote Value", value = initialRemoteJson)
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

                item("remote_value") {
                    ReadOnlyField(label = "Remote Value", value = initialRemoteJson)
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
    showTitle: Boolean,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_button")) {
                Text("Cancel")
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("title"),
                contentAlignment = Alignment.Center
            ) {
                if (showTitle) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
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
private fun ReadOnlyField(label: String, value: String, placeholder: String = "Not set") {
    val displayValue = value.ifBlank { placeholder }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
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
    var errorMessage by remember(state) { mutableStateOf<String?>(null) }

    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .testTag(field.label),
        value = text,
        onValueChange = { updated ->
            text = updated

            if (updated.isBlank()) {
                val result = runCatching { field.setter(state, null) }
                result.onSuccess { newState ->
                    errorMessage = null
                    onStateChange(newState)
                }.onFailure {
                    errorMessage = "Field cannot be empty"
                }
                return@OutlinedTextField
            }

            val parsed = parser(updated)
            if (parsed != null) {
                errorMessage = null
                onStateChange(field.setter(state, parsed))
            } else {
                errorMessage = "Invalid number"
            }
        },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let { Text(it) }
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
                    },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface)
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
                        },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface)
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

// region Preview support data

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    RemoteConfigTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

private val PreviewSerializersModule: SerializersModule = SerializersModule {
    polymorphic(SampleChoiceVariant::class) {
        subclass(SampleChoiceVariant.Baseline::class)
        subclass(SampleChoiceVariant.TestVariant::class)
    }
}

private val PreviewJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = PreviewSerializersModule
}

@Serializable
private enum class SampleEnum {
    OPTION_A,
    OPTION_B,
    OPTION_C,
}

@Serializable
private enum class SampleOption {
    OPTION_ONE,
    OPTION_TWO,
    OPTION_THREE,
}

@Serializable
private enum class SampleChoiceImage {
    @SerialName("CROWN")
    CROWN,
}

@Serializable
private enum class SampleChoiceBulletIcon {
    @SerialName("BELL")
    BELL,

    @SerialName("SHIELD")
    SHIELD,

    @SerialName("ROCKET")
    ROCKET,
}

@Serializable
private enum class SkipBehavior(val skipStart: Boolean, val skipMiddle: Boolean) {
    SkippableStart(true, false),
    SkippableMiddle(false, true),
    SkippableStartMiddle(true, true),
    NotSkippable(false, false),
}

@Serializable
private data class PreviewNestedItem(
    val nestedString: String,
    val nestedBool: Boolean,
)

@Serializable
private data class SamplePreviewConfig(
    val title: String,
    val enabled: Boolean,
    val maxItems: Int,
    val expirationMillis: Long,
    val selection: SampleEnum,
    val nested: PreviewNestedItem,
    val nestedList: List<PreviewNestedItem>,
)

@Serializable
private data class SampleEntry(
    val label: String,
    val highlighted: Boolean,
)

@Serializable
private data class SampleDetails(
    val label: String,
    val highlighted: Boolean,
    val summary: SampleEntry,
)

@Serializable
private data class SampleDeeplyNestedConfig(
    val title: String,
    val contactNumber: String,
    val provider: String,
    val region: String,
    val lastUpdatedEpochMillis: Long,
    val option: SampleOption,
    val mode: SkipBehavior,
    val enabled: Boolean,
    val detail: SampleDetails,
    val entries: List<SampleEntry>,
    val tags: List<String>,
)

@Serializable
private data class SampleBulletPoint(
    val icon: SampleChoiceBulletIcon,
    val title: String,
    val description: String,
)

@Serializable
private data class SampleTier(
    val pretext: String,
    val title: String,
    val cta: String,
    val isFree: Boolean,
    val image: SampleChoiceImage,
    val points: List<SampleBulletPoint>,
    val description: String,
)

@Serializable
private sealed interface SampleChoiceVariant {
    val variant: String
    val experimentName: String

    @Serializable
    @SerialName("Baseline")
    data class Baseline(
        override val variant: String,
        override val experimentName: String,
    ) : SampleChoiceVariant

    @Serializable
    @SerialName("TestVariant")
    data class TestVariant(
        override val variant: String,
        override val experimentName: String,
        val tier1: SampleTier,
        val tier2: SampleTier,
    ) : SampleChoiceVariant
}

@Serializable
private data class SampleChoiceConfig(
    @Polymorphic val value: SampleChoiceVariant,
)

@Serializable
private data class SampleResponseD(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SampleResponseD) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

// endregion

// region Preview helpers

private fun samplePreviewConfig(): SamplePreviewConfig = SamplePreviewConfig(
    title = "Preview Title",
    enabled = true,
    maxItems = 3,
    expirationMillis = 1_700_000_000_000L,
    selection = SampleEnum.OPTION_B,
    nested = PreviewNestedItem(nestedString = "Nested label", nestedBool = true),
    nestedList = listOf(
        PreviewNestedItem(nestedString = "Nested child 1", nestedBool = false),
        PreviewNestedItem(nestedString = "Nested child 2", nestedBool = true)
    )
)

private fun sampleDeeplyNestedConfig(): SampleDeeplyNestedConfig = SampleDeeplyNestedConfig(
    title = "Deeply Nested",
    contactNumber = "+1-555-0100",
    provider = "Provider Inc.",
    region = "US",
    lastUpdatedEpochMillis = 1_700_000_000_000L,
    option = SampleOption.OPTION_TWO,
    mode = SkipBehavior.SkippableStartMiddle,
    enabled = true,
    detail = SampleDetails(
        label = "Important Detail",
        highlighted = true,
        summary = SampleEntry(label = "Summary", highlighted = false)
    ),
    entries = listOf(
        SampleEntry(label = "First entry", highlighted = true),
        SampleEntry(label = "Second entry", highlighted = false)
    ),
    tags = listOf("beta", "rollout", "android")
)

private fun sampleChoiceConfig(): SampleChoiceConfig = SampleChoiceConfig(
    value = SampleChoiceVariant.TestVariant(
        variant = "Gold",
        experimentName = "Experiment Gold",
        tier1 = sampleTier("Tier 1", isFree = true),
        tier2 = sampleTier("Tier 2", isFree = false)
    )
)

private fun sampleTier(name: String, isFree: Boolean): SampleTier = SampleTier(
    pretext = "Exclusive",
    title = name,
    cta = "Start now",
    isFree = isFree,
    image = SampleChoiceImage.CROWN,
    points = sampleBulletPoints(name),
    description = "$name benefits"
)

private fun sampleBulletPoints(prefix: String): List<SampleBulletPoint> = listOf(
    SampleBulletPoint(
        icon = SampleChoiceBulletIcon.BELL,
        title = "$prefix notification",
        description = "Stay informed about $prefix"
    ),
    SampleBulletPoint(
        icon = SampleChoiceBulletIcon.SHIELD,
        title = "$prefix protection",
        description = "Extra safety for $prefix"
    ),
    SampleBulletPoint(
        icon = SampleChoiceBulletIcon.ROCKET,
        title = "$prefix boost",
        description = "Accelerate growth with $prefix"
    )
)

private fun sampleEntryFieldEditors(): List<FieldEditor> = listOf(
    StringFieldEditor(
        label = "label",
        getter = { (it as SampleEntry).label },
        setter = { state, value -> (state as SampleEntry).copy(label = value as String) }
    ),
    BooleanFieldEditor(
        label = "highlighted",
        getter = { (it as SampleEntry).highlighted },
        setter = { state, value -> (state as SampleEntry).copy(highlighted = value as Boolean) }
    )
)

private fun sampleDetailsFieldEditor(): ClassFieldEditor = ClassFieldEditor(
    label = "detail",
    getter = { (it as SampleDeeplyNestedConfig).detail },
    setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(detail = value as SampleDetails) },
    nestedFieldEditors = listOf(
        StringFieldEditor(
            label = "label",
            getter = { (it as SampleDetails).label },
            setter = { state, value -> (state as SampleDetails).copy(label = value as String) }
        ),
        BooleanFieldEditor(
            label = "highlighted",
            getter = { (it as SampleDetails).highlighted },
            setter = { state, value -> (state as SampleDetails).copy(highlighted = value as Boolean) }
        ),
        ClassFieldEditor(
            label = "summary",
            getter = { (it as SampleDetails).summary },
            setter = { state, value -> (state as SampleDetails).copy(summary = value as SampleEntry) },
            nestedFieldEditors = sampleEntryFieldEditors()
        )
    )
)

private fun sampleEntriesFieldEditor(): ListFieldEditor = ListFieldEditor(
    label = "entries",
    getter = { (it as SampleDeeplyNestedConfig).entries },
    setter = { state, value ->
        val typed = (value as? List<*>)?.mapNotNull { it as? SampleEntry } ?: emptyList()
        (state as SampleDeeplyNestedConfig).copy(entries = typed)
    },
    defaultItemProvider = { SampleEntry(label = "New entry", highlighted = false) },
    itemEditor = ClassFieldEditor(
        label = "entry",
        getter = { it as? SampleEntry },
        setter = { _, value -> value as? SampleEntry ?: SampleEntry(label = "", highlighted = false) },
        nestedFieldEditors = sampleEntryFieldEditors()
    )
)

private fun sampleTagsFieldEditor(): ListFieldEditor = ListFieldEditor(
    label = "tags",
    getter = { (it as SampleDeeplyNestedConfig).tags },
    setter = { state, value ->
        val typed = (value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        (state as SampleDeeplyNestedConfig).copy(tags = typed)
    },
    defaultItemProvider = { "" },
    itemEditor = StringFieldEditor(
        label = "tag",
        getter = { it as? String ?: "" },
        setter = { _, value -> value as? String ?: "" }
    )
)

private fun sampleBulletPointEditors(): List<FieldEditor> = listOf(
    EnumFieldEditor(
        label = "icon",
        getter = { (it as SampleBulletPoint).icon },
        setter = { state, value -> (state as SampleBulletPoint).copy(icon = value as SampleChoiceBulletIcon) },
        values = SampleChoiceBulletIcon.values().toList()
    ),
    StringFieldEditor(
        label = "title",
        getter = { (it as SampleBulletPoint).title },
        setter = { state, value -> (state as SampleBulletPoint).copy(title = value as String) }
    ),
    StringFieldEditor(
        label = "description",
        getter = { (it as SampleBulletPoint).description },
        setter = { state, value -> (state as SampleBulletPoint).copy(description = value as String) }
    )
)

private fun sampleTierFieldEditors(): List<FieldEditor> = listOf(
    StringFieldEditor(
        label = "pretext",
        getter = { (it as SampleTier).pretext },
        setter = { state, value -> (state as SampleTier).copy(pretext = value as String) }
    ),
    StringFieldEditor(
        label = "title",
        getter = { (it as SampleTier).title },
        setter = { state, value -> (state as SampleTier).copy(title = value as String) }
    ),
    StringFieldEditor(
        label = "cta",
        getter = { (it as SampleTier).cta },
        setter = { state, value -> (state as SampleTier).copy(cta = value as String) }
    ),
    BooleanFieldEditor(
        label = "isFree",
        getter = { (it as SampleTier).isFree },
        setter = { state, value -> (state as SampleTier).copy(isFree = value as Boolean) }
    ),
    EnumFieldEditor(
        label = "image",
        getter = { (it as SampleTier).image },
        setter = { state, value -> (state as SampleTier).copy(image = value as SampleChoiceImage) },
        values = SampleChoiceImage.values().toList()
    ),
    ListFieldEditor(
        label = "points",
        getter = { (it as SampleTier).points },
        setter = { state, value ->
            val typed = (value as? List<*>)?.mapNotNull { it as? SampleBulletPoint } ?: emptyList()
            (state as SampleTier).copy(points = typed)
        },
        defaultItemProvider = { SampleBulletPoint(SampleChoiceBulletIcon.BELL, "Point", "Description") },
        itemEditor = ClassFieldEditor(
            label = "point",
            getter = { it as? SampleBulletPoint },
            setter = { _, value -> value as? SampleBulletPoint ?: SampleBulletPoint(SampleChoiceBulletIcon.BELL, "", "") },
            nestedFieldEditors = sampleBulletPointEditors()
        )
    ),
    StringFieldEditor(
        label = "description",
        getter = { (it as SampleTier).description },
        setter = { state, value -> (state as SampleTier).copy(description = value as String) }
    )
)

private fun sampleChoiceVariantEditors(clazz: KClass<*>): List<FieldEditor> = when (clazz) {
    SampleChoiceVariant.Baseline::class -> listOf(
        StringFieldEditor(
            label = "variant",
            getter = { (it as SampleChoiceVariant.Baseline).variant },
            setter = { state, value -> (state as SampleChoiceVariant.Baseline).copy(variant = value as String) }
        ),
        StringFieldEditor(
            label = "experimentName",
            getter = { (it as SampleChoiceVariant.Baseline).experimentName },
            setter = { state, value -> (state as SampleChoiceVariant.Baseline).copy(experimentName = value as String) }
        )
    )

    SampleChoiceVariant.TestVariant::class -> listOf(
        StringFieldEditor(
            label = "variant",
            getter = { (it as SampleChoiceVariant.TestVariant).variant },
            setter = { state, value -> (state as SampleChoiceVariant.TestVariant).copy(variant = value as String) }
        ),
        StringFieldEditor(
            label = "experimentName",
            getter = { (it as SampleChoiceVariant.TestVariant).experimentName },
            setter = { state, value -> (state as SampleChoiceVariant.TestVariant).copy(experimentName = value as String) }
        ),
        ClassFieldEditor(
            label = "tier1",
            getter = { (it as SampleChoiceVariant.TestVariant).tier1 },
            setter = { state, value -> (state as SampleChoiceVariant.TestVariant).copy(tier1 = value as SampleTier) },
            nestedFieldEditors = sampleTierFieldEditors()
        ),
        ClassFieldEditor(
            label = "tier2",
            getter = { (it as SampleChoiceVariant.TestVariant).tier2 },
            setter = { state, value -> (state as SampleChoiceVariant.TestVariant).copy(tier2 = value as SampleTier) },
            nestedFieldEditors = sampleTierFieldEditors()
        )
    )

    else -> emptyList()
}

private fun sampleChoiceDefaultInstance(clazz: KClass<*>): SampleChoiceVariant? = when (clazz) {
    SampleChoiceVariant.Baseline::class -> SampleChoiceVariant.Baseline(
        variant = "Baseline",
        experimentName = "Control"
    )

    SampleChoiceVariant.TestVariant::class -> SampleChoiceVariant.TestVariant(
        variant = "Test",
        experimentName = "Experiment A",
        tier1 = sampleTier("Tier 1", isFree = true),
        tier2 = sampleTier("Tier 2", isFree = false)
    )

    else -> null
}

private object PreviewDeeplyNestedConfigEditor : RemoteConfigEditor<SampleDeeplyNestedConfig> {
    override val key: String = "sample_deeply_nested"

    override fun defaultInstance(): SampleDeeplyNestedConfig = sampleDeeplyNestedConfig()

    override fun fields(): List<FieldEditor> = listOf(
        StringFieldEditor(
            label = "title",
            getter = { (it as SampleDeeplyNestedConfig).title },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(title = value as String) }
        ),
        StringFieldEditor(
            label = "contactNumber",
            getter = { (it as SampleDeeplyNestedConfig).contactNumber },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(contactNumber = value as String) }
        ),
        StringFieldEditor(
            label = "provider",
            getter = { (it as SampleDeeplyNestedConfig).provider },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(provider = value as String) }
        ),
        StringFieldEditor(
            label = "region",
            getter = { (it as SampleDeeplyNestedConfig).region },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(region = value as String) }
        ),
        LongFieldEditor(
            label = "lastUpdatedEpochMillis",
            getter = { (it as SampleDeeplyNestedConfig).lastUpdatedEpochMillis },
            setter = { state, value ->
                (state as SampleDeeplyNestedConfig).copy(lastUpdatedEpochMillis = (value as Number).toLong())
            }
        ),
        EnumFieldEditor(
            label = "option",
            getter = { (it as SampleDeeplyNestedConfig).option },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(option = value as SampleOption) },
            values = SampleOption.values().toList()
        ),
        EnumFieldEditor(
            label = "mode",
            getter = { (it as SampleDeeplyNestedConfig).mode },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(mode = value as SkipBehavior) },
            values = SkipBehavior.values().toList()
        ),
        BooleanFieldEditor(
            label = "enabled",
            getter = { (it as SampleDeeplyNestedConfig).enabled },
            setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(enabled = value as Boolean) }
        ),
        sampleDetailsFieldEditor(),
        sampleEntriesFieldEditor(),
        sampleTagsFieldEditor()
    )
}

private object PreviewSampleChoiceConfigEditor : RemoteConfigEditor<SampleChoiceConfig> {
    override val key: String = "sample_choice_config"

    override val serializersModule: SerializersModule = PreviewSerializersModule

    override fun defaultInstance(): SampleChoiceConfig = sampleChoiceConfig()

    override fun fields(): List<FieldEditor> = listOf(sampleChoiceFieldEditor())
}

private fun sampleChoiceFieldEditor(): PolymorphicFieldEditor = PolymorphicFieldEditor(
    label = "value",
    getter = { (it as SampleChoiceConfig).value },
    setter = { state, value -> (state as SampleChoiceConfig).copy(value = value as SampleChoiceVariant) },
    subclasses = listOf(SampleChoiceVariant.Baseline::class, SampleChoiceVariant.TestVariant::class),
    nestedFieldEditorsProvider = { clazz -> sampleChoiceVariantEditors(clazz) },
    defaultInstanceProvider = { clazz -> sampleChoiceDefaultInstance(clazz) }
)

// endregion

// region Preview composables

@Preview(name = "Remote Config - Form (Light)", showBackground = true)
@Preview(name = "Remote Config - Form (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewRemoteConfigEditorScreenForm() {
    val json = PreviewJson
    val remote = json.encodeToString(SampleDeeplyNestedConfig.serializer(), sampleDeeplyNestedConfig())
    PreviewSurface {
        RemoteConfigEditorScreen(
            title = "Deeply Nested Config",
            configKey = PreviewDeeplyNestedConfigEditor.key,
            remoteJson = remote,
            overrideJson = "",
            editor = PreviewDeeplyNestedConfigEditor,
            serializer = SampleDeeplyNestedConfig.serializer(),
            json = json,
            onSave = {},
            onShare = {},
            onReset = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Remote Config - Raw JSON (Light)", showBackground = true)
@Preview(name = "Remote Config - Raw JSON (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewRemoteConfigEditorScreenRaw() {
    val json = PreviewJson
    val remote = json.encodeToString(SampleDeeplyNestedConfig.serializer(), sampleDeeplyNestedConfig())
    PreviewSurface {
        RemoteConfigEditorScreen(
            title = "Deeply Nested Config",
            configKey = PreviewDeeplyNestedConfigEditor.key,
            remoteJson = remote,
            overrideJson = "{invalid json}",
            editor = PreviewDeeplyNestedConfigEditor,
            serializer = SampleDeeplyNestedConfig.serializer(),
            json = json,
            onSave = {},
            onShare = {},
            onReset = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Remote Config - Polymorphic (Light)", showBackground = true)
@Preview(name = "Remote Config - Polymorphic (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewRemoteConfigEditorScreenPolymorphic() {
    val json = PreviewJson
    val remote = json.encodeToString(SampleChoiceConfig.serializer(), sampleChoiceConfig())
    PreviewSurface {
        RemoteConfigEditorScreen(
            title = "Choice Config",
            configKey = PreviewSampleChoiceConfigEditor.key,
            remoteJson = remote,
            overrideJson = "",
            editor = PreviewSampleChoiceConfigEditor,
            serializer = SampleChoiceConfig.serializer(),
            json = json,
            onSave = {},
            onShare = {},
            onReset = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Create New Config Dialog (Light)", showBackground = true)
@Preview(name = "Create New Config Dialog (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewCreateNewConfigDialog() {
    RemoteConfigTheme {
        CreateNewConfigDialog(configKey = "sample_key", onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "Editor Dialog (Light)", showBackground = true)
@Preview(name = "Editor Dialog (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewEditorDialog() {
    PreviewSurface {
        EditorDialog(
            title = "Preview Editor",
            showTitle = true,
            onDismiss = {},
            onSave = {},
            onShare = {},
            isRawMode = false,
            isConfirmEnabled = true,
            headerContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Key: sample_key", style = MaterialTheme.typography.labelLarge)
                    Text(text = "Source: Remote", style = MaterialTheme.typography.labelMedium)
                }
            },
            mainContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Main content", style = MaterialTheme.typography.bodyMedium)
                    ReadOnlyField(label = "Remote", value = "{\n  \"title\": \"Preview\"\n}")
                }
            }
        )
    }
}

@Preview(name = "Read Only Field (Light)", showBackground = true)
@Preview(name = "Read Only Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewReadOnlyField() {
    PreviewSurface {
        ReadOnlyField(label = "Remote Value", value = "{\n  \"enabled\": true\n}")
    }
}

@Preview(name = "Field Editor Item (Light)", showBackground = true)
@Preview(name = "Field Editor Item (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewFieldEditorItem() {
    val field = PreviewDeeplyNestedConfigEditor.fields().first()
    PreviewSurface {
        FieldEditorItem(
            modifier = Modifier.fillMaxWidth(),
            fieldEditor = field,
            state = sampleDeeplyNestedConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "String Field (Light)", showBackground = true)
@Preview(name = "String Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewStringField() {
    val field = StringFieldEditor(
        label = "Title",
        getter = { (it as SampleDeeplyNestedConfig).title },
        setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(title = value as String) }
    )
    PreviewSurface {
        StringField(
            modifier = Modifier.fillMaxWidth(),
            field = field,
            state = sampleDeeplyNestedConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "Boolean Field (Light)", showBackground = true)
@Preview(name = "Boolean Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewBooleanField() {
    val field = BooleanFieldEditor(
        label = "Enabled",
        getter = { (it as SampleDeeplyNestedConfig).enabled },
        setter = { state, value -> (state as SampleDeeplyNestedConfig).copy(enabled = value as Boolean) }
    )
    PreviewSurface {
        BooleanField(
            modifier = Modifier.fillMaxWidth(),
            field = field,
            state = sampleDeeplyNestedConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "Numeric Field (Light)", showBackground = true)
@Preview(name = "Numeric Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewNumericField() {
    val field = IntFieldEditor(
        label = "Max Items",
        getter = { (it as SamplePreviewConfig).maxItems },
        setter = { state, value -> (state as SamplePreviewConfig).copy(maxItems = (value as Number).toInt()) }
    )
    PreviewSurface {
        NumericField(
            modifier = Modifier.fillMaxWidth(),
            field = field,
            state = samplePreviewConfig(),
            onStateChange = {},
            parser = { it.toIntOrNull() }
        )
    }
}

@Preview(name = "Enum Field (Light)", showBackground = true)
@Preview(name = "Enum Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewEnumField() {
    val field = EnumFieldEditor(
        label = "Selection",
        getter = { (it as SamplePreviewConfig).selection },
        setter = { state, value -> (state as SamplePreviewConfig).copy(selection = value as SampleEnum) },
        values = SampleEnum.values().toList()
    )
    PreviewSurface {
        EnumField(
            modifier = Modifier.fillMaxWidth(),
            field = field,
            state = samplePreviewConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "ByteArray Field (Light)", showBackground = true)
@Preview(name = "ByteArray Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewByteArrayField() {
    val field = ByteArrayFieldEditor(
        label = "Payload",
        getter = { (it as SampleResponseD).data },
        setter = { state, value ->
            (state as SampleResponseD).copy(data = (value as? ByteArray) ?: ByteArray(0))
        }
    )
    PreviewSurface {
        ByteArrayField(
            modifier = Modifier.fillMaxWidth(),
            field = field,
            state = SampleResponseD(data = "Sample data".encodeToByteArray()),
            onStateChange = {}
        )
    }
}

@Preview(name = "Class Field (Light)", showBackground = true)
@Preview(name = "Class Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewClassField() {
    PreviewSurface {
        ClassField(
            modifier = Modifier.fillMaxWidth(),
            field = sampleDetailsFieldEditor(),
            state = sampleDeeplyNestedConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "List Field (Light)", showBackground = true)
@Preview(name = "List Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewListField() {
    PreviewSurface {
        ListField(
            modifier = Modifier.fillMaxWidth(),
            field = sampleEntriesFieldEditor(),
            state = sampleDeeplyNestedConfig(),
            onStateChange = {}
        )
    }
}

@Preview(name = "List Item View (Light)", showBackground = true)
@Preview(name = "List Item View (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewListItemView() {
    val field = sampleEntriesFieldEditor()
    val state = sampleDeeplyNestedConfig()
    PreviewSurface {
        ListItemView(
            index = 0,
            item = state.entries.first(),
            field = field,
            parentState = state,
            onStateChange = {}
        )
    }
}

@Preview(name = "Polymorphic Field (Light)", showBackground = true)
@Preview(name = "Polymorphic Field (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewPolymorphicField() {
    PreviewSurface {
        PolymorphicField(
            modifier = Modifier.fillMaxWidth(),
            field = sampleChoiceFieldEditor(),
            state = sampleChoiceConfig(),
            onStateChange = {}
        )
    }
}

// endregion
