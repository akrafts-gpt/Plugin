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

            override fun fields(): List<EditorField<SampleConfig>> = listOf(
                EditorField(
                    name = "title",
                    type = "kotlin.String",
                    getter = { it.title },
                    setter = { data, value -> data.copy(title = value as String) }
                ),
                EditorField(
                    name = "enabled",
                    type = "kotlin.Boolean",
                    getter = { it.enabled },
                    setter = { data, value -> data.copy(enabled = value as Boolean) }
                ),
                EditorField(
                    name = "count",
                    type = "kotlin.Int",
                    getter = { it.count },
                    setter = { data, value -> data.copy(count = (value as Number).toInt()) }
                ),
                EditorField(
                    name = "unsupported",
                    type = "io.github.remote.konfig.sample.SampleOption",
                    getter = { _ -> null },
                    setter = { data, _ -> data }
                )
            )
        }

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RemoteConfigDialogContent(
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
        composeRule.onAllNodesWithText("Close").assertCountEquals(1)
        composeRule.onAllNodesWithText("Share").assertCountEquals(1)
        composeRule.onAllNodesWithText("Save").assertCountEquals(1)
        composeRule.onNodeWithText("Unsupported field type io.github.remote.konfig.sample.SampleOption. Edit using JSON mode.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("View as JSON").performClick()
        composeRule.onNodeWithText("View as Form").assertIsDisplayed()

        val updated = json.encodeToString(SampleConfig.serializer(), SampleConfig("Updated", true, 42))
        composeRule.onNode(hasSetTextAction()).performTextReplacement(updated)
        composeRule.onNodeWithText("Share").performClick()
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
