package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.ImageEncoder
import com.example.data.model.PublicProfile
import com.example.data.model.SocialPost
import com.example.data.model.UiState
import com.example.ui.components.CommentSheetDialog
import com.example.ui.components.SocialPostCard
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.WafaAvatar
import com.example.ui.components.WafaEmptyState
import com.example.ui.components.WafaMediaImage
import com.example.ui.viewmodel.WafaSocialViewModel
import kotlinx.coroutines.launch

private enum class ProfileTab(val label: String) {
    POSTS("Posts"),
    SAVED("Saved"),
    MEDIA("Media"),
    REQUESTS("Requests")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileHubScreen(
    viewModel: WafaSocialViewModel,
    onOpenCreatorStudio: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenPrivacyCenter: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    val account by viewModel.userAccount.collectAsStateWithLifecycle()
    val myPosts by viewModel.myPosts.collectAsStateWithLifecycle()
    val allPostsState by viewModel.feedPostsState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val likedPosts by viewModel.likedPosts.collectAsStateWithLifecycle()
    val followers by viewModel.followers.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val activeComments by viewModel.activePostComments.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(ProfileTab.POSTS) }
    var showEditDialog by remember { mutableStateOf(false) }
    var commentingPost by remember { mutableStateOf<SocialPost?>(null) }

    val likedIds = remember(likedPosts) { likedPosts.map { it.postId }.toSet() }
    val savedIds = remember(bookmarks) { bookmarks.map { it.postId }.toSet() }
    val allPosts = (allPostsState as? UiState.Success)?.data.orEmpty()
    val savedPosts = remember(allPosts, myPosts, savedIds) {
        (myPosts + allPosts).distinctBy { it.id }.filter { it.id in savedIds }
    }
    val mediaPosts = remember(myPosts) { myPosts.filter { !it.mediaData.isNullOrBlank() } }
    val pendingRequests = remember(followers) { followers.filter { it.status == "pending" } }
    val myHighlights = remember(stories) { stories.filter { it.authorId == viewModel.currentUserId && it.highlighted } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cover + Overlapping Avatar Identity Banner
        item(key = "profile_header") {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val coverBitmap = remember(profile?.coverData) {
                        ImageEncoder.decodeDataUriToImageBitmap(profile?.coverData)
                    }
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "Profile cover image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_default_cover),
                            contentDescription = "Default Wafa Social cover banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            IconButton(
                                onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Connect with ${profile?.displayName ?: "me"} (@${profile?.username ?: "member"}) on Wafa Social!"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share Wafa Social Profile"))
                                }
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share profile")
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("profile_settings_btn")
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings and About")
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp)
                            .offset(y = 34.dp)
                    ) {
                        WafaAvatar(
                            name = profile?.displayName ?: "Member",
                            avatarData = profile?.avatarData,
                            size = 76.dp,
                            ringActive = true
                        )
                    }
                }

                Spacer(Modifier.height(42.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile?.displayName ?: "Setting up profile...",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                if (profile?.verified == true) {
                                    Spacer(Modifier.width(6.dp))
                                    VerifiedBadge()
                                }
                                if (account?.privateAccount == true) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Rounded.Lock, contentDescription = "Private account", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "@${profile?.username ?: "member"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_profile_btn")
                        ) {
                            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile")
                        }
                    }

                    if (!profile?.bio.isNullOrBlank()) {
                        Text(profile!!.bio, style = MaterialTheme.typography.bodyMedium)
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!profile?.location.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(profile!!.location!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!profile?.website.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Link, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(profile!!.website!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (!profile?.interests.isNullOrEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            profile!!.interests.forEach { interest ->
                                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        interest,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Metrics Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatPill("Posts", myPosts.size.toString(), Modifier.weight(1f))
                        ProfileStatPill("Followers", (profile?.followersCount ?: 0L).toString(), Modifier.weight(1f))
                        ProfileStatPill("Following", (profile?.followingCount ?: 0L).toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        // Story Highlights Strip
        if (myHighlights.isNotEmpty()) {
            item(key = "highlights") {
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile Highlights", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(myHighlights, key = { it.id }) { story ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(10.dp).width(120.dp)) {
                                    Text(story.stickerTag ?: "Highlight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(story.overlayText ?: story.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Wafa Ecosystem Hub Cards (Creator Studio, AI Studio, Privacy Center, Notifications, Admin)
        item(key = "ecosystem_hub") {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wafa Social Workspace", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HubTile(Icons.Rounded.Analytics, "Creator Studio", "Analytics & reach", onOpenCreatorStudio, Modifier.weight(1f).testTag("hub_creator_studio"))
                    HubTile(Icons.Rounded.AutoAwesome, "Wafa AI Studio", "Gemini assistant", onOpenAiStudio, Modifier.weight(1f).testTag("hub_ai_studio"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HubTile(Icons.Rounded.Security, "Privacy Center", "Safety & sessions", onOpenPrivacyCenter, Modifier.weight(1f).testTag("hub_privacy_center"))
                    HubTile(Icons.Rounded.Notifications, "Notifications", "Activity alerts", onOpenNotifications, Modifier.weight(1f).testTag("hub_notifications"))
                }
                if (viewModel.isAdminUser) {
                    HubTile(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = "Admin Control Panel",
                        subtitle = "Moderation reports, verification approvals & immutable audit trail",
                        onClick = onOpenAdminPanel,
                        modifier = Modifier.fillMaxWidth().testTag("hub_admin_panel")
                    )
                }
            }
        }

        // Profile Tabs
        item(key = "profile_tabs") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ProfileTab.entries) { tab ->
                    val label = if (tab == ProfileTab.REQUESTS && pendingRequests.isNotEmpty()) {
                        "${tab.label} (${pendingRequests.size})"
                    } else tab.label
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Active Profile Tab Body
        when (selectedTab) {
            ProfileTab.POSTS -> {
                if (myPosts.isEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WafaEmptyState(
                                icon = Icons.Rounded.Edit,
                                title = "No posts published yet",
                                subtitle = "Your posts, polls, reels, and pinned highlights will appear here."
                            )
                        }
                    }
                } else {
                    items(myPosts, key = { it.id }) { post ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
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
                                onEdit = { txt, pin -> viewModel.editPost(post.id, txt, pin) },
                                onDelete = { viewModel.deletePost(post) },
                                onReport = {}
                            )
                        }
                    }
                }
            }

            ProfileTab.SAVED -> {
                if (savedPosts.isEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WafaEmptyState(
                                icon = Icons.Rounded.Bookmark,
                                title = "No bookmarked posts",
                                subtitle = "Tap the bookmark icon on any post or reel to keep it in your personal library."
                            )
                        }
                    }
                } else {
                    items(savedPosts, key = { "saved_${it.id}" }) { post ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SocialPostCard(
                                post = post,
                                currentUserId = viewModel.currentUserId,
                                liked = post.id in likedIds,
                                bookmarked = true,
                                onLike = { viewModel.toggleLike(post) },
                                onComment = {
                                    commentingPost = post
                                    viewModel.openComments(post.id)
                                },
                                onRepost = { viewModel.repost(post) },
                                onBookmark = { viewModel.toggleBookmark(post.id) },
                                onVotePoll = { idx -> viewModel.votePoll(post, idx) },
                                onEdit = { txt, pin -> viewModel.editPost(post.id, txt, pin) },
                                onDelete = { viewModel.deletePost(post) },
                                onReport = {}
                            )
                        }
                    }
                }
            }

            ProfileTab.MEDIA -> {
                if (mediaPosts.isEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WafaEmptyState(
                                icon = Icons.Rounded.Image,
                                title = "Media Gallery is empty",
                                subtitle = "Photos and visual Reels you upload will showcase cleanly here."
                            )
                        }
                    }
                } else {
                    items(mediaPosts, key = { "media_${it.id}" }) { post ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WafaMediaImage(
                                mediaData = post.mediaData!!,
                                contentDescription = post.content.take(60),
                                aspectRatio = 16f / 10f
                            )
                        }
                    }
                }
            }

            ProfileTab.REQUESTS -> {
                if (pendingRequests.isEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WafaEmptyState(
                                icon = Icons.Rounded.Lock,
                                title = "No pending follow requests",
                                subtitle = "When your account is set to Private, follow requests appear here for your approval."
                            )
                        }
                    }
                } else {
                    items(pendingRequests, key = { it.id }) { req ->
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Member ${req.followerId.take(10)}", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.acceptFollowRequest(req) }) { Text("Accept") }
                                    OutlinedButton(onClick = { viewModel.declineFollowRequest(req) }) { Text("Decline") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && profile != null) {
        EditProfileDialog(
            profile = profile!!,
            isPrivate = account?.privateAccount == true,
            onDismiss = { showEditDialog = false },
            onSave = { name, handle, bio, loc, web, interests, avatar, cover, priv ->
                viewModel.updateProfile(name, handle, bio, loc, web, interests, avatar, cover, priv) {
                    showEditDialog = false
                }
            }
        )
    }

    if (commentingPost != null) {
        CommentSheetDialog(
            post = commentingPost!!,
            comments = activeComments,
            onDismiss = {
                commentingPost = null
                viewModel.openComments(null)
            },
            onSendComment = { txt, parent -> viewModel.addComment(commentingPost!!, txt, parent) }
        )
    }
}

@Composable
private fun ProfileStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HubTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: PublicProfile,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String?, List<String>, String?, String?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(profile.displayName) }
    var username by remember { mutableStateOf(profile.username) }
    var bio by remember { mutableStateOf(profile.bio) }
    var location by remember { mutableStateOf(profile.location.orEmpty()) }
    var website by remember { mutableStateOf(profile.website.orEmpty()) }
    var interestsRaw by remember { mutableStateOf(profile.interests.joinToString(", ")) }
    var avatarData by remember { mutableStateOf(profile.avatarData) }
    var coverData by remember { mutableStateOf(profile.coverData) }
    var privateAccount by remember { mutableStateOf(isPrivate) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                avatarData = ImageEncoder.uriToCompressedDataUri(context, uri, maxDimensionPx = 280, maxChars = 120_000)
            }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                coverData = ImageEncoder.uriToCompressedDataUri(context, uri, maxDimensionPx = 640, maxChars = 150_000)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Social Identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Avatar") }
                    OutlinedButton(
                        onClick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cover") }
                }
                OutlinedTextField(value = displayName, onValueChange = { if (it.length <= 80) displayName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { if (it.length <= 32) username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bio, onValueChange = { if (it.length <= 300) bio = it }, label = { Text("Bio") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { if (it.length <= 80) location = it }, label = { Text("Location (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = website, onValueChange = { if (it.length <= 200) website = it }, label = { Text("Website (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = interestsRaw, onValueChange = { interestsRaw = it }, label = { Text("Interests (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Private Account", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = privateAccount, onCheckedChange = { privateAccount = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val interests = interestsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
                    onSave(
                        displayName,
                        username,
                        bio,
                        location.takeIf { it.isNotBlank() },
                        website.takeIf { it.isNotBlank() },
                        interests,
                        avatarData,
                        coverData,
                        privateAccount
                    )
                },
                enabled = displayName.trim().isNotEmpty() && username.trim().length >= 2
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
