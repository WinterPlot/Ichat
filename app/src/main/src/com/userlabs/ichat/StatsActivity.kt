package com.userlabs.ichat

import android.os.Bundle
import android.provider.Telephony
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.userlabs.ichat.ui.ComposeBaseActivity
import com.userlabs.ichat.ui.components.BarChart
import com.userlabs.ichat.ui.components.ContactAvatar
import com.userlabs.ichat.ui.components.EmptyState
import com.userlabs.ichat.ui.components.IChatTopBar
import com.userlabs.ichat.ui.theme.IChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Messaging insights computed on-device: totals, activity charts and top contacts. */
class StatsActivity : ComposeBaseActivity() {

    private class Top(val address: String, var count: Int) {
        var info: Contacts.Info? = null
        var name: String? = null
    }

    private class Stats {
        var total = 0
        var sent = 0
        var received = 0
        var first = Long.MAX_VALUE
        val hours = IntArray(24)
        val weekday = IntArray(7)
        val daySent = IntArray(14)
        val dayRecv = IntArray(14)
        var top: List<Top> = emptyList()
    }

    private var stats by mutableStateOf<Stats?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent { StatsScreen() }
        lifecycleScope.launch { stats = withContext(Dispatchers.IO) { compute() } }
    }

    private fun compute(): Stats {
        val s = Stats()
        val map = HashMap<String, Top>()
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val today = (now + tz.getOffset(now)) / 86400000L
        try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf("type", "date", "address"), null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(0)
                    if (type == 3) continue // drafts
                    val date = c.getLong(1)
                    val recv = type == 1
                    s.total++
                    if (recv) s.received++ else s.sent++
                    if (date < s.first) s.first = date
                    val local = date + tz.getOffset(date)
                    var hour = ((local / 3600000L) % 24L).toInt()
                    if (hour < 0) hour += 24
                    s.hours[hour]++
                    val day = local / 86400000L
                    var wd = ((day + 3L) % 7L).toInt()
                    if (wd < 0) wd += 7
                    s.weekday[wd]++
                    val ago = today - day
                    if (ago in 0..13) {
                        val idx = 13 - ago.toInt()
                        if (recv) s.dayRecv[idx]++ else s.daySent[idx]++
                    }
                    c.getString(2)?.let { addr ->
                        map.getOrPut(Prefs.key(addr)) { Top(addr, 0) }.count++
                    }
                }
            }
        } catch (e: Exception) {
            // no permission: empty stats
        }
        s.top = map.values.sortedByDescending { it.count }.take(6).onEach {
            val info = Contacts.lookup(this@StatsActivity, it.address)
            it.info = info
            it.name = info.name
        }
        return s
    }

    @Composable
    private fun StatsScreen() {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                IChatTopBar(stringResource(R.string.menu_insights), onBack = { finish() })
                val s = stats
                when {
                    s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    s.total == 0 -> EmptyState(stringResource(R.string.stats_empty))
                    else -> Content(s)
                }
            }
        }
    }

    @Composable
    private fun Content(s: Stats) {
        val accent = MaterialTheme.colorScheme.primary
        val accent2 = IChat.colors.accent2
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(s.total.toString(), stringResource(R.string.stat_total), accent, Modifier.weight(1f))
                Tile(s.received.toString(), stringResource(R.string.stat_received), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                Tile(s.sent.toString(), stringResource(R.string.stat_sent), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            val days = maxOf(1L, (System.currentTimeMillis() - s.first) / 86400000L)
            val since = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(s.first))
            val avg = Math.round(s.total * 10.0 / days) / 10.0
            Text(
                stringResource(R.string.stats_since, since, avg.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // last 14 days
            val cal = Calendar.getInstance()
            val dayLabels = List(14) { i ->
                if (i % 2 == 1) {
                    (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i - 13) }
                        .get(Calendar.DAY_OF_MONTH).toString()
                } else ""
            }
            ChartCard(stringResource(R.string.chart_14)) {
                BarChart(
                    a = s.dayRecv.map { it.toFloat() }, b = s.daySent.map { it.toFloat() },
                    labels = dayLabels, colorA = accent, colorB = accent2,
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(accent)
                    Text(stringResource(R.string.stat_received), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Dot(accent2)
                    Text(stringResource(R.string.stat_sent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // hours
            val peak = s.hours.indices.maxByOrNull { s.hours[it] } ?: 0
            ChartCard(stringResource(R.string.chart_hours)) {
                BarChart(
                    a = s.hours.map { it.toFloat() },
                    labels = List(24) { if (it % 6 == 0) it.toString() else "" },
                    colorA = accent, highlight = peak,
                )
                Text(
                    stringResource(R.string.peak_hour, peak, (peak + 1) % 24),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // weekday (Monday first)
            val sym = DateFormatSymbols(Locale.getDefault()).shortWeekdays
            val order = intArrayOf(2, 3, 4, 5, 6, 7, 1)
            val wpeak = s.weekday.indices.maxByOrNull { s.weekday[it] } ?: 0
            ChartCard(stringResource(R.string.chart_weekday)) {
                BarChart(
                    a = s.weekday.map { it.toFloat() },
                    labels = order.map { sym[it].let { n -> if (n.length > 3) n.substring(0, 3) else n } },
                    colorA = accent, highlight = wpeak,
                )
            }

            // top contacts
            ChartCard(stringResource(R.string.chart_top)) {
                val max = s.top.firstOrNull()?.count ?: 1
                s.top.forEach { t ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(t.address, t.info, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    t.name?.takeIf { it.isNotEmpty() } ?: t.address,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    t.count.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(t.count / max.toFloat())
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(accent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(value: String, label: String, color: Color, modifier: Modifier) {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = color, textAlign = TextAlign.Center)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun ChartCard(title: String, content: @Composable () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                content()
            }
        }
    }

    @Composable
    private fun Dot(color: Color) {
        Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(color))
    }
}
