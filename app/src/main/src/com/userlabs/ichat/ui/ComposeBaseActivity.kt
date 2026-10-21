package com.userlabs.ichat.ui

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.userlabs.ichat.Look
import com.userlabs.ichat.Prefs
import com.userlabs.ichat.ui.theme.IChatTheme

/**
 * Base for every Compose screen: resolves the palette, applies edge-to-edge system bars that match
 * it, and recreates itself when a style setting changed elsewhere (same behaviour as BaseActivity).
 */
abstract class ComposeBaseActivity : ComponentActivity() {

    protected lateinit var look: Look
        private set
    private var revSeen = 0

    /** Address whose per-chat style should be used, or null for the global look. */
    protected open fun lookAddress(): String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Look.theme(this)
        super.onCreate(savedInstanceState)
        look = Look.get(this, lookAddress())
        revSeen = Prefs.rev()
        val style = if (look.dark) {
            SystemBarStyle.dark(AColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.rev() != revSeen) {
            revSeen = Prefs.rev()
            recreate()
        }
    }

    /** Call after changing a style setting from this very screen so it is not rebuilt needlessly. */
    protected fun markSeen() {
        revSeen = Prefs.rev()
    }

    protected fun setThemedContent(content: @Composable () -> Unit) {
        setContent { IChatTheme(look, content) }
    }
}
