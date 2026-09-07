package eu.kanade.tachiyomi.ui.reader.viewer.text.webview

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.viewer.text.shared.ErrorFormatter
import eu.kanade.tachiyomi.ui.reader.viewer.text.shared.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.novel.TDMR

internal class NovelWebViewInlineFeedback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val evaluateJs: (String) -> Unit,
) {

    private var pendingRetry: (() -> Unit)? = null
    private var autoDismissJob: Job? = null

    fun showInlineLoading(isPrepend: Boolean) {
        val js = """
            (function() {
                var loadingDiv = document.getElementById('$ID_INLINE_LOADING');
                if (!loadingDiv) {
                    loadingDiv = document.createElement('div');
                    loadingDiv.id = '$ID_INLINE_LOADING';
                    loadingDiv.style.textAlign = 'center';
                    loadingDiv.style.padding = '20px';
                    loadingDiv.style.color = '#888';
                    loadingDiv.innerHTML = 'Loading...';
                }

                if ($isPrepend) {
                    document.body.insertBefore(loadingDiv, document.body.firstChild);
                } else {
                    document.body.appendChild(loadingDiv);
                }
            })();
        """.trimIndent()
        evaluateJs(js)
    }

    fun hideInlineLoading(@Suppress("UNUSED_PARAMETER") isPrepend: Boolean = false) {
        val js = """
            (function() {
                var loadingDiv = document.getElementById('$ID_INLINE_LOADING');
                if (loadingDiv) loadingDiv.remove();
            })();
        """.trimIndent()
        evaluateJs(js)
    }

    /** When [onRetry] is non-null the banner persists (no auto-dismiss) until it's tapped. */
    fun showInlineError(message: String, isPrepend: Boolean, onRetry: (() -> Unit)? = null) {
        autoDismissJob?.cancel()
        pendingRetry = onRetry

        scope.launch(Dispatchers.Main) {
            val label = if (onRetry != null) {
                context.stringResource(TDMR.strings.novel_inline_tap_to_retry, message)
            } else {
                context.stringResource(TDMR.strings.novel_inline_tap_to_dismiss, message)
            }
            val escapedLabel = label.jsEscape()
            val onClickJs = if (onRetry != null) {
                "errorDiv.remove(); if (window.Android && window.Android.retryInlineError) window.Android.retryInlineError();"
            } else {
                "errorDiv.remove();"
            }

            val js = """
                (function() {
                    var errorDiv = document.getElementById('$ID_INLINE_ERROR');
                    if (errorDiv) errorDiv.remove();
                    errorDiv = document.createElement('div');
                    errorDiv.id = '$ID_INLINE_ERROR';
                    errorDiv.style.textAlign = 'center';
                    errorDiv.style.padding = '16px';
                    errorDiv.style.color = '#FF5252';
                    errorDiv.style.backgroundColor = 'rgba(255, 82, 82, 0.1)';
                    errorDiv.style.cursor = 'pointer';
                    errorDiv.innerHTML = '$escapedLabel';
                    errorDiv.onclick = function() { $onClickJs };

                    if ($isPrepend) {
                        document.body.insertBefore(errorDiv, document.body.firstChild);
                    } else {
                        document.body.appendChild(errorDiv);
                    }
                })();
            """.trimIndent()
            evaluateJs(js)

            if (onRetry == null) {
                autoDismissJob = scope.launch(Dispatchers.Main) {
                    delay(AUTO_DISMISS_MS)
                    evaluateJs("document.getElementById('$ID_INLINE_ERROR')?.remove();")
                }
            }
        }
    }

    /** Rich variant for an actual load failure: real category/summary + Retry + Copy, not a generic string. */
    fun showInlineError(error: Throwable, isPrepend: Boolean, onRetry: (() -> Unit)? = null) {
        autoDismissJob?.cancel()
        pendingRetry = onRetry
        val fmt = ErrorFormatter.format(error)

        scope.launch(Dispatchers.Main) {
            val escapedCategory = fmt.category.localized(context).jsEscape()
            val escapedSummary = fmt.summary.jsEscape()
            val retryLabel = context.stringResource(TDMR.strings.novel_error_retry).jsEscape()
            val copyLabel = context.stringResource(TDMR.strings.novel_error_copy_details).jsEscape()
            val base64Trace = android.util.Base64.encodeToString(
                fmt.stackTrace.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )

            val retryBtnJs = if (onRetry != null) {
                """
                var retryBtn = document.createElement('button');
                retryBtn.textContent = '$retryLabel';
                retryBtn.style.cssText = 'margin:0 6px;padding:6px 14px;border-radius:6px;border:1px solid #4a90d9;background:transparent;color:#4a90d9;font-size:13px;';
                retryBtn.onclick = function() {
                    errorDiv.remove();
                    if (window.Android && window.Android.retryInlineError) window.Android.retryInlineError();
                };
                btnRow.appendChild(retryBtn);
                """.trimIndent()
            } else {
                ""
            }

            val js = """
                (function() {
                    var errorDiv = document.getElementById('$ID_INLINE_ERROR');
                    if (errorDiv) errorDiv.remove();
                    errorDiv = document.createElement('div');
                    errorDiv.id = '$ID_INLINE_ERROR';
                    errorDiv.style.textAlign = 'center';
                    errorDiv.style.padding = '16px';
                    errorDiv.style.backgroundColor = 'rgba(255, 82, 82, 0.1)';

                    var categoryDiv = document.createElement('div');
                    categoryDiv.textContent = '$escapedCategory';
                    categoryDiv.style.cssText = 'color:#FF5252;font-weight:bold;font-size:14px;margin-bottom:6px;';
                    errorDiv.appendChild(categoryDiv);

                    var summaryDiv = document.createElement('div');
                    summaryDiv.textContent = '$escapedSummary';
                    summaryDiv.style.cssText = 'color:#888;font-size:13px;margin-bottom:10px;word-break:break-word;';
                    errorDiv.appendChild(summaryDiv);

                    var btnRow = document.createElement('div');
                    $retryBtnJs
                    var copyBtn = document.createElement('button');
                    copyBtn.textContent = '$copyLabel';
                    copyBtn.style.cssText = 'margin:0 6px;padding:6px 14px;border-radius:6px;border:1px solid #555;background:transparent;color:inherit;font-size:13px;';
                    copyBtn.onclick = function() {
                        if (window.Android && window.Android.copyToClipboard) window.Android.copyToClipboard('$base64Trace');
                    };
                    btnRow.appendChild(copyBtn);
                    errorDiv.appendChild(btnRow);

                    if ($isPrepend) {
                        document.body.insertBefore(errorDiv, document.body.firstChild);
                    } else {
                        document.body.appendChild(errorDiv);
                    }
                })();
            """.trimIndent()
            evaluateJs(js)
        }
    }

    /** Called from the JS bridge when the user taps a retryable inline error banner. */
    fun retryPendingError() {
        val retry = pendingRetry ?: return
        pendingRetry = null
        retry()
    }

    private fun String.jsEscape(): String = this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    companion object {
        const val ID_INLINE_LOADING = "inline-loading"
        const val ID_INLINE_ERROR = "inline-error"
        private const val AUTO_DISMISS_MS = 8_000L
    }
}
