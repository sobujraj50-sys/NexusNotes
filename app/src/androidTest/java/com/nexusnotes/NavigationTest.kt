package com.nexusnotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusnotes.core.design.components.NAV_BACK_TAG
import com.nexusnotes.core.design.components.SCREEN_TITLE_TAG
import com.nexusnotes.feature.editor.EDITOR_TITLE_TAG
import com.nexusnotes.feature.home.HOME_FAB_TAG
import com.nexusnotes.navigation.TopLevelDestination
import com.nexusnotes.navigation.bottomNavTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun quickNoteOpensReadOnlyEditorAndBackReturnsHome() {
        rule.onNodeWithTag(HOME_FAB_TAG).performClick()
        assertTitle("New note")
        rule.onNodeWithTag(EDITOR_TITLE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(NAV_BACK_TAG).performClick()
        assertTitle("Nexus Notes")
    }

    @Test
    fun settingsOpensAboutAndBackReturnsToSettings() {
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.SETTINGS)).performClick()
        rule.onNodeWithText("About Nexus Notes").performClick()
        assertTitle("About")
        rule.onNodeWithTag(NAV_BACK_TAG).performClick()
        assertTitle("Settings")
    }

    @Test
    fun foldersOpensUnfiledDetail() {
        rule.onNodeWithTag(bottomNavTag(TopLevelDestination.FOLDERS)).performClick()
        rule.onNodeWithText("Unfiled").performClick()
        assertTitle("Unfiled")
    }
}
