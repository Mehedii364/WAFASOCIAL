package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PostComment
import com.example.data.model.SocialPost
import com.example.data.model.StoryItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialPostCard(
    post: SocialPost,
    currentUserId: String,
    liked: Boolean,
    bookmarked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    onBookmark: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onEdit: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onHashtagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val isMine = post.authorId == currentUserId

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (post.pinned || !post.communityName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    if (post.pinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.PushPin,
                                    contentDescription = "Pinned post",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Pinned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (!post.communityName.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Groups,
                                    contentDescription = "Community post",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    post.communityName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WafaAvatar(
                    name = post.authorName,
                    avatarData = post.authorAvatar,
                    size = 42.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (post.authorVerified) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${post.authorHandle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = " · ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (post.postType == "REEL" || post.postType == "QUESTION") {
                    Surface(
                        color = if (post.postType == "REEL") MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (post.postType == "REEL") Icons.Rounded.PlayCircle else Icons.Rounded.HelpOutline,
                                contentDescription = post.postType,
                                modifier = Modifier.size(13.dp),
                                tint = if (post.postType == "REEL") MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (post.postType == "REEL") "Reel" else "Q&A",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (post.postType == "REEL") MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("post_more_${post.id}")
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Post options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Edit Post") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (post.pinned) "Unpin from Profile" else "Pin to Profile") },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(post.content, !post.pinned)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report Content") },
                                leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                                onClick = {
                                    menuExpanded = false
                                    onReport()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Body copy
            if (post.postType == "QUESTION") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Hashtag chips
            if (post.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    post.hashtags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable(enabled = onHashtagClick != null) {
                                onHashtagClick?.invoke(tag)
                            }
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Attached media
            if (!post.mediaData.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                val ratio = when (post.mediaAspect) {
                    "1:1" -> 1f
                    "4:3" -> 4f / 3f
                    "9:16" -> 3f / 4f
                    else -> 16f / 10f
                }
                WafaMediaImage(
                    mediaData = post.mediaData,
                    contentDescription = "Media shared by ${post.authorName}",
                    aspectRatio = ratio
                )
            }

            // Interactive Poll
            if (post.postType == "POLL" && post.pollOptions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val totalVotes = post.pollVoteCounts.sum().coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.pollOptions.forEachIndexed { idx, option ->
                        val votes = post.pollVoteCounts.getOrElse(idx) { 0L }
                        val fraction = if (post.pollVoteCounts.sum() == 0L) 0f else votes.toFloat() / totalVotes.toFloat()
                        val pct = (fraction * 100f).toInt()
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onVotePoll(idx) }
                                .testTag("poll_option_${post.id}_$idx")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$pct% ($votes)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            }
                        }
                    }
                    Text(
                        text = "${post.pollVoteCounts.sum()} total votes · Tap an option to vote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))

            // Action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostActionMetricButton(
                    icon = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    label = post.likesCount.toString(),
                    tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDesc = if (liked) "Unlike post" else "Like post",
                    onClick = onLike,
                    tag = "like_btn_${post.id}"
                )
                PostActionMetricButton(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    label = post.commentsCount.toString(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDesc = "Comments",
                    onClick = onComment,
                    tag = "comment_btn_${post.id}"
                )
                PostActionMetricButton(
                    icon = Icons.Rounded.Repeat,
                    label = post.repostsCount.toString(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDesc = "Repost",
                    onClick = onRepost,
                    tag = "repost_btn_${post.id}"
                )
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Save post",
                        tint = if (bookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${post.authorName} (@${post.authorHandle}) on Wafa Social:\n\n${post.content}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share via Wafa Social"))
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var editedText by remember(post.id) { mutableStateOf(post.content) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Post") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { if (it.length <= 2000) editedText = it },
                    label = { Text("Post content") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEdit(editedText, post.pinned)
                        showEditDialog = false
                    },
                    enabled = editedText.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PostActionMetricButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    contentDesc: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun CommentSheetDialog(
    post: SocialPost,
    comments: List<PostComment>,
    onDismiss: () -> Unit,
    onSendComment: (String, String?) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Comments (${comments.size})", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close comments")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (comments.isEmpty()) {
                        item {
                            Text(
                                text = "Be the first to share a thoughtful reply.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            val isReply = !comment.parentCommentId.isNullOrBlank()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isReply) 28.dp else 0.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                WafaAvatar(name = comment.authorName, avatarData = comment.authorAvatar, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(comment.authorName, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            formatRelativeTime(comment.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(
                                        onClick = { replyingTo = comment },
                                        modifier = Modifier.padding(top = 0.dp)
                                    ) {
                                        Text("Reply", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (replyingTo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replying to @${replyingTo!!.authorHandle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { replyingTo = null }) { Text("Cancel") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 1000) draft = it },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (draft.trim().isNotEmpty()) {
                                onSendComment(draft.trim(), replyingTo?.id)
                                draft = ""
                                replyingTo = null
                            }
                        },
                        enabled = draft.trim().isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("send_comment_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Post comment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportContentDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val reasons = listOf("Spam", "Harassment", "Misinformation", "Unsafe Content", "Impersonation")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $targetLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reports are routed to the Wafa Social moderation queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) }
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 800) details = it },
                    label = { Text("Additional context (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(selectedReason, details)
                onDismiss()
            }) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StoryViewerDialog(
    story: StoryItem,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onViewed: () -> Unit,
    onReact: () -> Unit,
    onToggleHighlight: (Boolean) -> Unit
) {
    LaunchedEffect(story.id) { onViewed() }

    val bgColor = remember(story.accentHex) { parseAccentHex(story.accentHex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(bgColor.copy(alpha = 0.92f), Color(0xFF090D14))
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WafaAvatar(name = story.authorName, avatarData = story.authorAvatar, size = 40.dp, ringActive = true)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(story.authorName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = "@${story.authorHandle} · ${formatRelativeTime(story.createdAt)}" +
                                if (story.highlighted) " · Highlight" else " · 24h Moment",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close story", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!story.mediaData.isNullOrBlank()) {
                            WafaMediaImage(
                                mediaData = story.mediaData,
                                contentDescription = "Story image",
                                aspectRatio = 3f / 4f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                        if (!story.stickerTag.isNullOrBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(story.stickerTag, style = MaterialTheme.typography.labelLarge, color = Color.White)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        if (!story.overlayText.isNullOrBlank()) {
                            Text(
                                text = story.overlayText,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (story.caption.isNotBlank()) {
                            Text(
                                text = story.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${story.viewsCount} views · ${story.reactionsCount} reactions", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isOwner) {
                            OutlinedButton(
                                onClick = { onToggleHighlight(!story.highlighted) },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Text(if (story.highlighted) "Unpin" else "Highlight", color = Color.White)
                            }
                        }
                        Button(onClick = onReact) {
                            Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("React")
                        }
                    }
                }
            }
        }
    }
}
