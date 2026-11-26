package com.nuvio.app.features.livetv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.RecordingRename
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.action_saving
import nuvio.composeapp.generated.resources.livetv_rec_rename_error
import nuvio.composeapp.generated.resources.livetv_rec_rename_hint
import nuvio.composeapp.generated.resources.livetv_rec_rename_invalid
import nuvio.composeapp.generated.resources.livetv_rec_rename_shared
import nuvio.composeapp.generated.resources.livetv_rec_rename_title
import nuvio.composeapp.generated.resources.livetv_rec_manual_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RenameRecordingDialog(recording: LiveTvRecording, onDismiss: () -> Unit, onSave: suspend (String) -> String?) {
    var title by remember(recording.id, recording.dir) { mutableStateOf(recording.title) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = RecordingRename.title(title) != null
    val failureText = stringResource(Res.string.livetv_rec_rename_error)
    DialogSurface(onDismissRequest = { if (!saving) onDismiss() }, title = stringResource(Res.string.livetv_rec_rename_title)) {
        NuvioInputField(value = title, onValueChange = { if (!saving) { title = it; problem = null } }, placeholder = stringResource(Res.string.livetv_rec_manual_title))
        Text(stringResource(Res.string.livetv_rec_rename_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.textSecondary)
        if (NetworkLibrary.isNetwork(recording)) {
            Text(stringResource(Res.string.livetv_rec_rename_shared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.textSecondary)
        }
        val error = problem ?: stringResource(Res.string.livetv_rec_rename_invalid).takeIf { !valid }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.danger) }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), enabled = !saving, onClick = onDismiss)
            DialogButton(text = stringResource(if (saving) Res.string.action_saving else Res.string.action_save), style = DialogButtonStyle.Primary, enabled = valid && !saving, loading = saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        val result = onSave(title)
                        if (result == null) onDismiss() else problem = result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        problem = failureText
                    } finally { saving = false }
                }
            })
        }
    }
}
