package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.WafaAiModel
import com.example.ui.components.MetricStatTile
import com.example.ui.components.WafaEmptyState
import com.example.ui.components.formatRelativeTime
import com.example.ui.viewmodel.AiAssistantUiState
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.AiTaskPreset
import com.example.ui.viewmodel.WafaSocialViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreen(
    socialViewModel: WafaSocialViewModel,
    aiViewModel: AiAssistantViewModel,
    onBack: () -> Unit,
    onApplyToComposer: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val selectedModel by aiViewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedPreset by aiViewModel.selectedPreset.collectAsStateWithLifecycle()
    val uiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val usageLogs by socialViewModel.aiUsageLogs.collectAsStateWithLifecycle()

    var promptInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Wafa AI Studio", subtitle = "Powered by Firebase AI Logic · App Check Attested", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Model Selector
            Text("Model Selection", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WafaAiModel.entries.forEach { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { aiViewModel.selectModel(model) },
                        label = { Text("${model.label} · ${model.badge}") }
                    )
                }
            }

            // Task Preset Selector
            Text("Assistant Capability", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AiTaskPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { aiViewModel.selectPreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }

            OutlinedTextField(
                value = promptInput,
                onValueChange = { if (it.length <= 2000) promptInput = it },
                label = { Text(selectedPreset.hint) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_studio_prompt_input")
            )

            Button(
                onClick = { aiViewModel.runStreamingTask(selectedPreset, promptInput) },
                enabled = promptInput.trim().isNotEmpty() && uiState !is AiAssistantUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_studio_run_btn")
            ) {
                if (uiState is AiAssistantUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting to ${selectedModel.label}...")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Response")
                }
            }

            // Output Area
            when (val state = uiState) {
                is AiAssistantUiState.Streaming -> {
                    AiOutputCard(text = state.text, isStreaming = true, onSendToComposer = null)
                }
                is AiAssistantUiState.TextSuccess -> {
                    AiOutputCard(
                        text = state.text,
                        isStreaming = false,
                        onSendToComposer = { onApplyToComposer(state.text) }
                    )
                }
                is AiAssistantUiState.StructuredSuccess -> {
                    val combined = buildString {
                        append(state.suggestion.polishedText)
                        if (state.suggestion.hashtags.isNotEmpty()) {
                            append("\n\n")
                            append(state.suggestion.hashtags.joinToString(" ") { "#$it" })
                        }
                    }
                    AiOutputCard(text = combined, isStreaming = false, onSendToComposer = { onApplyToComposer(combined) })
                }
                is AiAssistantUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                else -> Unit
            }

            // AI Usage Tracking Log
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Usage Tracking (${usageLogs.size} requests logged)", style = MaterialTheme.typography.titleSmall)
            if (usageLogs.isEmpty()) {
                Text(
                    "Your AI requests are tracked privately under your account to help monitor usage. The AI assistant never publishes content automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                usageLogs.take(8).forEach { log ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${log.taskType.uppercase()} · ${log.modelName}", style = MaterialTheme.typography.labelMedium)
                                Text("${log.promptChars} in / ${log.responseChars} out chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRelativeTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiOutputCard(
    text: String,
    isStreaming: Boolean,
    onSendToComposer: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(if (isStreaming) "Streaming response..." else "AI Draft Ready for Review", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (isStreaming) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (onSendToComposer != null) {
                Button(onClick = onSendToComposer) {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use in Post Composer")
                }
            }
        }
    }
}

@Composable
fun CreatorStudioScreen(
    viewModel: WafaSocialViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val analytics by viewModel.creatorAnalytics.collectAsStateWithLifecycle()
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    var verifCategory by remember { mutableStateOf("Creator / Engineer") }
    var verifReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Creator Studio", subtitle = "Live Firestore performance & eligibility", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatTile("Followers", analytics.followers.toString(), Modifier.weight(1f))
                    MetricStatTile("Total Reach", analytics.totalViews.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatTile("Likes", analytics.totalLikes.toString(), Modifier.weight(1f))
                    MetricStatTile("Comments", analytics.totalComments.toString(), Modifier.weight(1f))
                    MetricStatTile("Reposts", analytics.totalReposts.toString(), Modifier.weight(1f))
                }
            }
            item {
                MetricStatTile(
                    label = "Engagement Rate (${analytics.totalPosts} published posts)",
                    value = String.format(Locale.US, "%.1f%%", analytics.engagementRatePercent),
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }

            // Verification Application
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (profile?.verified == true) "Account Verified" else "Apply for Profile Verification",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (profile?.verified == true) {
                            Text("Your Wafa Social identity carries a verified creator badge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedTextField(value = verifCategory, onValueChange = { if (it.length <= 60) verifCategory = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = verifReason, onValueChange = { if (it.length <= 500) verifReason = it }, label = { Text("Why should this account be verified?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = {
                                    viewModel.submitVerificationRequest(verifCategory, verifReason) {
                                        verifReason = ""
                                    }
                                },
                                enabled = verifReason.trim().length >= 5
                            ) { Text("Submit Verification Request") }
                        }
                    }
                }
            }

            // Honest Monetization Architecture Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MonetizationOn, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Monetization Readiness Architecture", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Eligibility Criteria: Verified identity, 50+ followers, and zero open safety violations. Payout processing requires integrating a verified external payment provider (e.g., Stripe Connect / Google Play Billing) — no simulated earnings are displayed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val eligible = profile?.verified == true && analytics.followers >= 50
                        Text(
                            text = if (eligible) "Eligibility: Qualified (Connect external payout provider)" else "Current Status: ${analytics.followers}/50 followers · Verified: ${if (profile?.verified == true) "Yes" else "Pending"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Top Performing Posts
            item { Text("Top Content Performance", style = MaterialTheme.typography.titleMedium) }
            if (analytics.topPosts.isEmpty()) {
                item {
                    Text("Publish content to see per-post performance breakdowns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(analytics.topPosts, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(post.content.take(110), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${post.viewsCount} views · ${post.likesCount} likes · ${post.commentsCount} comments · ${post.repostsCount} reposts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    viewModel: WafaSocialViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(
            title = "Notifications",
            subtitle = "In-app real-time activity feed",
            onBack = onBack,
            trailing = {
                if (notifications.any { !it.read }) {
                    TextButton(onClick = { viewModel.markAllNotificationsRead() }) {
                        Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Read all")
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (notifications.isEmpty()) {
                item {
                    WafaEmptyState(
                        icon = Icons.Rounded.Notifications,
                        title = "All caught up",
                        subtitle = "Likes, comments, follows, follow requests, and community updates will arrive here in real time."
                    )
                }
            } else {
                items(notifications, key = { it.id }) { item ->
                    Surface(
                        color = if (!item.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!item.read) viewModel.markNotificationRead(item.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.actorName, style = MaterialTheme.typography.titleSmall)
                                    Text(item.type.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatRelativeTime(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!item.read) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Unread notification", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
