package com.userlabs.ichat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF171827)
private val Muted = Color(0xFF77798C)
private val Violet = Color(0xFF6750A4)
private val SurfaceSoft = Color(0xFFF4F2FA)

class ModernHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Violet, onPrimary = Color.White,
                    background = Color(0xFFF7F8FC), surface = Color.White,
                    onBackground = Ink, onSurface = Ink
                )
            ) {
                HomeScreen(
                    onMessages = { startActivity(Intent(this, MainActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(onMessages: () -> Unit, onSettings: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Color(0xFFF7F8FC),
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(selected == 0, { selected = 0 }, icon = { Icon(Icons.Default.ChatBubble, null) }, label = { Text("Chats") })
                NavigationBarItem(selected == 1, { selected = 1 }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("AI tools") })
                NavigationBarItem(selected == 2, { selected = 2; onSettings() }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("iChat", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Your messages, made smarter.", color = Muted, fontSize = 14.sp)
                    }
                    Box(Modifier.size(48.dp).background(Color(0xFFE8E1FA), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Violet, modifier = Modifier.size(25.dp))
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Ink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF29263F), Color(0xFF51427D)))).padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(50)) {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFD9CCFF), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("iCHAT AI", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(15.dp))
                        Text("A little help with every message.", color = Color.White, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("Get help writing replies, summarizing texts, and finding the right words.", color = Color(0xFFE2DDF0), fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { selected = 1 }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) {
                            Text("Explore AI tools", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Your space", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Simple by design", color = Muted, fontSize = 12.sp)
                }
            }
            item {
                HomeActionCard(
                    icon = { Icon(Icons.Default.ChatBubble, null, tint = Violet) },
                    title = "Open messages",
                    subtitle = "Read, send, and manage SMS conversations",
                    onClick = onMessages
                )
            }
            item {
                HomeActionCard(
                    icon = { Icon(Icons.Default.Contacts, null, tint = Violet) },
                    title = "Smart replies",
                    subtitle = "Turn a rough idea into a clear reply",
                    onClick = { selected = 1 }
                )
            }
            item {
                HomeActionCard(
                    icon = { Icon(Icons.Default.Settings, null, tint = Violet) },
                    title = "Personalize iChat",
                    subtitle = "Themes, notifications, and preferences",
                    onClick = onSettings
                )
            }
            if (selected == 1) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("AI tools", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Writing help and smart replies are presented here as the next AI workspace. Connect an AI provider to enable live generation.", color = Muted, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Color(0xFFF0ECFA), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Text("›", fontSize = 28.sp, color = Muted)
        }
    }
}
