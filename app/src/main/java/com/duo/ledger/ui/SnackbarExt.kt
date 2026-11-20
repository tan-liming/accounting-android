package com.duo.ledger.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SNACKBAR_VISIBLE_MILLIS = 3000L

/**
 * Material3 的 showSnackbar 在有 actionLabel 时默认 duration = Indefinite，会一直挂着不消失。
 * 这里用 Indefinite + 定时 dismiss 精确控制成 3 秒，同时保留 action 在这 3 秒内可点。
 * 用户点了 action 或手动关闭时立即取消定时器，避免误关后续弹出的提示。
 */
suspend fun SnackbarHostState.showFor3Seconds(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    coroutineScope {
        val autoDismiss = launch {
            delay(SNACKBAR_VISIBLE_MILLIS)
            currentSnackbarData?.dismiss()
        }
        val result = showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = actionLabel != null,
            duration = SnackbarDuration.Indefinite
        )
        autoDismiss.cancel()
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
