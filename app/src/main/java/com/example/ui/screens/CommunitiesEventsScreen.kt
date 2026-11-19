package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Community
import com.example.data.model.SocialEvent
import com.example.data.model.UiState
import com.example.ui.components.WafaEmptyState
import com.example.ui.viewmodel.WafaSocialViewModel

enum class CommunitySection(val label: String) {
    COMMUNITIES("Communities"),
    EVENTS_LIVE("Events & Live")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunitiesEventsScreen(
    viewModel: WafaSocialViewModel,
    initialSection: CommunitySection = CommunitySection.COMMUNITIES
) {
    val publicCommunities by viewModel.publicCommunities.collectAsStateWithLifecycle()
    val myCommunities by viewModel.myCommunities.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val postsState by viewModel.feedPostsState.collectAsStateWithLifecycle()

    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var showCreateCommunity by remember { mutableStateOf(false) }
    var showCreateEvent by remember { mutableStateOf(false) }
    var selectedCommunity by remember { mutableStateOf<Community?>(null) }

    val mergedCommunities = remember(publicCommunities, myCommunities) {
        (myCommunities + publicCommunities).distinctBy { it.id }
    }
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (section == CommunitySection.COMMUNITIES) "Communities" else "Events & Live",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (section == CommunitySection.COMMUNITIES)
                        "Public, private & role-managed discussion spaces"
                    else
                        "RSVPs, schedules & external streaming setup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = {
                    if (section == CommunitySection.COMMUNITIES) showCreateCommunity = true
                    else showCreateEvent = true
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("create_community_or_event_btn")
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = if (section == CommunitySection.COMMUNITIES) "Create community" else "Create event"
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CommunitySection.entries.forEach { tab ->
                FilterChip(
                    selected = section == tab,
                    onClick = { section = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("section_tab_${tab.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (section) {
            CommunitySection.COMMUNITIES -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (mergedCommunities.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.Groups,
                                title = "Build a Community on Wafa Social",
                                subtitle = "Create public or private topic groups with rules, pinned announcements, and member roles.",
                                actionLabel = "Create Community",
                                onAction = { showCreateCommunity = true }
                            )
                        }
                    } else {
                        items(mergedCommunities, key = { it.id }) { community ->
                            val isMember = viewModel.currentUserId in community.memberIds
                            val isPending = viewModel.currentUserId in community.pendingMemberIds
                            val roleLabel = when {
                                community.ownerId == viewModel.currentUserId -> "Owner"
                                viewModel.currentUserId in community.moderatorIds -> "Moderator"
                                isMember -> "Member"
                                else -> null
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCommunity = community }
                                    .testTag("community_card_${community.id}")
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = if (community.visibility == "public") Icons.Rounded.Public else Icons.Rounded.Lock,
                                                contentDescription = community.visibility,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(community.name, style = MaterialTheme.typography.titleLarge)
                                        }
                                        if (roleLabel != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    roleLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${community.category} · ${community.memberCount} members · ${community.visibility.replace('_', ' ')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(community.description, style = MaterialTheme.typography.bodyMedium)
                                    if (community.announcement.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(community.announcement, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (community.ownerId != viewModel.currentUserId) {
                                            if (isMember) {
                                                OutlinedButton(onClick = { viewModel.toggleCommunityMembership(community) }) {
                                                    Text("Leave")
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.toggleCommunityMembership(community) },
                                                    enabled = !isPending
                                                ) {
                                                    Text(if (isPending) "Request Pending" else if (community.visibility == "private") "Request Join" else "Join")
                                                }
                                            }
                                        }
                                        TextButton(onClick = { selectedCommunity = community }) {
                                            Text("Open Space")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CommunitySection.EVENTS_LIVE -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Honest Live Streaming Infrastructure Readiness Banner
                    item(key = "live_infra_notice") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Rounded.LiveTv, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Live Streaming Architecture Status", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Event creation, scheduled live sessions, and real-time RSVPs are backed by Cloud Firestore. Real-time broadcast video requires connecting an external RTMP/WebRTC provider (e.g., LiveKit, Mux, or IVS) in your deployment.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (events.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "No upcoming events yet",
                                subtitle = "Schedule an in-person meetup, online town hall, or upcoming live broadcast session.",
                                actionLabel = "Create Event / Schedule Live",
                                onAction = { showCreateEvent = true }
                            )
                        }
                    } else {
                        items(events, key = { it.id }) { event ->
                            val isGoing = viewModel.currentUserId in event.rsvpGoingIds
                            val isInterested = viewModel.currentUserId in event.rsvpInterestedIds
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            color = if (event.eventType == "LIVE_SCHEDULED") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (event.eventType == "LIVE_SCHEDULED") "SCHEDULED LIVE" else "EVENT",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (event.eventType == "LIVE_SCHEDULED") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text("Hosted by ${event.hostName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(event.title, style = MaterialTheme.typography.titleLarge)
                                    Text(event.description, style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text(event.startTimeText, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(6.dp))
                                        Text(event.locationOrLink, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!event.streamProviderNote.isNullOrBlank()) {
                                        Text(
                                            "Stream config: ${event.streamProviderNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isGoing) {
                                            Button(onClick = { viewModel.toggleEventRsvp(event, going = true) }) {
                                                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Going (${event.rsvpGoingIds.size})")
                                            }
                                        } else {
                                            FilledTonalButton(onClick = { viewModel.toggleEventRsvp(event, going = true) }) {
                                                Text("Going (${event.rsvpGoingIds.size})")
                                            }
                                        }
                                        OutlinedButton(onClick = { viewModel.toggleEventRsvp(event, going = false) }) {
                                            Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (isInterested) "Interested ✓ (${event.rsvpInterestedIds.size})" else "Interested (${event.rsvpInterestedIds.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateCommunity) {
        CreateCommunityDialog(
            onDismiss = { showCreateCommunity = false },
            onCreate = { name, desc, cat, vis, rules, ann ->
                viewModel.createCommunity(name, desc, cat, vis, rules, ann) {
                    showCreateCommunity = false
                }
            }
        )
    }

    if (showCreateEvent) {
        CreateEventDialog(
            onDismiss = { showCreateEvent = false },
            onCreate = { title, desc, type, loc, time, streamNote ->
                viewModel.createEvent(title, desc, type, loc, time, streamNote) {
                    showCreateEvent = false
                }
            }
        )
    }

    if (selectedCommunity != null) {
        val fresh = mergedCommunities.firstOrNull { it.id == selectedCommunity!!.id } ?: selectedCommunity!!
        val communityPosts = allPosts.filter { it.communityId == fresh.id }
        CommunityDetailDialog(
            community = fresh,
            communityPostsCount = communityPosts.size,
            isManager = fresh.ownerId == viewModel.currentUserId || viewModel.currentUserId in fresh.moderatorIds,
            onDismiss = { selectedCommunity = null },
            onUpdateAnnouncement = { ann -> viewModel.updateCommunityAnnouncement(fresh.id, ann) },
            onApproveMember = { uid -> viewModel.approveCommunityMember(fresh, uid) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCommunityDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Technology") }
    var visibility by remember { mutableStateOf("public") }
    var rules by remember { mutableStateOf("Be constructive, respect privacy, and share authentic content.") }
    var announcement by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Community") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it }, label = { Text("Community name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { if (it.length <= 40) category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { if (it.length <= 600) description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("public" to "Public", "private" to "Private", "invite_only" to "Invite-only").forEach { (key, lbl) ->
                        FilterChip(selected = visibility == key, onClick = { visibility = key }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(value = rules, onValueChange = { if (it.length <= 1000) rules = it }, label = { Text("Community rules") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = announcement, onValueChange = { if (it.length <= 500) announcement = it }, label = { Text("Pinned announcement (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, category, visibility, rules, announcement) },
                enabled = name.trim().length >= 2 && description.trim().isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateEventDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("EVENT") }
    var location by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var streamNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Event / Live Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = eventType == "EVENT", onClick = { eventType = "EVENT" }, label = { Text("Event") })
                    FilterChip(selected = eventType == "LIVE_SCHEDULED", onClick = { eventType = "LIVE_SCHEDULED" }, label = { Text("Scheduled Live") })
                }
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 120) title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { if (it.length <= 1000) description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = timeText, onValueChange = { if (it.length <= 80) timeText = it }, label = { Text("Date & Time (e.g. Sat 18:00 UTC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { if (it.length <= 200) location = it }, label = { Text("Location or Online URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (eventType == "LIVE_SCHEDULED") {
                    OutlinedTextField(value = streamNote, onValueChange = { if (it.length <= 200) streamNote = it }, label = { Text("RTMP / Provider note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, description, eventType, location, timeText, streamNote.takeIf { it.isNotBlank() }) },
                enabled = title.trim().length >= 2 && description.trim().isNotEmpty() && location.trim().isNotEmpty() && timeText.trim().isNotEmpty()
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommunityDetailDialog(
    community: Community,
    communityPostsCount: Int,
    isManager: Boolean,
    onDismiss: () -> Unit,
    onUpdateAnnouncement: (String) -> Unit,
    onApproveMember: (String) -> Unit
) {
    var announcementDraft by remember(community.id) { mutableStateOf(community.announcement) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 580.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(community.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${community.category} · ${community.memberCount} members · $communityPostsCount posts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close community details")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(community.description, style = MaterialTheme.typography.bodyMedium)
                if (community.rulesText.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Community Rules", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(community.rulesText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (isManager) {
                    OutlinedTextField(
                        value = announcementDraft,
                        onValueChange = { if (it.length <= 500) announcementDraft = it },
                        label = { Text("Pinned Announcement") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { onUpdateAnnouncement(announcementDraft) }) {
                        Text("Update Announcement")
                    }
                    if (community.pendingMemberIds.isNotEmpty()) {
                        Text("Pending Join Requests (${community.pendingMemberIds.size})", style = MaterialTheme.typography.titleSmall)
                        community.pendingMemberIds.forEach { pendingUid ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Member ${pendingUid.take(8)}...", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onApproveMember(pendingUid) }) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        }
    }
}
