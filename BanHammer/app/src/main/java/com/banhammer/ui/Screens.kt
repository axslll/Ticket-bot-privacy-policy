package com.banhammer.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banhammer.AppItem
import com.banhammer.BanState
import com.banhammer.Prefs
import kotlinx.coroutines.delay

private fun toast(ctx: android.content.Context, msg: String?) {
    if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

@Composable
fun BanHammerApp(s: BanState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { s.tick(); delay(1000) }
    }

    Scaffold(
        containerColor = Pal.Bg,
        bottomBar = {
            NavigationBar(containerColor = Pal.Card) {
                listOf("🛡️" to "Home", "📱" to "Apps", "🌐" to "Sites").forEachIndexed { i, (emoji, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Pal.Red.copy(alpha = 0.2f),
                            selectedTextColor = Pal.Text,
                            unselectedTextColor = Pal.Dim,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(s)
                1 -> AppsScreen(s)
                else -> SitesScreen(s)
            }
        }
    }
}

// ============================================================ shared bits

@Composable
private fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pal.Text)
        if (sub != null) Text(sub, fontSize = 14.sp, color = Pal.Dim, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, tint: Color = Pal.Card, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tint).padding(18.dp)) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Pal.Dim, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
    )
}

// ============================================================ Home

@Composable
private fun HomeScreen(s: BanState) {
    val ctx = LocalContext.current
    var confirmDays by remember { mutableIntStateOf(0) }

    val (title, sub, color) = when {
        !s.serviceRunning -> Triple("Not running", "Turn BanHammer on in Accessibility settings.", Pal.Amber)
        !s.enabled -> Triple("Paused", "Nothing is being blocked right now.", Pal.Amber)
        else -> Triple("You're protected", "Banned apps and sites send you straight home.", Pal.Green)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Title("BanHammer")

        // Hero status
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Pal.Card)))
                .padding(24.dp)
        ) {
            Column {
                Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Text(if (color == Pal.Green) "✓" else "!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pal.Text)
                Text(sub, fontSize = 14.sp, color = Pal.Dim, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${s.blocked.size} apps")
                    Pill("${s.sites.size} sites")
                    if (s.locked) Pill("🔒 ${Prefs.describeRemaining(ctx)}")
                }
            }
        }

        // Service problem
        if (!s.serviceRunning) {
            Spacer(Modifier.height(12.dp))
            Card(tint = Pal.Amber.copy(alpha = 0.12f)) {
                Column {
                    Text(
                        if (s.serviceOn) "BanHammer is switched on but not running" else "One-time setup",
                        fontWeight = FontWeight.SemiBold, color = Pal.Text, fontSize = 16.sp,
                    )
                    Text(
                        if (s.serviceOn)
                            "This happens after an update. Open Accessibility settings, switch BanHammer off, then on again."
                        else
                            "Accessibility settings → Installed apps → BanHammer → turn it on. If Android says it's restricted: Settings → Apps → BanHammer → ⋮ → Allow restricted settings.",
                        color = Pal.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Amber, contentColor = Color.Black),
                    ) { Text("Open Accessibility settings") }
                }
            }
        }

        // Ban switch
        Spacer(Modifier.height(12.dp))
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ban active", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
                    Text(
                        if (s.locked) "Locked on" else "Turn off to pause everything",
                        fontSize = 13.sp, color = Pal.Dim,
                    )
                }
                if (s.locked) Icon(Icons.Default.Lock, null, tint = Pal.Dim)
                else Switch(
                    checked = s.enabled,
                    onCheckedChange = { toast(ctx, s.setEnabled(it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pal.Red, checkedThumbColor = Color.White),
                )
            }
        }

        // Lock
        Spacer(Modifier.height(12.dp))
        Card {
            Column {
                Text("Lock the ban", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
                Text(
                    if (s.locked) "Locked for ${Prefs.describeRemaining(ctx)}. You can ban more but not unban. Extend it any time."
                    else "While locked you can't pause the ban or remove anything from it. No undo.",
                    fontSize = 13.sp, color = Pal.Dim, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1 day" to 1, "7 days" to 7, "30 days" to 30).forEach { (label, days) ->
                        FilledTonalButton(
                            onClick = { confirmDays = days },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Pal.CardHi, contentColor = Pal.Text),
                        ) { Text(label) }
                    }
                }
            }
        }

        // Diagnostics
        Spacer(Modifier.height(12.dp))
        val seenAgo = if (s.lastSeenAt == 0L) null else ((s.now - s.lastSeenAt) / 1000).coerceAtLeast(0)
        Text(
            buildString {
                append(if (s.serviceRunning) "Blocker: running" else "Blocker: not running")
                append("\n")
                append(
                    if (seenAgo == null) "Last app seen: none yet. Open another app, then come back here."
                    else "Last app seen: ${s.lastSeenPkg} (${seenAgo}s ago)"
                )
                if (s.lastBlockedAt != 0L) append("\nLast blocked: ${s.lastBlockedWhat} (${(s.now - s.lastBlockedAt) / 1000}s ago)")
            },
            fontSize = 12.sp, color = Pal.Dim, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (confirmDays > 0) {
        val days = confirmDays
        AlertDialog(
            onDismissRequest = { confirmDays = 0 },
            containerColor = Pal.Card,
            title = { Text("Lock for $days ${if (days == 1) "day" else "days"}?", color = Pal.Text) },
            text = {
                Text(
                    "You won't be able to pause the ban or remove anything until the time is up. " +
                        "The only way out is switching off the accessibility service or uninstalling the app.",
                    color = Pal.Dim,
                )
            },
            confirmButton = { TextButton(onClick = { s.lock(days); confirmDays = 0 }) { Text("Lock it", color = Pal.Red) } },
            dismissButton = { TextButton(onClick = { confirmDays = 0 }) { Text("Cancel", color = Pal.Dim) } },
        )
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text, fontSize = 13.sp, color = Pal.Text, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ============================================================ Apps

@Composable
private fun AppsScreen(s: BanState) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    // Banned apps that aren't installed right now stay visible: the ban is on the package name.
    val (banned, others) = remember(s.installed, s.blocked, query) {
        val q = query.trim().lowercase()
        fun ok(a: AppItem) = q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.lowercase().contains(q)
        val installedPkgs = s.installed.map { it.pkg }.toSet()
        val missing = (s.blocked - installedPkgs).map { AppItem(it, it, null, false) }
        val ban = (s.installed.filter { it.pkg in s.blocked } + missing).filter(::ok).sortedBy { it.label.lowercase() }
        val rest = s.installed.filter { it.pkg !in s.blocked }.filter(::ok)
        ban to rest
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            Title("Apps", "Tick the apps you want banned.")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", color = Pal.Dim) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Pal.Dim) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear", tint = Pal.Dim) }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { showAdd = true }) { Text("+ Ban an app by package name", color = Pal.Red) }
        }

        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.Red) }
        } else LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (banned.isNotEmpty()) {
                item(key = "h-banned") { SectionLabel("BANNED · ${banned.size}") }
                items(banned, key = { it.pkg }) { app ->
                    AppRow(app, banned = true, locked = s.locked, modifier = Modifier.animateItem()) {
                        toast(ctx, s.setApp(app.pkg, false))
                    }
                }
            }
            item(key = "h-all") { SectionLabel("ALL APPS · ${others.size}") }
            items(others, key = { it.pkg }) { app ->
                AppRow(app, banned = false, locked = s.locked, modifier = Modifier.animateItem()) {
                    toast(ctx, s.setApp(app.pkg, true))
                }
            }
            item(key = "end") { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showAdd) AddPackageDialog(
        onDismiss = { showAdd = false },
        onAdd = { pkg ->
            val err = s.addPackage(pkg)
            toast(ctx, err ?: "Banned $pkg")
            if (err == null) showAdd = false
        },
    )
}

@Composable
private fun AppRow(app: AppItem, banned: Boolean, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (banned) Pal.Red.copy(alpha = 0.10f) else Pal.Card)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(app.icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Pal.CardHi), contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), color = Pal.Dim, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(app.label, color = Pal.Text, fontSize = 16.sp, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(
                if (app.installed) app.pkg else "Not installed · still banned",
                color = if (app.installed) Pal.Dim else Pal.Red.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1,
            )
        }
        if (banned && locked) Icon(Icons.Default.Lock, "Locked", tint = Pal.Dim)
        else Switch(
            checked = banned,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Pal.Red, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun AddPackageDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text("Ban by package name", color = Pal.Text) },
        text = {
            Column {
                Text(
                    "For apps that aren't in the list, or aren't installed yet. You can find an app's package name in its Play Store link after id=.",
                    color = Pal.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("com.reddit.frontpage", color = Pal.Dim) },
                    colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }) { Text("Ban it", color = Pal.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.Dim) } },
    )
}

// ============================================================ Sites

@Composable
private fun SitesScreen(s: BanState) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    fun add() {
        val err = s.addSite(text)
        if (err != null) toast(ctx, err) else text = ""
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Title("Websites", "Opening these in a supported browser sends you home.") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("example.com", color = Pal.Dim) },
                    colors = fieldColors(), shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { add() },
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp),
                ) { Text("Ban") }
            }
        }
        item { SectionLabel("BANNED SITES · ${s.sites.size}") }
        items(s.sites.sorted(), key = { it }) { site ->
            Row(
                Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(18.dp)).background(Pal.Card).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🌐", fontSize = 18.sp)
                Text(site, color = Pal.Text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                if (s.locked) Icon(Icons.Default.Lock, "Locked", tint = Pal.Dim, modifier = Modifier.padding(12.dp))
                else IconButton(onClick = { toast(ctx, s.removeSite(site)) }) { Icon(Icons.Default.Delete, "Remove", tint = Pal.Dim) }
            }
        }
        item {
            Text(
                "Works in Chrome, Brave, Edge, Firefox, Samsung Internet, Opera and DuckDuckGo. " +
                    "Subdomains are covered too (banning x.com also bans mobile.x.com).",
                color = Pal.Dim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Pal.Card, unfocusedContainerColor = Pal.Card,
    focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text,
    focusedIndicatorColor = Pal.Red, unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Pal.Red,
)
