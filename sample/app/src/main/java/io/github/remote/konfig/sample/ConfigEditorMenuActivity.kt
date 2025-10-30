package io.github.remote.konfig.sample

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.remote.konfig.debug.RemoteConfigEditor

class ConfigEditorMenuActivity : AppCompatActivity() {

    private val editorClassNames = listOf(
        "io.github.remote.konfig.generated.WelcomeConfigRemoteConfigEditor",
        "io.github.remote.konfig.generated.SampleProfileConfigRemoteConfigEditor",
        "io.github.remote.konfig.generated.SampleProfileWithOptionConfigRemoteConfigEditor",
        "io.github.remote.konfig.generated.SampleDeeplyNestedConfigRemoteConfigEditor",
        "io.github.remote.konfig.generated.SamplePreviewConfigRemoteConfigEditor",
        "io.github.remote.konfig.generated.SampleMessageEnvelopeRemoteConfigEditor",
        "io.github.remote.konfig.generated.SampleChoiceConfigRemoteConfigEditor",
    )

    private val editors: List<RemoteConfigEditor<*>> by lazy {
        editorClassNames.mapNotNull { className ->
            runCatching {
                val clazz = Class.forName(className)
                @Suppress("UNCHECKED_CAST")
                (clazz.getDeclaredConstructor().newInstance() as RemoteConfigEditor<*>)
            }.getOrElse {
                null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_editor_menu)

        val listView: ListView = findViewById(R.id.editorList)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            editors.map { it.key }
        )
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val editor = editors[position]
            showEditorDetails(editor)
        }
    }

    private fun showEditorDetails(editor: RemoteConfigEditor<*>) {
        val fields = editor.fields()
        val fieldDescriptions = if (fields.isEmpty()) {
            getString(R.string.no_fields_message)
        } else {
            fields.joinToString(separator = "\n") { field ->
                "${field.name}: ${field.type}"
            }
        }

        AlertDialog.Builder(this)
            .setTitle(editor.key)
            .setMessage(fieldDescriptions)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
