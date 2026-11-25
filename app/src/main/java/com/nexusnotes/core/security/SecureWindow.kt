package com.nexusnotes.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private var secureCount = 0

/**
 * While shown, blocks screenshots/screen recording and blanks the app in the Recents screen
 * (FLAG_SECURE). Counted, so nested vault screens don't clear it early.
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        secureCount++
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureCount = (secureCount - 1).coerceAtLeast(0)
            if (secureCount == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
