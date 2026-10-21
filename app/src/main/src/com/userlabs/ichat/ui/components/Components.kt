package com.userlabs.ichat.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.userlabs.ichat.Contacts
import com.userlabs.ichat.ui.theme.IChat

/** Large, calm top bar shared by all screens. Pass [onBack] = null on a root screen. */
@Composable
fun IChatTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Round contact avatar: photo when there is one, otherwise a coloured initial. */
@Composable
fun ContactAvatar(
    address: String,
    info: Contacts.Info?,
    size: Dp = 52.dp,
    selected: Boolean = false,
) {
    val label = info?.name?.takeIf { it.isNotEmpty() } ?: address
    val photo = info?.photo
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) IChat.colors.accent else Color(Contacts.colorFor(address))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(
                Icons.Rounded.Check, null,
                tint = IChat.colors.onAccent, modifier = Modifier.size(size * 0.5f),
            )
            photo != null -> Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                Contacts.initial(label),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Rounded white card that holds a group of rows (settings, stats...). */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = IChat.colors.divider,
    )
}

/** Tinted rounded square that holds a row icon. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(IChat.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Simple bar chart. Pass [b] for grouped bars; [highlight] dims every bar except that index. */
@Composable
fun BarChart(
    a: List<Float>,
    labels: List<String>,
    colorA: Color,
    modifier: Modifier = Modifier,
    b: List<Float>? = null,
    colorB: Color = colorA,
    highlight: Int = -1,
) {
    val n = a.size
    val max = maxOf(1f, a.maxOrNull() ?: 0f, b?.maxOrNull() ?: 0f)
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 8.dp),
        ) {
            val slot = size.width / n
            val groups = if (b == null) 1 else 2
            val barW = slot * (if (b == null) 0.62f else 0.4f)
            val radius = androidx.compose.ui.geometry.CornerRadius(barW / 3f, barW / 3f)
            for (i in 0 until n) {
                val dim = if (highlight >= 0 && i != highlight) 0.4f else 1f
                val start = i * slot + (slot - barW * groups) / 2f
                val ha = size.height * (a[i] / max)
                drawRoundRect(
                    color = colorA.copy(alpha = dim),
                    topLeft = androidx.compose.ui.geometry.Offset(start, size.height - ha),
                    size = androidx.compose.ui.geometry.Size(barW, maxOf(ha, 2f)),
                    cornerRadius = radius,
                )
                if (b != null) {
                    val hb = size.height * (b[i] / max)
                    drawRoundRect(
                        color = colorB.copy(alpha = dim),
                        topLeft = androidx.compose.ui.geometry.Offset(start + barW, size.height - hb),
                        size = androidx.compose.ui.geometry.Size(barW, maxOf(hb, 2f)),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
