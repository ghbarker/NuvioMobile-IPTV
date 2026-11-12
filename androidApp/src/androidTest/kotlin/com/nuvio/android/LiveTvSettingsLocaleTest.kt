package com.nuvio.android

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.app.MainActivity
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.settings.AppLanguage
import com.nuvio.app.features.settings.SettingsScreen
import com.nuvio.app.features.settings.ThemeSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * English smoke test of the real phone settings and accessible dialog actions.
 * App-language switching is verified separately through normal app navigation and cold launches;
 * this instrumented host does not verify locale switching.
 */
@RunWith(AndroidJUnit4::class)
class LiveTvSettingsLocaleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun englishSettingsDialogsAndPaddingPersistAcrossActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Instrumentation should never write preferences in the installed production app.
        check(context.packageName != "com.nuvio.livetv.mobile")
        ActivityScenario.launch(MainActivity::class.java).use { compose.waitForIdle() }
        val originalLanguage = ThemeSettingsRepository.selectedAppLanguage.value
        var testFailure: Throwable? = null
        try {
            instrumentation.runOnMainSync { ThemeSettingsRepository.setAppLanguage(AppLanguage.ENGLISH) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                showSettings(scenario)
                assertEquals(AppLanguage.ENGLISH, ThemeSettingsRepository.selectedAppLanguage.value)
                compose.onNodeWithText("Playlists").performScrollTo().assertIsDisplayed()
                openSetting("Add M3U playlist")
                compose.onAllNodesWithText("Add M3U playlist").onFirst().assertIsDisplayed()
                compose.onNodeWithText("Cancel").performClick()

                compose.onNodeWithText("Calendar saved channels").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Nothing saved yet").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Setup Sync").performScrollTo().assertIsDisplayed()
                compose.onNode(
                    hasText("Sign in with your Nuvio account to keep your Live TV setup the same on all your devices.") or
                        hasText("Setup Sync works with the official Nuvio sign-in."),
                ).performScrollTo().assertIsDisplayed()

                openSetting("Default sort")
                choice("Newest release").assertIsDisplayed()
                choice("Oldest release").assertIsDisplayed()
                compose.onNodeWithText("Cancel").performClick()

                openSetting("Start early")
                val originalPadding = compose.onNode(isSelected()).fetchSemanticsNode()
                    .config[SemanticsProperties.Text].single().text
                var paddingMayHaveChanged = false
                var paddingFailure: Throwable? = null
                try {
                    choice("None").assertIsDisplayed()
                    choice("1 minute").assertIsDisplayed()
                    paddingMayHaveChanged = true
                    choose("5 minutes")

                    // Recreate before checking selection to verify the choice is retained.
                    scenario.recreate()
                    showSettings(scenario)
                    compose.onNodeWithText("Add M3U playlist").performScrollTo().assertIsDisplayed()
                    openSetting("Start early")
                    choice("5 minutes").assertIsDisplayed()
                    compose.onNode(isSelected() and hasText("5 minutes")).assertIsDisplayed()
                    compose.onNodeWithText("Cancel").performClick()
                } catch (failure: Throwable) {
                    paddingFailure = failure
                    throw failure
                } finally {
                    if (paddingMayHaveChanged) {
                        preserveFailure(paddingFailure) {
                            closeDialogIfOpen()
                            openSetting("Start early")
                            choose(originalPadding)
                            openSetting("Start early")
                            choice(originalPadding).assertIsDisplayed()
                            compose.onNode(isSelected() and hasText(originalPadding)).assertIsDisplayed()
                            compose.onNodeWithText("Cancel").performClick()
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            preserveFailure(testFailure) {
                instrumentation.runOnMainSync { ThemeSettingsRepository.setAppLanguage(originalLanguage) }
            }
        }
    }

    private fun showSettings(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            activity.setContent { NuvioTheme { SettingsScreen(initialPageName = "LiveTv") } }
        }
        compose.waitForIdle()
    }

    private fun preserveFailure(original: Throwable?, cleanup: () -> Unit) {
        try {
            cleanup()
        } catch (failure: Throwable) {
            if (original == null) throw failure
            original.addSuppressed(failure)
        }
    }

    private fun closeDialogIfOpen() {
        if (compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitForIdle()
        }
    }

    private fun choice(text: String): SemanticsNodeInteraction {
        val selectable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(selectable))
            .performScrollToNode(hasText(text))
        return compose.onNode(hasText(text) and selectable)
    }

    private fun openSetting(text: String) {
        // Scrolling aligns a row beneath the fixed header; invoke the row's accessibility action.
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun choose(text: String) {
        choice(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

}
