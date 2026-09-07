package eu.kanade.tachiyomi.ui.reader.viewer.text.textview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import eu.kanade.tachiyomi.ui.reader.viewer.text.shared.ErrorFormatter
import eu.kanade.tachiyomi.ui.reader.viewer.text.shared.localized
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.novel.TDMR

internal class NovelTextViewInlineFeedback(
    private val context: Context,
    private val contentContainer: LinearLayout,
    private val scope: CoroutineScope,
) {

    private var inlineLoadingView: TextView? = null
    private var inlineErrorView: View? = null

    fun showInlineLoading(isPrepend: Boolean) {
        if (inlineLoadingView != null) return
        inlineLoadingView = TextView(context).apply {
            text = context.stringResource(tachiyomi.i18n.MR.strings.loading)
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
        }
        val view = inlineLoadingView ?: return
        if (isPrepend) {
            contentContainer.addView(view, 0)
        } else {
            contentContainer.addView(view)
        }
    }

    fun hideInlineLoading() {
        inlineLoadingView?.let { contentContainer.removeView(it) }
        inlineLoadingView = null
    }

    fun showInlineError(message: String, isPrepend: Boolean, onRetry: (() -> Unit)? = null) {
        inlineErrorView?.let { contentContainer.removeView(it) }

        val view = TextView(context).apply {
            text = if (onRetry != null) {
                context.stringResource(TDMR.strings.novel_inline_tap_to_retry, message)
            } else {
                context.stringResource(TDMR.strings.novel_inline_tap_to_dismiss, message)
            }
            textSize = 14f
            setTextColor(0xFFFF5252.toInt())
            setBackgroundColor(0x1AFF5252)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 48
                bottomMargin = 48
            }
            setPadding(16, 24, 16, 24)
            setOnClickListener {
                contentContainer.removeView(this)
                inlineErrorView = null
                onRetry?.invoke()
            }
        }
        inlineErrorView = view

        if (isPrepend) {
            contentContainer.addView(view, 0)
        } else {
            contentContainer.addView(view)
        }

        // A retryable banner stays until tapped - that's the only way to retry.
        if (onRetry == null) {
            scope.launch {
                delay(AUTO_DISMISS_MS)
                if (inlineErrorView == view) {
                    contentContainer.removeView(view)
                    inlineErrorView = null
                }
            }
        }
    }

    /** Rich variant for an actual load failure: real category/summary + Retry + Copy, not a generic string. */
    fun showInlineError(error: Throwable, isPrepend: Boolean, onRetry: (() -> Unit)? = null) {
        inlineErrorView?.let { contentContainer.removeView(it) }
        val fmt = ErrorFormatter.format(error)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0x1AFF5252)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 48
                bottomMargin = 48
            }
            setPadding(24, 24, 24, 24)
        }

        val categoryView = TextView(context).apply {
            text = fmt.category.localized(context)
            textSize = 14f
            setTextColor(0xFFFF5252.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val summaryView = TextView(context).apply {
            text = fmt.summary
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        if (onRetry != null) {
            val retryButton = Button(context).apply {
                text = context.stringResource(TDMR.strings.novel_error_retry)
                isAllCaps = false
                textSize = 13f
                setTextColor(0xFF4A90D9.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 16 }
                setOnClickListener {
                    contentContainer.removeView(container)
                    inlineErrorView = null
                    onRetry.invoke()
                }
            }
            buttonRow.addView(retryButton)
        }

        val copyButton = Button(context).apply {
            text = context.stringResource(TDMR.strings.novel_error_copy_details)
            isAllCaps = false
            textSize = 13f
            setOnClickListener {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("error", fmt.stackTrace))
                context.toast(context.stringResource(TDMR.strings.novel_error_copied))
            }
        }
        buttonRow.addView(copyButton)

        container.addView(categoryView)
        container.addView(summaryView)
        container.addView(buttonRow)

        inlineErrorView = container
        if (isPrepend) {
            contentContainer.addView(container, 0)
        } else {
            contentContainer.addView(container)
        }
    }

    companion object {
        private const val AUTO_DISMISS_MS = 8_000L
    }
}
