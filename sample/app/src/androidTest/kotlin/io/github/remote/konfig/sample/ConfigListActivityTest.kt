package io.github.remote.konfig.sample

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.dagger.hilt.android.testing.HiltAndroidRule
import com.google.dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConfigListActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeRule = createAndroidComposeRule<ConfigListActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun displaysGeneratedScreensAndOpensDialog() {
        val targetTitle = "SampleChoiceConfig for sample_choice_config"

        composeRule.onNodeWithText(targetTitle).assertIsDisplayed()
        composeRule.onNodeWithText(targetTitle).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Key: sample_choice_config").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close").assertCountEquals(1)
        composeRule.onAllNodesWithText("Share").assertCountEquals(1)
        composeRule.onAllNodesWithText("Save").assertCountEquals(1)
    }
}
