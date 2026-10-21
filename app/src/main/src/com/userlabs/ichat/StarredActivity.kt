package com.userlabs.ichat

import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.userlabs.ichat.ui.ComposeBaseActivity
import com.userlabs.ichat.ui.components.ContactAvatar
import com.userlabs.ichat.ui.components.EmptyState
import com.userlabs.ichat.ui.components.IChatTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All starred messages across conversations. */
class StarredActivity : ComposeBaseActivity() {

    private class StarRow(
        val id: Long, val address: String, val name: String?,
        val body: String?, val date: Long, val info: Contacts.Info,
    )

    private var rows by mutableStateOf<List<StarRow>>(emptyList())
    private var loaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent { StarredScreen() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            rows = withContext(Dispatchers.IO) { query() }
            loaded = true
        }
    }

    private fun query(): List<StarRow> {
        val ids = Prefs.starredIds(this)
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<StarRow>()
        try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "date"),
                "_id IN (" + ids.joinToString(",") + ")", null, "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(1) ?: continue
                    val info = Contacts.lookup(this, addr)
                    out.add(StarRow(c.getLong(0), addr, info.name, c.getString(2), c.getLong(3), info))
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return out
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun StarredScreen() {
        var toRemove by remember { mutableStateOf<StarRow?>(null) }
        val context = LocalContext.current
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                IChatTopBar(stringResource(R.string.menu_starred), onBack = { finish() })
                if (loaded && rows.isEmpty()) {
                    EmptyState(stringResource(R.string.no_starred))
                } else {
                    LazyColumn(
                        Modifier.navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    ) {
                        items(rows, key = { it.id }) { r ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            startActivity(
                                                Intent(context, ThreadActivity::class.java)
                                                    .putExtra("address", r.address)
                                                    .putExtra("jump_id", r.id)
                                            )
                                        },
                                        onLongClick = { toRemove = r },
                                    ),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp),
                            ) {
                                Row(Modifier.padding(14.dp)) {
                                    ContactAvatar(r.address, r.info, 44.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                r.name?.takeIf { it.isNotEmpty() } ?: r.address,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                SmsUtil.formatTime(context, r.date),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            r.body.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        toRemove?.let { r ->
            AlertDialog(
                onDismissRequest = { toRemove = null },
                text = { Text(stringResource(R.string.unstar_q)) },
                confirmButton = {
                    TextButton(onClick = {
                        Prefs.setStarred(this, r.id, false)
                        toRemove = null
                        load()
                    }) { Text(stringResource(R.string.unstar)) }
                },
                dismissButton = {
                    TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}
