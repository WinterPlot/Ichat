package com.userlabs.ichat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.userlabs.ichat.ui.ComposeBaseActivity
import com.userlabs.ichat.ui.components.GroupCard
import com.userlabs.ichat.ui.components.IChatTopBar
import com.userlabs.ichat.ui.components.LinkRow
import com.userlabs.ichat.ui.components.RowDivider
import com.userlabs.ichat.ui.components.SectionLabel
import com.userlabs.ichat.ui.components.ToggleRow

/** Settings: appearance shortcuts, messaging behaviour, notifications, privacy and about. */
class SettingsActivity : ComposeBaseActivity() {

    /** Bumped on resume so the "default app" / "blocked" subtitles re-read their sources. */
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent { SettingsScreen() }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == REQ_PHONE) {
            Sims.invalidate()
            recreate()
        }
    }

    private fun go(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun openSystemNotifications() {
        try {
            val i = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            }
            startActivity(i)
        } catch (e: Exception) {
            Ui.toast(this, R.string.no_app)
        }
    }

    @Composable
    private fun Pref(title: Int, sub: Int, key: String, def: Boolean, appearance: Boolean) {
        var on by remember { mutableStateOf(Prefs.getBool(this, key, def)) }
        ToggleRow(stringResource(title), stringResource(sub), on) {
            on = it
            Prefs.putBool(this, key, it)
            if (appearance) {
                Prefs.touch()
                markSeen()
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        var quickDialog by remember { mutableStateOf(false) }
        var blockedDialog by remember { mutableStateOf(false) }
        val tick = refresh // re-read dynamic subtitles after resume

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                IChatTopBar(stringResource(R.string.menu_settings), onBack = { finish() })
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp),
                ) {
                    SectionLabel(stringResource(R.string.set_sec_appearance))
                    GroupCard {
                        LinkRow(Icons.Rounded.Palette, stringResource(R.string.menu_chat_style), stringResource(R.string.set_style_sub)) { go(StyleActivity::class.java) }
                        RowDivider()
                        Pref(R.string.set_group, R.string.set_group_sub, "group_msgs", true, true)
                        Pref(R.string.set_avatars, R.string.set_avatars_sub, "show_avatars", true, true)
                        Pref(R.string.set_time_always, R.string.set_time_always_sub, "time_always", false, true)
                        Pref(R.string.set_time24, R.string.set_time24_sub, "time24", true, true)
                    }

                    SectionLabel(stringResource(R.string.set_sec_messaging))
                    GroupCard {
                        Pref(R.string.set_smart, R.string.set_smart_sub, "smart_replies", true, false)
                        LinkRow(Icons.Rounded.Edit, stringResource(R.string.set_quick), stringResource(R.string.set_quick_sub)) { quickDialog = true }
                        RowDivider()
                        Pref(R.string.set_haptics, R.string.set_haptics_sub, "haptics", true, false)
                    }

                    SectionLabel(stringResource(R.string.set_sec_sim))
                    GroupCard {
                        Pref(R.string.set_sim_remember, R.string.set_sim_remember_sub, "sim_remember", true, false)
                        LinkRow(
                            Icons.Rounded.SimCard,
                            stringResource(R.string.set_sim_perm),
                            stringResource(if (Sims.hasPermission(this@SettingsActivity)) R.string.set_sim_perm_on else R.string.set_sim_perm_sub),
                        ) {
                            when {
                                Sims.hasPermission(this@SettingsActivity) -> Ui.toast(this@SettingsActivity, R.string.set_sim_perm_on)
                                Build.VERSION.SDK_INT >= 23 ->
                                    requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQ_PHONE)
                                else -> Ui.toast(this@SettingsActivity, R.string.set_sim_unsupported)
                            }
                        }
                    }

                    SectionLabel(stringResource(R.string.set_sec_notifications))
                    GroupCard {
                        Pref(R.string.set_hide, R.string.set_hide_sub, "hide_notif", false, false)
                        LinkRow(Icons.Rounded.Notifications, stringResource(R.string.set_system_notif), stringResource(R.string.set_system_notif_sub)) { openSystemNotifications() }
                    }

                    SectionLabel(stringResource(R.string.set_sec_privacy))
                    GroupCard {
                        LinkRow(
                            Icons.Rounded.Block,
                            stringResource(R.string.set_blocked),
                            if (tick >= 0) stringResource(R.string.n_blocked, Prefs.blockedKeys(this@SettingsActivity).size) else null,
                        ) { blockedDialog = true }
                        RowDivider()
                        LinkRow(Icons.Rounded.Star, stringResource(R.string.menu_starred), stringResource(R.string.set_starred_sub)) { go(StarredActivity::class.java) }
                        RowDivider()
                        LinkRow(Icons.Rounded.BarChart, stringResource(R.string.menu_insights), stringResource(R.string.set_insights_sub)) { go(StatsActivity::class.java) }
                    }

                    SectionLabel(stringResource(R.string.set_sec_default))
                    GroupCard {
                        LinkRow(
                            Icons.Rounded.ChatBubble,
                            stringResource(R.string.set_default_app),
                            if (tick >= 0) stringResource(if (SmsUtil.isDefault(this@SettingsActivity)) R.string.default_yes else R.string.default_no) else null,
                        ) {
                            startActivity(
                                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                            )
                        }
                    }

                    SectionLabel(stringResource(R.string.set_sec_about))
                    GroupCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.about_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (quickDialog) QuickRepliesDialog { quickDialog = false }
        if (blockedDialog) BlockedDialog { blockedDialog = false; refresh++ }
    }

    @Composable
    private fun QuickRepliesDialog(onDismiss: () -> Unit) {
        var text by remember { mutableStateOf(Prefs.quickReplies(this).joinToString("\n")) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.set_quick)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.quick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.putString(this, "quick_replies", text)
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        Prefs.remove(this, "quick_replies")
                        onDismiss()
                    }) { Text(stringResource(R.string.reset)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    @Composable
    private fun BlockedDialog(onDismiss: () -> Unit) {
        var keys by remember { mutableStateOf(Prefs.blockedKeys(this).toList()) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.set_blocked)) },
            text = {
                if (keys.isEmpty()) {
                    Text(stringResource(R.string.no_blocked))
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        keys.forEach { k ->
                            val info = Contacts.cached(k)
                            val label = if (info != null && info.found) "${info.name}  ($k)" else k
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Prefs.unblockKey(this@SettingsActivity, k)
                                        Ui.toast(this@SettingsActivity, R.string.unblocked)
                                        keys = keys - k
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    }

    companion object {
        private const val REQ_PHONE = 78
    }
}
