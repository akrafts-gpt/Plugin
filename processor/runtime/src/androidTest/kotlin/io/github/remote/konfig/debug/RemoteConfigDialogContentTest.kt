package io.github.remote.konfig.debug

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.remote.konfig.sample.SampleOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteConfigDialogContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun togglesBetweenModesAndSavesJson() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val remotePayload = json.encodeToString(SampleConfig.serializer(), SampleConfig("Remote", true, 7))
        var savedJson: String? = null
        var sharedJson: String? = null

        val editor = object : RemoteConfigEditor<SampleConfig> {
            override val key: String = "sample_config"

            override fun defaultInstance(): SampleConfig = SampleConfig("Default", false, 0)

            override fun fields(): List<FieldEditor> = listOf(
                StringFieldEditor(
                    label = "Title",
                    getter = { (it as SampleConfig).title },
                    setter = { data, value -> (data as SampleConfig).copy(title = value as String) }
                ),
                BooleanFieldEditor(
                    label = "Enabled",
                    getter = { (it as SampleConfig).enabled },
                    setter = { data, value -> (data as SampleConfig).copy(enabled = value as Boolean) }
                ),
                IntFieldEditor(
                    label = "Count",
                    getter = { (it as SampleConfig).count },
                    setter = { data, value -> (data as SampleConfig).copy(count = value as Int) }
                ),
                EnumFieldEditor(
                    label = "Unsupported",
                    getter = { null },
                    setter = { data, _ -> data },
                    values = SampleOption.values().toList()
                )
            )
        }

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RemoteConfigEditorScreen(
                    title = "Sample Config",
                    configKey = editor.key,
                    remoteJson = remotePayload,
                    overrideJson = null,
                    editor = editor,
                    serializer = SampleConfig.serializer(),
                    json = json,
                    onSave = { savedJson = it },
                    onShare = { sharedJson = it },
                    onReset = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Key: sample_config").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cancel").assertCountEquals(1)
        composeRule.onAllNodesWithText("Save").assertCountEquals(1)
        composeRule.onNodeWithText("Unsupported").assertIsDisplayed()

        composeRule.onNodeWithText("View as JSON").performClick()
        composeRule.onNodeWithText("View as Form").assertIsDisplayed()
        composeRule.onAllNodesWithText("Share").assertCountEquals(1)

        val updated = json.encodeToString(SampleConfig.serializer(), SampleConfig("Updated", true, 42))
        composeRule.onNode(hasSetTextAction()).performTextReplacement(updated)
        composeRule.onNodeWithText("Share").performClick()
        composeRule.onNodeWithText("View as Form").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil { sharedJson != null }
        composeRule.waitUntil { savedJson != null }
        assertNotNull(savedJson)
        assertEquals(updated, savedJson)
        assertEquals(updated, sharedJson)

        composeRule.onNodeWithText("View as JSON").assertIsDisplayed()
        composeRule.onNodeWithText("title").assertIsDisplayed()
    }

    @Serializable
    private data class SampleConfig(
        val title: String,
        val enabled: Boolean,
        val count: Int,
    )
}
