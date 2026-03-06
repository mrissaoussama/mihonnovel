package eu.kanade.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun UpdateSelectedDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (fetchChapters: Boolean, fetchDetails: Boolean, ignoreSkipRecentlyUpdated: Boolean) -> Unit,
) {
    var fetchChapters by remember { mutableStateOf(true) }
    var fetchDetails by remember { mutableStateOf(false) }
    var ignoreSkipRecentlyUpdated by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = fetchChapters || fetchDetails,
                onClick = {
                    onDismissRequest()
                    onConfirm(fetchChapters, fetchDetails, ignoreSkipRecentlyUpdated)
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(TDMR.strings.update_selected_title))
        },
        text = {
            Column {
                LabeledCheckbox(
                    label = stringResource(TDMR.strings.update_selected_fetch_chapters),
                    checked = fetchChapters,
                    onCheckedChange = { fetchChapters = it },
                )
                LabeledCheckbox(
                    label = stringResource(TDMR.strings.update_selected_fetch_details),
                    checked = fetchDetails,
                    onCheckedChange = { fetchDetails = it },
                )
                LabeledCheckbox(
                    label = stringResource(TDMR.strings.update_selected_ignore_skip),
                    checked = ignoreSkipRecentlyUpdated,
                    onCheckedChange = { ignoreSkipRecentlyUpdated = it },
                )
            }
        },
    )
}
