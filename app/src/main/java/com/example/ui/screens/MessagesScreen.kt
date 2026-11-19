package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.ImageEncoder
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.WafaAvatar
import com.example.ui.components.WafaEmptyState
import com.example.ui.components.WafaMediaImage
import com.example.ui.components.formatRelativeTime
import com.example.ui.viewmodel.WafaSocialViewModel
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(
    viewModel: WafaSocialViewModel
) {
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessages.collectAsStateWithLifecycle()
    val publicProfiles by viewModel.publicProfiles.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }

    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val activeConversation = conversations.firstOrNull { it.id == activeConvId }

    if (activeConversation != null) {
        BackHandler { viewModel.openConversation(null) }
        ChatThreadView(
            conversation = activeConversation,
            messagesState = activeMessagesState,
            currentUserId = viewModel.currentUserId,
            onBack = { viewModel.openConversation(null) },
            onSendMessage = { text, attachment, replyTo ->
                viewModel.sendMessage(activeConversation, text, attachment, replyTo)
            },
            onTypingChanged = { typing -> viewModel.setTyping(activeConversation, typing) },
            onReact = { msg, code -> viewModel.reactToMessage(activeConversation.id, msg, code) },
            onEditOrDelete = { msgId, txt, del -> viewModel.editOrDeleteMessage(activeConversation.id, msgId, txt, del) },
            onMarkRead = { msg -> viewModel.markMessageRead(activeConversation.id, msg) },
            onReportMessage = { msg, reason, details ->
                viewModel.submitReport("message", msg.id, reason, details)
            }
        )
        return
    }

    val filtered = remember(conversations, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) conversations
        else conversations.filter {
            (it.title?.lowercase()?.contains(q) == true) ||
                it.participantLabels.any { lbl -> lbl.lowercase().contains(q) } ||
                it.lastMessageText.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Messages", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Real-time Firestore sync · 1-to-1 & Group Threads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("new_conversation_btn")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = "Start new conversation")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (val st = conversationsState) {
                is UiState.Loading -> {
                    item {
                        Text(
                            "Syncing conversations...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Error -> {
                    item {
                        WafaEmptyState(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            title = "Could not load messages",
                            subtitle = st.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (filtered.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.AutoMirrored.Rounded.Chat,
                                title = "No conversations yet",
                                subtitle = "Start a private direct message or create a group chat with members of Wafa Social.",
                                actionLabel = "Start a Chat",
                                onAction = { showNewChatDialog = true }
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { conv ->
                            val displayTitle = conv.title?.takeIf { it.isNotBlank() }
                                ?: conv.participantLabels.joinToString(", ").ifBlank { "Direct Chat" }
                            val othersTyping = conv.typingUserIds.any { it != viewModel.currentUserId }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openConversation(conv.id) }
                                    .testTag("conversation_row_${conv.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (conv.groupChat) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Groups, contentDescription = "Group chat", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    } else {
                                        WafaAvatar(name = displayTitle, avatarData = null, size = 46.dp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatRelativeTime(conv.updatedAt ?: conv.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = when {
                                                othersTyping -> "Typing..."
                                                conv.lastMessageText.isNotBlank() -> conv.lastMessageText
                                                else -> "Tap to say hello"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (othersTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        val otherMembers = remember(publicProfiles) {
            publicProfiles.filter { it.userId != viewModel.currentUserId }
        }
        val selected = remember { mutableStateListOf<PublicProfile>() }
        var groupTitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("New Conversation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (otherMembers.isEmpty()) {
                        Text(
                            "No other public accounts have signed in yet. Invite another Google account to test real-time multi-user messaging.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (selected.size > 1) {
                            OutlinedTextField(
                                value = groupTitle,
                                onValueChange = { if (it.length <= 80) groupTitle = it },
                                label = { Text("Group chat name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(otherMembers, key = { it.userId }) { member ->
                                val isChecked = selected.any { it.userId == member.userId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) selected.removeAll { it.userId == member.userId }
                                            else selected.add(member)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selected.add(member)
                                            else selected.removeAll { it.userId == member.userId }
                                        }
                                    )
                                    WafaAvatar(name = member.displayName, avatarData = member.avatarData, size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text("@${member.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startConversation(selected.toList(), groupTitle.takeIf { it.isNotBlank() }) {
                            showNewChatDialog = false
                        }
                    },
                    enabled = selected.isNotEmpty()
                ) {
                    Text(if (selected.size > 1) "Create Group" else "Start Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChatThreadView(
    conversation: Conversation,
    messagesState: UiState<List<ChatMessage>>,
    currentUserId: String,
    onBack: () -> Unit,
    onSendMessage: (String, String?, String?) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onEditOrDelete: (String, String, Boolean) -> Unit,
    onMarkRead: (ChatMessage) -> Unit,
    onReportMessage: (ChatMessage, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var attachmentData by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var reportingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                attachmentData = ImageEncoder.uriToCompressedDataUri(context, uri, maxDimensionPx = 560, maxChars = 210_000)
            }
        }
    }

    val title = conversation.title?.takeIf { it.isNotBlank() }
        ?: conversation.participantLabels.joinToString(", ").ifBlank { "Conversation" }
    val othersTyping = conversation.typingUserIds.any { it != currentUserId }

    Column(modifier = Modifier.fillMaxSize()) {
        // Thread Header
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to messages")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (othersTyping) "Typing..." else "${conversation.participantIds.size} participants · Live sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (othersTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Messages List
        val messages = (messagesState as? UiState.Success)?.data.orEmpty()
        LaunchedEffect(messages.size) {
            messages.lastOrNull()?.let { latest ->
                if (latest.senderId != currentUserId && currentUserId !in latest.readBy) {
                    onMarkRead(latest)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUserId
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!isMine) {
                                Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (!msg.replyToText.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Replying to: ${msg.replyToText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                            if (!msg.attachmentData.isNullOrBlank() && !msg.deleted) {
                                WafaMediaImage(
                                    mediaData = msg.attachmentData,
                                    contentDescription = "Shared photo attachment",
                                    aspectRatio = 4f / 3f
                                )
                            }
                            if (msg.text.isNotBlank()) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = if (msg.deleted) FontStyle.Italic else FontStyle.Normal,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatRelativeTime(msg.createdAt) + if (msg.edited) " · edited" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isMine) {
                                    Icon(
                                        imageVector = if (msg.readBy.size > 1) Icons.Rounded.DoneAll else Icons.Rounded.Check,
                                        contentDescription = if (msg.readBy.size > 1) "Read" else "Sent",
                                        tint = if (msg.readBy.size > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (!msg.reactionCode.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = msg.reactionCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Compact message action strip
                    if (!msg.deleted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { replyTarget = msg }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = "Reply", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onReact(msg, "Liked") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Favorite, contentDescription = "React", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isMine) {
                                IconButton(onClick = { editingMessage = msg }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit message", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onEditOrDelete(msg.id, "", true) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete message", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                IconButton(onClick = { reportingMessage = msg }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Flag, contentDescription = "Report message", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reply / Attachment preview strip
        if (replyTarget != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Replying to ${replyTarget!!.senderName}: ${replyTarget!!.text.take(40)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (attachmentData != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Photo attachment ready to send", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    IconButton(onClick = { attachmentData = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Message Composer Bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Attach photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        if (it.length <= 2000) {
                            inputText = it
                            onTypingChanged(it.isNotBlank())
                        }
                    },
                    placeholder = { Text("Write a message...") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(
                    onClick = {
                        onSendMessage(inputText, attachmentData, replyTarget?.text)
                        inputText = ""
                        attachmentData = null
                        replyTarget = null
                        onTypingChanged(false)
                    },
                    enabled = inputText.trim().isNotEmpty() || attachmentData != null,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                }
            }
        }
    }

    if (editingMessage != null) {
        var edited by remember(editingMessage!!.id) { mutableStateOf(editingMessage!!.text) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { if (it.length <= 2000) edited = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditOrDelete(editingMessage!!.id, edited, false)
                        editingMessage = null
                    },
                    enabled = edited.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    if (reportingMessage != null) {
        ReportContentDialog(
            targetLabel = "Message from ${reportingMessage!!.senderName}",
            onDismiss = { reportingMessage = null },
            onSubmit = { reason, details -> onReportMessage(reportingMessage!!, reason, details) }
        )
    }
}
