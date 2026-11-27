package com.nuvio.app.features.livetv.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.RecordingStatus
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
// Width qualifiers make Robolectric loop on text-field dialogs: robolectric/robolectric#8460.
@Config(sdk = [34], application = Application::class, qualifiers = "h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenameRecordingDialogTest {
    @get:Rule val compose = createComposeRule()
    private val rec = LiveTvRecording("synthetic", "channel", "PPV", null, "No program guide available",
        startedAtMs = 10L, stopAtMs = 20L, status = RecordingStatus.DONE, dir = "/synthetic")

    @Test fun cancelNeverCallsSaveAndBlankOrOversizedTitlesDisableSaving() {
        var calls = 0
        var dismissals = 0
        compose.setContent { NuvioTheme { RenameRecordingDialog(rec, onDismiss = { dismissals++ }, onSave = { calls++; null }) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("x".repeat(201))
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls); assertEquals(1, dismissals) }
    }

    @Test fun failureStaysVisibleAndRetryClosesOnlyAfterSuccessfulSave() {
        val open = mutableStateOf(true)
        val saves = mutableListOf<String>()
        compose.setContent { NuvioTheme {
            if (open.value) RenameRecordingDialog(rec, onDismiss = { open.value = false }, onSave = {
                saves += it
                if (saves.size == 1) "Synthetic storage failure" else null
            })
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Weekend fight")
        exportDialog("mobile-recording-rename")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Synthetic storage failure").assertIsDisplayed()
        exportDialog("mobile-recording-rename-retry")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("Weekend fight", "Weekend fight"), saves); assertEquals(false, open.value) }
    }

    @Test fun sharedRecordingExplainsThatItsTitleChangesOnOtherDevices() {
        compose.setContent { NuvioTheme { RenameRecordingDialog(rec.copy(dir = "net:synthetic"), onDismiss = {}, onSave = { null }) } }
        compose.onNodeWithText("This shared recording's title will also change on your other devices.").assertIsDisplayed()
        exportDialog("mobile-recording-rename-shared")
    }

    /** Optional local review artifacts; neither CI nor the behavior assertions require screenshots. */
    private fun exportDialog(name: String) {
        val folder = System.getenv("NUVIO_TEST_SCREENSHOTS")?.takeIf { it.isNotBlank() } ?: return
        val target = File(folder, "$name.png")
        runCatching {
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val signature = target.inputStream().use { it.readNBytes(8) }
            check(signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        }.onFailure {
            target.delete()
            System.err.println("Optional Rename screenshot unavailable: $name (${it.javaClass.simpleName})")
        }
    }
}
