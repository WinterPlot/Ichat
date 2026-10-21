package com.userlabs.ichat

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.userlabs.ichat.ui.ComposeBaseActivity
import com.userlabs.ichat.ui.components.ContactAvatar
import com.userlabs.ichat.ui.components.EmptyState
import com.userlabs.ichat.ui.theme.IChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Conversation list: pinned chats, filters, search (names and message text), multi-select. */
class MainActivity : ComposeBaseActivity() {

    private var convs by mutableStateOf<List<Conv>>(emptyList())
    private var anyBlocked by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var filter by mutableStateOf(ConversationRepo.F_ALL)
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var isDefault by mutableStateOf(true)
    private var permsOk by mutableStateOf(true)

    private var loading = false
    private var pending = false
    private var observing = false
    private var askedPerms = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent { ConversationsScreen() }
    }

    override fun onResume() {
        super.onResume()
        isDefault = SmsUtil.isDefault(this)
        permsOk = hasPermissions()
        if (permsOk) {
            if (!observing) {
                contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                observing = true
            }
            load()
        } else if (Build.VERSION.SDK_INT >= 23 && !askedPerms) {
            askedPerms = true
            requestPermissions(PERMS, REQ_PERMS)
        }
    }

    override fun onPause() {
        super.onPause()
        if (observing) {
            contentResolver.unregisterContentObserver(observer)
            observing = false
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        permsOk = hasPermissions()
        if (permsOk) load()
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return PERMS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestDefault() {
        startActivity(
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        )
    }

    /** Loads off the UI thread; coalesces rapid change events. */
    private fun load() {
        if (loading) {
            pending = true
            return
        }
        loading = true
        val q = query
        val f = filter
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { ConversationRepo.load(this@MainActivity, q, f) }
            convs = r.list
            anyBlocked = r.anyBlocked
            loaded = true
            loading = false
            if (pending) {
                pending = false
                load()
            }
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val v = ContentValues().apply { put("read", 1); put("seen", 1) }
                    contentResolver.update(Telephony.Sms.CONTENT_URI, v, "read = 0", null)
                } catch (e: Exception) {
                    // ignore
                }
            }
            Prefs.putString(this@MainActivity, "manual_unread", "")
            load()
        }
    }

    private fun open(cv: Conv) {
        val i = Intent(this, ThreadActivity::class.java).putExtra("address", cv.address)
        if (cv.kind == 2) i.putExtra("jump_id", cv.msgId)
        startActivity(i)
    }

    private fun selectedConvs(): List<Conv> =
        convs.filter { it.kind == 0 && it.key in selected }

    private fun toggleSelect(cv: Conv) {
        selected = if (cv.key in selected) selected - cv.key else selected + cv.key
    }

    private fun clearSelection() {
        selected = emptySet()
    }

    /** Pin, read/unread, mute and block share one "apply to all, or undo if all already set" rule. */
    private fun runSelectionAction(action: Int) {
        val sel = selectedConvs()
        if (sel.isEmpty()) return
        when (action) {
            ACT_PIN -> {
                val all = sel.all { it.pinned }
                sel.forEach { Prefs.setPinned(this, it.address, !all) }
            }
            ACT_READ -> {
                if (sel.any { it.unread > 0 }) {
                    sel.forEach {
                        Prefs.setMarkedUnread(this, it.address, false)
                        SmsUtil.markThreadRead(this, it.threadId)
                        Notifier.cancel(this, it.address)
                    }
                } else {
                    sel.forEach { Prefs.setMarkedUnread(this, it.address, true) }
                }
            }
            ACT_MUTE -> {
                val all = sel.all { it.muted }
                sel.forEach { Prefs.setMuted(this, it.address, !all) }
            }
            ACT_BLOCK -> {
                val all = sel.all { it.blocked }
                sel.forEach { Prefs.setBlocked(this, it.address, !all) }
                Ui.toast(this, if (all) R.string.unblocked else R.string.blocked_toast)
            }
        }
        clearSelection()
        load()
    }

    private fun deleteSelected() {
        val sel = selectedConvs()
        sel.forEach { SmsUtil.deleteThread(this, it.address) }
        clearSelection()
        load()
    }

    // ---------------------------------------------------------------- UI

    @Composable
    private fun ConversationsScreen() {
        var searching by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        val selecting = selected.isNotEmpty()

        LaunchedEffect(query, filter) { if (loaded || permsOk) load() }

        BackHandler(enabled = selecting || searching) {
            if (selecting) {
                clearSelection()
            } else {
                searching = false
                query = ""
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (!selecting) {
                    FloatingActionButton(
                        onClick = { startActivity(Intent(this@MainActivity, NewMessageActivity::class.java)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp),
                    ) { Icon(Icons.Rounded.Edit, contentDescription = null) }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when {
                    selecting -> SelectionBar(onDelete = { confirmDelete = true })
                    searching -> SearchBar(onClose = { searching = false; query = "" })
                    else -> TitleBar(
                        onSearch = { searching = true },
                        menuOpen = menuOpen,
                        onMenu = { menuOpen = it },
                    )
                }
                if (!isDefault) DefaultBanner()
                FilterRow()
                Box(Modifier.weight(1f)) {
                    if (convs.isEmpty()) {
                        EmptyState(
                            text = emptyText(),
                            onClick = if (!permsOk && Build.VERSION.SDK_INT >= 23) {
                                { requestPermissions(PERMS, REQ_PERMS) }
                            } else null,
                        )
                    } else {
                        ConversationList()
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                text = { Text(stringResource(R.string.delete_convs_confirm, selected.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        if (SmsUtil.isDefault(this)) deleteSelected() else Ui.toast(this, R.string.need_default)
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    @Composable
    private fun emptyText(): String = when {
        !permsOk -> stringResource(R.string.perm_needed)
        !loaded -> ""
        query.isNotEmpty() -> stringResource(R.string.no_results)
        filter == ConversationRepo.F_UNREAD -> stringResource(R.string.no_unread)
        filter == ConversationRepo.F_PINNED -> stringResource(R.string.no_pinned)
        filter == ConversationRepo.F_BLOCKED -> stringResource(R.string.no_blocked)
        else -> stringResource(R.string.no_conversations)
    }

    @Composable
    private fun TitleBar(onSearch: () -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
            )
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, null) }
            Box {
                IconButton(onClick = { onMenu(true) }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                    fun go(cls: Class<*>) { onMenu(false); startActivity(Intent(this@MainActivity, cls)) }
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_chat_style)) }, onClick = { go(StyleActivity::class.java) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_starred)) }, onClick = { go(StarredActivity::class.java) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_insights)) }, onClick = { go(StatsActivity::class.java) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_settings)) }, onClick = { go(SettingsActivity::class.java) })
                    if (isDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_mark_all_read)) },
                            onClick = { onMenu(false); markAllRead() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.set_default)) },
                            onClick = { onMenu(false); requestDefault() },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchBar(onClose: () -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.trim() },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).clickable { query = "" },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SelectionBar(onDelete: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { clearSelection() }) { Icon(Icons.Rounded.Close, null) }
            Text(
                selected.size.toString(),
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { runSelectionAction(ACT_PIN) }) { Icon(Icons.Rounded.PushPin, null) }
            IconButton(onClick = { runSelectionAction(ACT_READ) }) { Icon(Icons.Rounded.MarkChatUnread, null) }
            IconButton(onClick = { runSelectionAction(ACT_MUTE) }) { Icon(Icons.Rounded.NotificationsOff, null) }
            IconButton(onClick = { runSelectionAction(ACT_BLOCK) }) { Icon(Icons.Rounded.Block, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null) }
        }
    }

    @Composable
    private fun DefaultBanner() {
        Surface(
            onClick = { requestDefault() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = IChat.colors.accentSoft,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.banner_default),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.set_default),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    @Composable
    private fun FilterRow() {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(R.string.chip_all, ConversationRepo.F_ALL)
            Chip(R.string.chip_unread, ConversationRepo.F_UNREAD)
            Chip(R.string.chip_pinned, ConversationRepo.F_PINNED)
            if (anyBlocked || filter == ConversationRepo.F_BLOCKED) Chip(R.string.chip_blocked, ConversationRepo.F_BLOCKED)
        }
    }

    @Composable
    private fun Chip(label: Int, f: Int) {
        FilterChip(
            selected = filter == f,
            onClick = { filter = f },
            label = { Text(stringResource(label)) },
            shape = RoundedCornerShape(50),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = null,
        )
    }

    @Composable
    private fun ConversationList() {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(convs, key = { c -> if (c.kind == 0) "t${c.threadId}" else "k${c.kind}-${c.msgId}-${c.headerText}" }) { cv ->
                if (cv.kind == 1) {
                    Text(
                        cv.headerText.orEmpty().uppercase(),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    ConversationRow(cv)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ConversationRow(cv: Conv) {
        val view = LocalView.current
        val isSel = cv.kind == 0 && cv.key in selected
        val unread = cv.unread > 0
        val context = LocalContext.current
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .combinedClickable(
                    onClick = {
                        if (selected.isNotEmpty() && cv.kind == 0) toggleSelect(cv) else open(cv)
                    },
                    onLongClick = {
                        if (cv.kind == 0) {
                            Ui.tick(view)
                            toggleSelect(cv)
                        }
                    },
                ),
            shape = MaterialTheme.shapes.large,
            color = if (isSel) IChat.colors.accentSoft else Color.Transparent,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(cv.address, cv.info, 52.dp, selected = isSel)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cv.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            SmsUtil.formatTime(context, cv.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            snippetText(cv),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (cv.kind == 0) {
                            if (cv.subId >= 0 && Sims.isDual(context)) {
                                val slot = Sims.slotNumber(context, cv.subId)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.sim_n, slot),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color(Sims.colorOf(context, cv.subId)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            if (cv.muted) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (cv.pinned) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (unread) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (cv.unread > 99) "99+" else cv.unread.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun snippetText(cv: Conv): AnnotatedString {
        val accent = MaterialTheme.colorScheme.primary
        val danger = MaterialTheme.colorScheme.error
        val draftPrefix = stringResource(R.string.draft_prefix)
        val youPrefix = stringResource(R.string.you_prefix)
        val draft = cv.draft
        if (cv.kind == 0 && !draft.isNullOrEmpty()) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = danger, fontWeight = FontWeight.SemiBold)) {
                    append(draftPrefix)
                }
                append(" ")
                append(draft.replace('\n', ' '))
            }
        }
        val sent = cv.lastType != SmsUtil.TYPE_INBOX
        val body = (cv.snippet ?: "").replace('\n', ' ')
        val q = query
        return buildAnnotatedString {
            if (cv.kind == 0 && sent) append("$youPrefix ")
            val idx = if (cv.kind == 2 && q.isNotEmpty()) {
                body.lowercase(Locale.getDefault()).indexOf(q.lowercase(Locale.getDefault()))
            } else -1
            if (idx >= 0) {
                val end = minOf(body.length, idx + q.length)
                val start = if (idx > 18) idx - 14 else 0
                if (start > 0) append("\u2026")
                append(body.substring(start, idx))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(body.substring(idx, end))
                }
                append(body.substring(end))
            } else {
                append(body)
            }
        }
    }

    companion object {
        private const val REQ_PERMS = 1
        private const val ACT_PIN = 1
        private const val ACT_READ = 2
        private const val ACT_MUTE = 3
        private const val ACT_BLOCK = 4
        private val PERMS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
        )
    }
}
