package com.banhammer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.banhammer.AppItem
import com.banhammer.BanState
import com.banhammer.Check
import com.banhammer.Prefs
import com.banhammer.SettingsLinks
import kotlinx.coroutines.delay

private fun toast(ctx: Context, msg: String?) {
    if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private val NoInsets = WindowInsets(0, 0, 0, 0)

// ================================================================ shell

@Composable
fun BanHammerApp(s: BanState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { s.tick(); delay(1000) }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("Home", Icons.Rounded.Shield, 0),
                    Triple("Apps", Icons.Rounded.Apps, 1),
                    Triple("Sites", Icons.Rounded.Language, 2),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
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

// ================================================================ shared pieces

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    title: String,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = NoInsets,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** An icon in a tinted circle, used as the leading element of rows. */
@Composable
private fun IconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Int = 40,
) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun transparentListColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

// ================================================================ Home

private class Step(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val check: Check,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
private fun HomeScreen(s: BanState) {
    val ctx = LocalContext.current
    var confirmDays by remember { mutableIntStateOf(0) }

    val steps = buildList {
        add(
            Step(
                Icons.Rounded.Accessibility,
                "Turn on the blocker",
                when {
                    s.serviceRunning -> "BanHammer is running."
                    s.serviceOn -> "It's switched on but not running (this happens after an update). Switch it off and on again."
                    else -> "Accessibility → BanHammer → On. If it's greyed out, do the restricted-settings step, then come back."
                },
                if (s.serviceRunning) Check.Done else Check.Todo,
                if (s.serviceOn) "Open page" else "Turn on",
            ) { if (!SettingsLinks.openAccessibility(ctx)) toast(ctx, "Couldn't open settings") },
        )
        if (s.restrictedApplies) add(
            Step(
                Icons.Rounded.Security,
                "Allow restricted settings",
                when (s.restricted) {
                    Check.Done -> "Android isn't blocking BanHammer."
                    Check.Todo -> "Android is blocking the setting. On the page: ⋮ (top right) → Allow restricted settings."
                    Check.Unknown -> "Only needed if the step above is greyed out. On the page: ⋮ (top right) → Allow restricted settings."
                },
                s.restricted,
                "Open app info",
            ) { if (!SettingsLinks.openAppInfo(ctx)) toast(ctx, "Couldn't open settings") },
        )
        add(
            Step(
                Icons.Rounded.BatterySaver,
                "Run in the background",
                if (s.batteryOk) "Battery restrictions are off for BanHammer." else "Stops your phone killing the blocker to save battery.",
                if (s.batteryOk) Check.Done else Check.Todo,
                "Allow",
            ) { if (!SettingsLinks.openBattery(ctx)) toast(ctx, "Couldn't open settings") },
        )
    }
    // "Unknown" never counts as a problem; it just isn't a confirmed tick.
    val needed = steps.filter { it.check != Check.Unknown }
    val doneCount = needed.count { it.check == Check.Done }
    val setupDone = s.setupDone
    var showSteps by remember(setupDone) { mutableStateOf(!setupDone) }

    Screen("BanHammer") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // ---- status
            val (icon, title, sub, container, onContainer) = when {
                !setupDone -> StatusUi(
                    Icons.Rounded.Warning, "Finish setup",
                    "$doneCount of ${needed.size} steps done. Nothing is blocked until the blocker is running.",
                    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
                )
                !s.enabled -> StatusUi(
                    Icons.Rounded.PauseCircle, "Paused",
                    "Nothing is being blocked right now.",
                    MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface,
                )
                else -> StatusUi(
                    Icons.Rounded.Shield, "Protection is on",
                    "${s.blocked.size} apps and ${s.sites.size} websites are banned." +
                        if (s.locked) " Locked for ${Prefs.describeRemaining(ctx)}." else "",
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(40.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            // ---- setup
            SectionTitle("Setup")
            GroupCard {
                Column {
                    if (setupDone) {
                        ListItem(
                            modifier = Modifier.clickable { showSteps = !showSteps },
                            colors = transparentListColors(),
                            leadingContent = { IconBadge(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                            headlineContent = { Text("All set") },
                            supportingContent = { Text("Every permission BanHammer needs is on.") },
                            trailingContent = { Icon(if (showSteps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                        )
                    }
                    AnimatedVisibility(showSteps) {
                        Column { steps.forEach { StepRow(it) } }
                    }
                }
            }

            // ---- controls
            SectionTitle("Ban")
            GroupCard {
                ListItem(
                    colors = transparentListColors(),
                    headlineContent = { Text("Ban active") },
                    supportingContent = { Text(if (s.locked) "Locked on" else "Turn off to pause everything") },
                    trailingContent = {
                        if (s.locked) Icon(Icons.Rounded.Lock, "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Switch(checked = s.enabled, onCheckedChange = { toast(ctx, s.setEnabled(it)) })
                    },
                )
            }

            SectionTitle("Lock")
            GroupCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (s.locked) "Locked for ${Prefs.describeRemaining(ctx)}. You can ban more, but not unban. Extend any time."
                        else "While locked you can't pause the ban or remove anything from it. There is no undo.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1 day" to 1, "7 days" to 7, "30 days" to 30).forEach { (label, days) ->
                            OutlinedButton(onClick = { confirmDays = days }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
            }

            // ---- diagnostics
            Text(
                buildString {
                    append(if (s.serviceRunning) "Blocker running" else "Blocker not running")
                    if (s.serviceRunning && s.enabled) append(" · watching ${s.blocked.size} apps")
                    if (s.lastBlockedAt != 0L) append("\nLast blocked: ${s.lastBlockedWhat} (${(s.now - s.lastBlockedAt) / 1000}s ago)")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
    }

    if (confirmDays > 0) {
        val days = confirmDays
        AlertDialog(
            onDismissRequest = { confirmDays = 0 },
            icon = { Icon(Icons.Rounded.Lock, null) },
            title = { Text("Lock for $days ${if (days == 1) "day" else "days"}?") },
            text = {
                Text(
                    "You won't be able to pause the ban or remove anything until the time is up. " +
                        "The only way out is switching off the accessibility service or uninstalling the app."
                )
            },
            confirmButton = { TextButton(onClick = { s.lock(days); confirmDays = 0 }) { Text("Lock it") } },
            dismissButton = { TextButton(onClick = { confirmDays = 0 }) { Text("Cancel") } },
        )
    }
}

private data class StatusUi(val icon: ImageVector, val title: String, val sub: String, val container: Color, val onContainer: Color)

@Composable
private fun StepRow(step: Step) {
    val done = step.check == Check.Done
    ListItem(
        colors = transparentListColors(),
        leadingContent = {
            if (done) IconBadge(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            else IconBadge(step.icon)
        },
        headlineContent = { Text(step.title) },
        supportingContent = {
            Column {
                Text(step.body)
                if (!done) {
                    Spacer(Modifier.height(10.dp))
                    if (step.check == Check.Todo) Button(onClick = step.onAction) { Text(step.action) }
                    else FilledTonalButton(onClick = step.onAction) { Text(step.action) }
                }
            }
        },
    )
}

// ================================================================ Apps

@Composable
private fun AppsScreen(s: BanState) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var onlyBanned by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    // Banned apps that aren't installed right now stay listed: the ban is on the package name.
    val (banned, others) = remember(s.installed, s.blocked, query) {
        val q = query.trim().lowercase()
        fun ok(a: AppItem) = q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.lowercase().contains(q)
        val installedPkgs = s.installed.map { it.pkg }.toSet()
        val missing = (s.blocked - installedPkgs).map { AppItem(it, it, null, false) }
        val ban = (s.installed.filter { it.pkg in s.blocked } + missing).filter(::ok).sortedBy { it.label.lowercase() }
        val rest = s.installed.filter { it.pkg !in s.blocked }.filter(::ok)
        ban to rest
    }

    Screen(
        "Apps",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add by package") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search ${s.installed.size} apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyBanned, onClick = { onlyBanned = false }, label = { Text("All") })
                FilterChip(selected = onlyBanned, onClick = { onlyBanned = true }, label = { Text("Banned · ${s.blocked.size}") })
            }

            if (s.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (banned.isNotEmpty()) {
                    item(key = "h-banned") { SectionTitle("Banned") }
                    items(banned, key = { it.pkg }) { app ->
                        AppRow(app, banned = true, locked = s.locked, modifier = Modifier.animateItem()) {
                            toast(ctx, s.setApp(app.pkg, false))
                        }
                    }
                } else if (onlyBanned) {
                    item(key = "none") {
                        Text(
                            "No banned apps. Switch to All and tick the ones you want gone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (!onlyBanned) {
                    item(key = "h-all") { SectionTitle("All apps") }
                    items(others, key = { it.pkg }) { app ->
                        AppRow(app, banned = false, locked = s.locked, modifier = Modifier.animateItem()) {
                            toast(ctx, s.setApp(app.pkg, true))
                        }
                    }
                }
            }
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
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = transparentListColors(),
        leadingContent = {
            if (app.icon != null) Image(app.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            else IconBadge(Icons.Rounded.Apps)
        },
        headlineContent = { Text(app.label, maxLines = 1) },
        supportingContent = {
            Text(if (app.installed) app.pkg else "Not installed · still banned", maxLines = 1)
        },
        trailingContent = {
            if (banned && locked) Icon(Icons.Rounded.Lock, "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else Switch(checked = banned, onCheckedChange = null)
        },
    )
}

@Composable
private fun AddPackageDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ban by package name") },
        text = {
            Column {
                Text(
                    "For apps that aren't in the list, or aren't installed yet. An app's package name is in its Play Store link after id=.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("com.reddit.frontpage") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }) { Text("Ban") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================ Sites

@Composable
private fun SitesScreen(s: BanState) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    fun add() {
        val err = s.addSite(text)
        if (err != null) toast(ctx, err) else text = ""
    }

    Screen("Websites") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Add a website") },
                    placeholder = { Text("example.com") },
                    leadingIcon = { Icon(Icons.Rounded.Language, null) },
                    trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Rounded.Add, "Ban website") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item { SectionTitle("Banned websites · ${s.sites.size}") }
            items(s.sites.sorted(), key = { it }) { site ->
                ListItem(
                    modifier = Modifier.animateItem(),
                    colors = transparentListColors(),
                    leadingContent = { IconBadge(Icons.Rounded.Language) },
                    headlineContent = { Text(site) },
                    trailingContent = {
                        if (s.locked) Icon(Icons.Rounded.Lock, "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else IconButton(onClick = { toast(ctx, s.removeSite(site)) }) { Icon(Icons.Rounded.Delete, "Remove $site") }
                    },
                )
            }
            item {
                Text(
                    "Opening these in Chrome, Brave, Edge, Firefox, Samsung Internet, Opera or DuckDuckGo sends you home. " +
                        "Subdomains count too: banning x.com also bans mobile.x.com.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}
