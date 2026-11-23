package com.nexusnotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusnotes.core.design.components.NAV_BACK_TAG
import com.nexusnotes.core.design.components.SCREEN_TITLE_TAG
import com.nexusnotes.feature.editor.EDITOR_BODY_TAG
import com.nexusnotes.feature.editor.EDITOR_PREVIEW_TOGGLE_TAG
import com.nexusnotes.feature.editor.PREVIEW_TAG
import com.nexusnotes.feature.editor.EDITOR_TITLE_TAG
import com.nexusnotes.feature.home.HOME_FAB_TAG
import com.nexusnotes.navigation.TopLevelDestination
import com.nexusnotes.navigation.bottomNavTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun assertTitle(expected: String) {
        rule.waitForIdle()
        rule.onNodeWithTag(SCREEN_TITLE_TAG).assertTextEquals(expected)
    }

    @Test
    fun appStartsOnHome() {
        assertTitle("Nexus Notes")
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.HOME)).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationReachesEveryTab() {
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.FOLDERS)).performClick()
        assertTitle("Folders")
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.PROMPTS)).performClick()
        assertTitle("Prompt Library")
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.SETTINGS)).performClick()
        assertTitle("Settings")
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.HOME)).performClick()
        assertTitle("Nexus Notes")
    }

    @Test
    fun writtenNoteAppearsOnHome() {
        val unique = "Test " + UUID.randomUUID().toString().take(8)
        rule.onNodeWithTag(HOME_FAB_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(EDITOR_TITLE_TAG).performTextInput(unique)
        rule.onNodeWithTag(EDITOR_BODY_TAG).performTextInput("Hello from the UI test")
        rule.onNodeWithTag(NAV_BACK_TAG).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTextExists(unique)
        }
        rule.onNodeWithText(unique).assertIsDisplayed()
    }

    @Test
    fun boldButtonWrapsTextAndPreviewRendersIt() {
        rule.onNodeWithTag(HOME_FAB_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(EDITOR_BODY_TAG).performTextInput("word")
        rule.onNodeWithTag(EDITOR_BODY_TAG).performTextInputSelection(androidx.compose.ui.text.TextRange(0, 4))
        rule.onNodeWithContentDescription("Bold").performClick()
        rule.onNodeWithTag(EDITOR_BODY_TAG).assertTextContains("**word**")
        rule.onNodeWithTag(EDITOR_PREVIEW_TOGGLE_TAG).performClick()
        rule.onNodeWithTag(PREVIEW_TAG).assertIsDisplayed()
        rule.onNodeWithText("word").assertIsDisplayed()
    }

    @Test
    fun settingsOpensAboutAndBackReturnsToSettings() {
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.SETTINGS)).performClick()
        rule.onNodeWithText("About Nexus Notes").performClick()
        assertTitle("About")
        rule.onNodeWithTag(NAV_BACK_TAG).performClick()
        assertTitle("Settings")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
