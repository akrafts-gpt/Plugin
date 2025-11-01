package io.github.remote.konfig.debug

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
        composeRule.onNodeWithText("Type: io.github.remote.konfig.sample.SampleOption", substring = true)
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

    @Test
    fun displaysNestedValuesForComplexFields() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        val editor = object : RemoteConfigEditor<NestedConfig> {
            override val key: String = "nested_config"

            override fun defaultInstance(): NestedConfig = NestedConfig(
                title = "Nested Title",
                child = NestedChild(message = "Nested detail", flag = true)
            )

            override fun fields(): List<EditorField<NestedConfig>> = listOf(
                EditorField(
                    name = "title",
                    type = "kotlin.String",
                    getter = { it.title },
                    setter = { data, value -> data.copy(title = value as String) }
                ),
                EditorField(
                    name = "child",
                    type = NestedChild::class.qualifiedName ?: "NestedChild",
                    getter = { it.child },
                    setter = { data, value -> data.copy(child = value as NestedChild) }
                )
            )
        }

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RemoteConfigDialogContent(
                    title = "Nested Config",
                    configKey = editor.key,
                    remoteJson = null,
                    overrideJson = null,
                    editor = editor,
                    serializer = NestedConfig.serializer(),
                    json = json,
                    onSave = {},
                    onShare = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("child").assertIsDisplayed()
        composeRule.onNodeWithText("NestedChild(message=Nested detail, flag=true)", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Type: ${NestedChild::class.qualifiedName}", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun savingFormUpdatesOverrideValueOnly() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val remoteConfig = FormConfig("Remote Title", true, 7)
        val remotePayload = json.encodeToString(FormConfig.serializer(), remoteConfig)
        var savedJson: String? = null

        val editor = object : RemoteConfigEditor<FormConfig> {
            override val key: String = "form_config"

            override fun defaultInstance(): FormConfig = FormConfig("Default", false, 0)

            override fun fields(): List<EditorField<FormConfig>> = listOf(
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
                )
            )
        }

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RemoteConfigDialogContent(
                    title = "Form Config",
                    configKey = editor.key,
                    remoteJson = remotePayload,
                    overrideJson = null,
                    editor = editor,
                    serializer = FormConfig.serializer(),
                    json = json,
                    onSave = { savedJson = it },
                    onShare = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction() and hasText("Remote Title")).performTextReplacement("Updated Title")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil { savedJson != null }
        val expectedOverride = json.encodeToString(FormConfig.serializer(), remoteConfig.copy(title = "Updated Title"))
        assertEquals(expectedOverride, savedJson)

        composeRule.onNodeWithText("Remote Value").assertIsDisplayed()
        composeRule.onNodeWithText("\"title\": \"Remote Title\"", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Override Value").assertIsDisplayed()
        composeRule.onNodeWithText("\"title\": \"Updated Title\"", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Serializable
    private data class SampleConfig(
        val title: String,
        val enabled: Boolean,
        val count: Int,
    )

    @Serializable
    private data class NestedConfig(
        val title: String,
        val child: NestedChild,
    )

    @Serializable
    private data class NestedChild(
        val message: String,
        val flag: Boolean,
    )

    @Serializable
    private data class FormConfig(
        val title: String,
        val enabled: Boolean,
        val count: Int,
    )
}
