package eu.kanade.presentation.library.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Options for EPUB export
 */
data class EpubExportOptions(
    val downloadedOnly: Boolean = false,
    val preferTranslated: Boolean = false,
    // Filename options
    val includeChapterCount: Boolean = false,
    val includeChapterRange: Boolean = false,
    val includeStatus: Boolean = false,
)

@Composable
fun BatchExportEpubDialog(
    mangaList: List<Manga>,
    onDismissRequest: () -> Unit,
    onExport: (Uri, EpubExportOptions) -> Unit,
) {
    val isSingleNovel = mangaList.size == 1
    var filename by remember {
        mutableStateOf(
            if (isSingleNovel) {
                sanitizeFilename(mangaList.first().title) + ".epub"
            } else {
                "novels_export.zip"
            },
        )
    }
    var downloadedOnly by remember { mutableStateOf(false) }
    var preferTranslated by remember { mutableStateOf(false) }
    var includeChapterCount by remember { mutableStateOf(false) }
    var includeChapterRange by remember { mutableStateOf(false) }
    var includeStatus by remember { mutableStateOf(false) }

    val mimeType = if (isSingleNovel) "application/epub+zip" else "application/zip"

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { uri ->
        if (uri != null) {
            onExport(
                uri,
                EpubExportOptions(
                    downloadedOnly = downloadedOnly,
                    preferTranslated = preferTranslated,
                    includeChapterCount = includeChapterCount,
                    includeChapterRange = includeChapterRange,
                    includeStatus = includeStatus,
                ),
            )
            onDismissRequest()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(TDMR.strings.action_export_epub)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(TDMR.strings.epub_novels_export_count, mangaList.size),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // List the novels to be exported
                mangaList.take(5).forEach { manga ->
                    Text(
                        text = "• ${manga.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (mangaList.size > 5) {
                    Text(
                        text = stringResource(TDMR.strings.epub_and_more, mangaList.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(stringResource(TDMR.strings.epub_filename)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Export options
                Text(
                    text = stringResource(TDMR.strings.epub_content_options),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                CheckboxItem(
                    label = stringResource(TDMR.strings.epub_downloaded_only),
                    checked = downloadedOnly,
                    onClick = { downloadedOnly = !downloadedOnly },
                )

                CheckboxItem(
                    label = stringResource(TDMR.strings.epub_prefer_translated),
                    checked = preferTranslated,
                    onClick = { preferTranslated = !preferTranslated },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(TDMR.strings.epub_filename_options),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                CheckboxItem(
                    label = stringResource(TDMR.strings.epub_include_chapter_count),
                    checked = includeChapterCount,
                    onClick = { includeChapterCount = !includeChapterCount },
                )

                CheckboxItem(
                    label = stringResource(TDMR.strings.epub_include_chapter_range),
                    checked = includeChapterRange,
                    onClick = { includeChapterRange = !includeChapterRange },
                )

                CheckboxItem(
                    label = stringResource(TDMR.strings.epub_include_status),
                    checked = includeStatus,
                    onClick = { includeStatus = !includeStatus },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (downloadedOnly) {
                        stringResource(TDMR.strings.epub_downloaded_only_info)
                    } else {
                        stringResource(TDMR.strings.epub_source_info)
                    } + if (preferTranslated) {
                        stringResource(TDMR.strings.epub_translated_info)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    launcher.launch(filename)
                },
            ) {
                Text(stringResource(TDMR.strings.action_export_epub))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun sanitizeFilename(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(200) // Limit filename length
}
