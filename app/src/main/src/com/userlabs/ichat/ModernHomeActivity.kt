package com.userlabs.ichat

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.userlabs.ichat.ui.ComposeBaseActivity
import com.userlabs.ichat.ui.components.IconTile
import com.userlabs.ichat.ui.theme.IChat
import androidx.compose.material.icons.rounded.ChevronRight

/** Launcher screen. Every colour comes from the shared IChatTheme, so it follows light/dark/AMOLED and the accent. */
class ModernHomeActivity : ComposeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent {
            HomeScreen(
                onMessages = { startActivity(Intent(this, MainActivity::class.java)) },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onStyle = { startActivity(Intent(this, StyleActivity::class.java)) },
                onStarred = { startActivity(Intent(this, StarredActivity::class.java)) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onMessages: () -> Unit,
    onSettings: () -> Unit,
    onStyle: () -> Unit,
    onStarred: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        bottomBar = {
            NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(indicatorColor = IChat.colors.accentSoft)
                NavigationBarItem(selected == 0, { selected = 0 }, icon = { Icon(Icons.Rounded.ChatBubble, null) }, label = { Text("Chats") }, colors = colors)
                NavigationBarItem(selected == 1, { selected = 1 }, icon = { Icon(Icons.Rounded.AutoAwesome, null) }, label = { Text("AI tools") }, colors = colors)
                NavigationBarItem(false, onSettings, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("Settings") }, colors = colors)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("iChat", style = MaterialTheme.typography.headlineMedium)
                        Text("Your messages, made smarter.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(48.dp).background(IChat.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = scheme.primary, modifier = Modifier.size(25.dp))
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .background(Brush.linearGradient(listOf(IChat.colors.accent, IChat.colors.accent2)))
                            .padding(22.dp),
                    ) {
                        Surface(color = Color.White.copy(alpha = .18f), shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("iCHAT AI", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(15.dp))
                        Text("A little help with every message.", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Get help writing replies, summarizing texts, and finding the right words.",
                            color = Color.White.copy(alpha = .88f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { selected = 1 },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF171827)),
                        ) {
                            Text("Explore AI tools", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your space", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("Simple by design", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            item { HomeActionCard(Icons.Rounded.ChatBubble, "Open messages", "Read, send, and manage SMS conversations", onMessages) }
            item { HomeActionCard(Icons.Rounded.Star, "Starred messages", "Everything you saved, in one place", onStarred) }
            item { HomeActionCard(Icons.Rounded.Palette, "Chat style", "Accent, bubbles and wallpapers", onStyle) }
            item { HomeActionCard(Icons.Rounded.Settings, "Personalize iChat", "Themes, notifications, and preferences", onSettings) }
            if (selected == 1) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = IChat.colors.accentSoft), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(18.dp)) {
                            Text("AI tools", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Writing help and smart replies are presented here as the next AI workspace. Connect an AI provider to enable live generation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, Modifier.size(46.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
