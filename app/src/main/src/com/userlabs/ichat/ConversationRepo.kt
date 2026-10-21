package com.userlabs.ichat

import android.content.Context
import android.provider.Telephony
import java.util.Locale

/** One row of the conversation list. [kind]: 0 conversation, 1 section header, 2 message hit. */
data class Conv(
    val kind: Int = 0,
    val threadId: Long = 0,
    val address: String = "",
    val name: String? = null,
    val snippet: String? = null,
    val date: Long = 0,
    val unread: Int = 0,
    val lastType: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val blocked: Boolean = false,
    val draft: String? = null,
    val msgId: Long = 0,
    val headerText: String? = null,
    val info: Contacts.Info? = null,
    val subId: Int = -1,
) {
    val label: String get() = name?.takeIf { it.isNotEmpty() } ?: address
    val key: String get() = Prefs.key(address)
}

data class ConvResult(val list: List<Conv>, val anyBlocked: Boolean)

object ConversationRepo {

    const val F_ALL = 0
    const val F_UNREAD = 1
    const val F_PINNED = 2
    const val F_BLOCKED = 3

    /** Blocking: call from a background thread. */
    fun load(c: Context, q: String, filter: Int): ConvResult {
        val map = LinkedHashMap<Long, MutableConv>()
        try {
            SmsUtil.querySms(
                c,
                arrayOf("thread_id", "address", "body", "date", "read", "type"),
                null, null, "date DESC",
            )?.use { cur ->
                val hasSub = cur.columnCount > 6
                while (cur.moveToNext()) {
                    val tid = cur.getLong(0)
                    var cv = map[tid]
                    if (cv == null) {
                        val addr = cur.getString(1) ?: continue
                        cv = MutableConv(tid, addr, cur.getString(2), cur.getLong(3), cur.getInt(5))
                        if (hasSub && !cur.isNull(6)) cv.subId = cur.getInt(6)
                        map[tid] = cv
                    }
                    if (cur.getInt(4) == 0 && cur.getInt(5) == 1) cv.unread++
                }
            }
        } catch (e: Exception) {
            // permission missing or provider error: show an empty list
        }

        val ql = q.lowercase(Locale.getDefault())
        val pinned = ArrayList<Conv>()
        val rest = ArrayList<Conv>()
        var anyBlocked = false
        for (m in map.values) {
            val info = Contacts.lookup(c, m.address)
            val isPinned = Prefs.isPinned(c, m.address)
            val isBlocked = Prefs.isBlocked(c, m.address)
            var unread = m.unread
            if (isBlocked) anyBlocked = true
            if (Prefs.isMarkedUnread(c, m.address) && unread == 0) unread = 1
            if (filter == F_BLOCKED) {
                if (!isBlocked) continue
            } else if (isBlocked) {
                continue
            }
            if (filter == F_UNREAD && unread == 0) continue
            if (filter == F_PINNED && !isPinned) continue
            if (ql.isNotEmpty()) {
                val hay = "${info.name ?: ""} ${m.address} ${m.snippet ?: ""}".lowercase(Locale.getDefault())
                if (!hay.contains(ql)) continue
            }
            val cv = Conv(
                threadId = m.threadId, address = m.address, name = info.name,
                snippet = m.snippet, date = m.date, unread = unread, lastType = m.lastType,
                pinned = isPinned, muted = Prefs.isMuted(c, m.address), blocked = isBlocked,
                draft = Prefs.draft(c, m.address), info = info, subId = m.subId,
            )
            if (isPinned && ql.isEmpty()) pinned.add(cv) else rest.add(cv)
        }

        val out = ArrayList<Conv>()
        if (ql.isNotEmpty() && pinned.size + rest.size > 0) {
            out.add(Conv(kind = 1, headerText = c.getString(R.string.section_conversations)))
        }
        out.addAll(pinned)
        out.addAll(rest)

        if (q.length >= 2) {
            val hits = ArrayList<Conv>()
            try {
                val esc = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                c.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf("_id", "address", "body", "date", "type"),
                    "body LIKE ? ESCAPE '\\'", arrayOf("%$esc%"), "date DESC LIMIT 40",
                )?.use { cur ->
                    while (cur.moveToNext()) {
                        val addr = cur.getString(1) ?: continue
                        val info = Contacts.lookup(c, addr)
                        hits.add(
                            Conv(
                                kind = 2, msgId = cur.getLong(0), address = addr,
                                snippet = cur.getString(2), date = cur.getLong(3),
                                lastType = cur.getInt(4), info = info, name = info.name,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            if (hits.isNotEmpty()) {
                out.add(Conv(kind = 1, headerText = c.getString(R.string.section_messages)))
                out.addAll(hits)
            }
        }
        return ConvResult(out, anyBlocked)
    }

    private class MutableConv(
        val threadId: Long, val address: String, val snippet: String?,
        val date: Long, val lastType: Int,
    ) {
        var unread = 0
        var subId = -1
    }
}
