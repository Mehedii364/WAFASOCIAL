package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.ImageEncoder
import com.example.data.model.FeedCategory
import com.example.data.model.SocialPost
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.ui.components.CommentSheetDialog
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.SocialPostCard
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.WafaAvatar
import com.example.ui.components.WafaEmptyState
import com.example.ui.components.WafaMediaImage
import com.example.ui.components.WafaSkeletonPostCard
import com.example.ui.components.parseAccentHex
import com.example.ui.viewmodel.WafaSocialViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeFeedScreen(
    viewModel: WafaSocialViewModel,
    onNavigateCreate: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenEvents: () -> Unit,
    onSearchHashtag: (String) -> Unit
) {
    val feedState by viewModel.visibleFeedState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedFeedCategory.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val likedPosts by viewModel.likedPosts.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val activeComments by viewModel.activePostComments.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()

    val likedIds = remember(likedPosts) { likedPosts.map { it.postId }.toSet() }
    val savedIds = remember(bookmarks) { bookmarks.map { it.postId }.toSet() }

    var commentingPost by remember { mutableStateOf<SocialPost?>(null) }
    var reportingPost by remember { mutableStateOf<SocialPost?>(null) }
    var viewingStory by remember { mutableStateOf<StoryItem?>(null) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Editorial Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Wafa Social",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "SOCIALVERSE FEED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenAiStudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("header_ai_studio_btn")
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "Open Wafa AI Studio",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onOpenEvents,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("header_events_btn")
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = "Events and Scheduled Live",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("header_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (unreadCount > 0) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stories & Moments Rail
            item(key = "stories_rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "add_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { showCreateStoryDialog = true }
                                .testTag("add_story_button")
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                WafaAvatar(
                                    name = myProfile?.displayName ?: "You",
                                    avatarData = myProfile?.avatarData,
                                    size = 58.dp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = "Add 24h story",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Add Story",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    items(stories, key = { it.id }) { story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { viewingStory = story }
                                .testTag("story_item_${story.id}")
                        ) {
                            WafaAvatar(
                                name = story.authorName,
                                avatarData = story.authorAvatar,
                                size = 58.dp,
                                ringActive = true
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = story.authorName.substringBefore(' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Feed Category Selector + Transparent Ranking Logic Note
            item(key = "feed_categories") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FeedCategory.entries) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.selectFeedCategory(category) },
                                label = { Text(category.label) },
                                modifier = Modifier.testTag("feed_tab_${category.name.lowercase()}")
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = selectedCategory.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Feed Stream
            when (val state = feedState) {
                is UiState.Loading -> {
                    items(3) { WafaSkeletonPostCard() }
                }
                is UiState.Error -> {
                    item {
                        WafaEmptyState(
                            icon = Icons.Rounded.DynamicFeed,
                            title = "Feed temporarily unavailable",
                            subtitle = state.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.DynamicFeed,
                                title = if (selectedCategory == FeedCategory.FOLLOWING) "Your Following feed is quiet" else "Start the conversation on Wafa Social",
                                subtitle = if (selectedCategory == FeedCategory.FOLLOWING)
                                    "Follow creators in Explore or publish your first post to see updates here."
                                else
                                    "Publish a post, photo, poll, or vertical reel — synced live across your community.",
                                actionLabel = "Create First Post",
                                onAction = onNavigateCreate
                            )
                        }
                    } else {
                        items(state.data, key = { it.id }) { post ->
                            SocialPostCard(
                                post = post,
                                currentUserId = viewModel.currentUserId,
                                liked = post.id in likedIds,
                                bookmarked = post.id in savedIds,
                                onLike = { viewModel.toggleLike(post) },
                                onComment = {
                                    commentingPost = post
                                    viewModel.openComments(post.id)
                                },
                                onRepost = { viewModel.repost(post) },
                                onBookmark = { viewModel.toggleBookmark(post.id) },
                                onVotePoll = { idx -> viewModel.votePoll(post, idx) },
                                onEdit = { newText, pinned -> viewModel.editPost(post.id, newText, pinned) },
                                onDelete = { viewModel.deletePost(post) },
                                onReport = { reportingPost = post },
                                onHashtagClick = onSearchHashtag
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (commentingPost != null) {
        CommentSheetDialog(
            post = commentingPost!!,
            comments = activeComments,
            onDismiss = {
                commentingPost = null
                viewModel.openComments(null)
            },
            onSendComment = { text, parentId ->
                viewModel.addComment(commentingPost!!, text, parentId)
            }
        )
    }

    if (reportingPost != null) {
        ReportContentDialog(
            targetLabel = "Post by @${reportingPost!!.authorHandle}",
            onDismiss = { reportingPost = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("post", reportingPost!!.id, reason, details)
            }
        )
    }

    if (viewingStory != null) {
        val currentStory = stories.firstOrNull { it.id == viewingStory!!.id } ?: viewingStory!!
        StoryViewerDialog(
            story = currentStory,
            isOwner = currentStory.authorId == viewModel.currentUserId,
            onDismiss = { viewingStory = null },
            onViewed = { viewModel.markStoryViewed(currentStory.id) },
            onReact = { viewModel.reactToStory(currentStory.id) },
            onToggleHighlight = { hl -> viewModel.toggleStoryHighlight(currentStory.id, hl) }
        )
    }

    if (showCreateStoryDialog) {
        CreateStoryDialog(
            onDismiss = { showCreateStoryDialog = false },
            onPublish = { caption, overlay, sticker, mediaData, accentHex, highlighted ->
                viewModel.createStory(caption, overlay, sticker, mediaData, accentHex, highlighted) {
                    showCreateStoryDialog = false
                }
            }
        )
    }
}

@Composable
private fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onPublish: (String, String?, String?, String?, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accents = listOf("#0F766E", "#1E3A8A", "#9A3412", "#4C1D95", "#0F172A")
    var selectedAccent by remember { mutableStateOf(accents.first()) }
    var caption by remember { mutableStateOf("") }
    var overlayText by remember { mutableStateOf("") }
    var stickerTag by remember { mutableStateOf("") }
    var mediaData by remember { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                mediaData = ImageEncoder.uriToCompressedDataUri(context, uri, maxDimensionPx = 640, maxChars = 220_000)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share 24h Moment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accents.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseAccentHex(hex))
                                .border(
                                    width = if (selectedAccent == hex) 2.5.dp else 1.dp,
                                    color = if (selectedAccent == hex) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedAccent = hex }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (mediaData == null) "Pick Story Photo (Optional)" else "Photo Attached · Replace")
                }
                if (!mediaData.isNullOrBlank()) {
                    WafaMediaImage(mediaData = mediaData!!, contentDescription = "Story preview", aspectRatio = 16f / 9f)
                }
                OutlinedTextField(
                    value = overlayText,
                    onValueChange = { if (it.length <= 140) overlayText = it },
                    label = { Text("Headline overlay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 280) caption = it },
                    label = { Text("Caption") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stickerTag,
                    onValueChange = { if (it.length <= 40) stickerTag = it },
                    label = { Text("Sticker tag (e.g. LIVE, UPDATE, MOOD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin to Profile Highlights", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = highlighted, onCheckedChange = { highlighted = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPublish(
                        caption.ifBlank { overlayText.ifBlank { "Shared a moment" } },
                        overlayText.takeIf { it.isNotBlank() },
                        stickerTag.takeIf { it.isNotBlank() },
                        mediaData,
                        selectedAccent,
                        highlighted
                    )
                },
                enabled = caption.isNotBlank() || overlayText.isNotBlank() || mediaData != null
            ) {
                Text("Publish Story")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
