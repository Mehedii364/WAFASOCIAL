package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.MetricStatTile
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.WafaSocialViewModel

@Composable
fun PrivacySecurityScreen(
    viewModel: WafaSocialViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val account by viewModel.userAccount.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val myReports by viewModel.myReports.collectAsStateWithLifecycle()

    var privateAccount by remember(account) { mutableStateOf(account?.privateAccount ?: false) }
    var allowMessagesFrom by remember(account) { mutableStateOf(account?.allowMessagesFrom ?: "everyone") }
    var birthYearInput by remember(account) { mutableStateOf(account?.birthYear?.toString().orEmpty()) }
    var teenSafetyMode by remember(account) { mutableStateOf(account?.teenSafetyMode ?: false) }
    var notifyLikes by remember(account) { mutableStateOf(account?.notifyLikes ?: true) }
    var notifyComments by remember(account) { mutableStateOf(account?.notifyComments ?: true) }
    var notifyFollows by remember(account) { mutableStateOf(account?.notifyFollows ?: true) }
    var notifyMessages by remember(account) { mutableStateOf(account?.notifyMessages ?: true) }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Privacy & Security Center", subtitle = "Zero-trust account controls & teen safeguards", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Privacy & Age Safety Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Account Visibility & Teen Safeguards", style = MaterialTheme.typography.titleMedium)
                    }
                    ToggleRow("Private Account (require follow approval)", privateAccount) { privateAccount = it }
                    ToggleRow("Teen Safety Safeguards (restrict unknown DMs)", teenSafetyMode) { teenSafetyMode = it }
                    OutlinedTextField(
                        value = birthYearInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) birthYearInput = it },
                        label = { Text("Birth Year (optional — under 18 auto-enables Teen Safety)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Who can message you", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("everyone" to "Everyone", "following" to "Following", "nobody" to "Nobody").forEach { (k, lbl) ->
                            FilterChip(
                                selected = allowMessagesFrom == k,
                                onClick = { allowMessagesFrom = k },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Notification Preferences", style = MaterialTheme.typography.labelLarge)
                    ToggleRow("Likes & Reactions", notifyLikes) { notifyLikes = it }
                    ToggleRow("Comments & Replies", notifyComments) { notifyComments = it }
                    ToggleRow("Followers & Requests", notifyFollows) { notifyFollows = it }
                    ToggleRow("Direct & Group Messages", notifyMessages) { notifyMessages = it }

                    Button(
                        onClick = {
                            viewModel.updatePrivacySettings(
                                privateAccount = privateAccount,
                                allowMessagesFrom = allowMessagesFrom,
                                birthYear = birthYearInput.toIntOrNull(),
                                teenSafetyMode = teenSafetyMode,
                                notifyLikes = notifyLikes,
                                notifyComments = notifyComments,
                                notifyFollows = notifyFollows,
                                notifyMessages = notifyMessages
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_privacy_settings_btn")
                    ) { Text("Save Privacy & Safety Settings") }
                }
            }

            // Active Device Sessions
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Active Device Sessions (${sessions.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    sessions.forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.deviceName, style = MaterialTheme.typography.titleSmall)
                                Text("${session.platform} · Active ${formatRelativeTime(session.lastActiveAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.revokeSession(session.id) }) { Text("Revoke") }
                        }
                    }
                }
            }

            // Blocked Accounts
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Blocked Accounts (${blockedUsers.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (blockedUsers.isEmpty()) {
                        Text("No blocked accounts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        blockedUsers.forEach { blocked ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(blocked.blockedName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${blocked.blockedHandle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { viewModel.unblockUser(blocked.blockedUserId) }) { Text("Unblock") }
                            }
                        }
                    }
                }
            }

            // My Submitted Reports
            if (myReports.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("My Safety Reports (${myReports.size})", style = MaterialTheme.typography.titleMedium)
                        myReports.forEach { rep ->
                            Text("${rep.targetType.uppercase()} · ${rep.reason} — Status: ${rep.status.uppercase()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Data Export & Account Controls
            OutlinedButton(
                onClick = { exportedJson = viewModel.buildAccountDataExportJson() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export My Account Data (JSON)")
            }

            val isDeactivated = account?.deactivated == true
            OutlinedButton(
                onClick = { viewModel.setDeactivated(!isDeactivated) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDeactivated) "Reactivate Public Account" else "Temporarily Deactivate Account")
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth().testTag("privacy_sign_out_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }
        }
    }

    if (exportedJson != null) {
        AlertDialog(
            onDismissRequest = { exportedJson = null },
            title = { Text("Account Data Export") },
            text = {
                Text(
                    exportedJson!!.take(1200),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            confirmButton = {
                Button(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, exportedJson)
                    }
                    context.startActivity(Intent.createChooser(share, "Export Wafa Social Data"))
                    exportedJson = null
                }) { Text("Share JSON") }
            },
            dismissButton = { TextButton(onClick = { exportedJson = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AdminControlPanelScreen(
    viewModel: WafaSocialViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val verifications by viewModel.adminVerificationRequests.collectAsStateWithLifecycle()
    val auditLogs by viewModel.adminAuditLogs.collectAsStateWithLifecycle()
    val profiles by viewModel.publicProfiles.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf("reports") }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Admin Control Panel", subtitle = "RBAC Verified · Immutable Audit Trail", onBack = onBack)

        if (!viewModel.isAdminUser) {
            Box(Modifier.padding(16.dp)) {
                Text("Administrative access requires verified Super Admin credentials.", color = MaterialTheme.colorScheme.error)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatTile("Public Users", profiles.size.toString(), Modifier.weight(1f))
                    MetricStatTile("Open Reports", reports.count { it.status == "open" }.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.error)
                    MetricStatTile("Verifications", verifications.count { it.status == "pending" }.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = section == "reports", onClick = { section = "reports" }, label = { Text("Reports (${reports.size})") })
                    FilterChip(selected = section == "verify", onClick = { section = "verify" }, label = { Text("Verify (${verifications.size})") })
                    FilterChip(selected = section == "audit", onClick = { section = "audit" }, label = { Text("Audit (${auditLogs.size})") })
                }
            }

            when (section) {
                "reports" -> {
                    if (reports.isEmpty()) {
                        item { Text("No safety reports in queue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${report.targetType.uppercase()} · ${report.reason}", style = MaterialTheme.typography.titleSmall)
                                    Text("Status: ${report.status.uppercase()} · Target: ${report.targetId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    if (report.details.isNotBlank()) Text(report.details, style = MaterialTheme.typography.bodySmall)
                                    if (report.status == "open" || report.status == "reviewed") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.resolveReportAsAdmin(report, "resolved", "Reviewed and resolved by admin") }) {
                                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Resolve")
                                            }
                                            OutlinedButton(onClick = { viewModel.resolveReportAsAdmin(report, "dismissed", "No policy violation found") }) {
                                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Dismiss")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "verify" -> {
                    if (verifications.isEmpty()) {
                        item { Text("No verification applications.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(verifications, key = { it.id }) { req ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("@${req.username} · ${req.category}", style = MaterialTheme.typography.titleSmall)
                                    Text(req.reason, style = MaterialTheme.typography.bodySmall)
                                    Text("Status: ${req.status.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    if (req.status == "pending") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.reviewVerificationAsAdmin(req, true) }) { Text("Approve Badge") }
                                            OutlinedButton(onClick = { viewModel.reviewVerificationAsAdmin(req, false) }) { Text("Reject") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (auditLogs.isEmpty()) {
                        item { Text("No administrative actions logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(auditLogs, key = { it.id }) { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${log.action} · ${formatRelativeTime(log.createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                    Text("By ${log.adminEmail} on ${log.targetPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAboutScreen(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Settings & About", subtitle = "Appearance & platform information", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Theme Switcher
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChanged(ThemeMode.DARK) },
                            leadingIcon = { Icon(Icons.Rounded.DarkMode, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Obsidian Dark") },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChanged(ThemeMode.LIGHT) },
                            leadingIcon = { Icon(Icons.Rounded.LightMode, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Daylight") },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChanged(ThemeMode.SYSTEM) },
                            label = { Text("System") }
                        )
                    }
                }
            }

            // About & Developer Credits (Section 30)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("About Wafa Social", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Wafa Social is an all-in-one Android social ecosystem unifying profiles, real-time feeds, 24-hour stories, vertical reels, direct & group messaging, communities, events, creator analytics, zero-trust privacy controls, and Gemini AI writing assistance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Platform Architecture",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "• Live Backend: Cloud Firestore + Zero-Trust Security Rules\n• Identity: Firebase Auth (Google Sign-In via Credential Manager)\n• AI Assistant: Firebase AI Logic (Gemini 3.8 Flash / 3.5 Flash-Lite)\n• External Infrastructure Optional: RTMP/WebRTC Live Video Server, FCM Push Server, Payment Payout Provider",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.developer_credit_full),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
