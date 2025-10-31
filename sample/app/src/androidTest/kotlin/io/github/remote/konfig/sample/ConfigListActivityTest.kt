package io.github.remote.konfig.sample

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigListActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ConfigListActivity>()

    @Test
    fun displaysScreensInAlphabeticalOrder() {
        val expectedTitles = listOf(
            "SampleChoiceConfig for sample_choice_config",
            "SampleDeeplyNestedConfig for sample_deeply_nested",
            "SampleMessageEnvelope for sample_message_envelope",
            "SamplePreviewConfig for sample_preview_config",
            "SampleProfileConfig for sample_profile_config",
            "SampleProfileWithOptionConfig for sample_profile_with_option",
            "WelcomeConfig for welcome",
        )

        val listEntries = composeRule.onAllNodesWithText(" for ", substring = true)

        listEntries.assertCountEquals(expectedTitles.size)
        expectedTitles.forEachIndexed { index, title ->
            listEntries[index].assertTextEquals(title)
        }
    }

    @Test
    fun displaysGeneratedScreensAndOpensDialog() {
        val targetTitle = "SampleChoiceConfig for sample_choice_config"

        composeRule.onNodeWithText(targetTitle).assertIsDisplayed()
        openSampleChoiceDialog(targetTitle)

        composeRule.onNodeWithText("Key: sample_choice_config").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close").assertCountEquals(1)
        composeRule.onAllNodesWithText("Share").assertCountEquals(1)
        composeRule.onAllNodesWithText("Save").assertCountEquals(1)
    }

    @Test
    fun closesDialogWhenCloseClicked() {
        val targetTitle = "SampleChoiceConfig for sample_choice_config"

        openSampleChoiceDialog(targetTitle)

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Key: sample_choice_config").assertDoesNotExist()
    }

    private fun openSampleChoiceDialog(title: String) {
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()
    }
}
