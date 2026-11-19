package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PublicProfile
import com.example.data.model.SocialPost
import com.example.data.model.UiState
import com.example.ui.components.CommentSheetDialog
import com.example.ui.components.ReportContentDialog
import com.example.ui.components.SocialPostCard
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.WafaAvatar
import com.example.ui.components.WafaEmptyState
import com.example.ui.components.WafaMediaImage
import com.example.ui.viewmodel.WafaSocialViewModel

private enum class ExploreTab(val label: String) {
    REELS("Reels"),
    PEOPLE("People"),
    POSTS("Posts"),
    HASHTAGS("Hashtags")
}

@Composable
fun ExploreReelsScreen(
    viewModel: WafaSocialViewModel,
    initialQuery: String = "",
    onOpenChat: (String) -> Unit,
    onNavigateCreate: () -> Unit
) {
    val postsState by viewModel.feedPostsState.collectAsStateWithLifecycle()
    val profiles by viewModel.publicProfiles.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val likedPosts by viewModel.likedPosts.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val activeComments by viewModel.activePostComments.collectAsStateWithLifecycle()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedTab by remember(initialQuery) {
        mutableStateOf(if (initialQuery.isNotBlank()) ExploreTab.POSTS else ExploreTab.REELS)
    }
    var commentingPost by remember { mutableStateOf<SocialPost?>(null) }
    var reportingPost by remember { mutableStateOf<SocialPost?>(null) }

    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedUserId }.toSet() }
    val likedIds = remember(likedPosts) { likedPosts.map { it.postId }.toSet() }
    val savedIds = remember(bookmarks) { bookmarks.map { it.postId }.toSet() }
    val followingMap = remember(following) { following.associateBy { it.followingId } }

    val cleanQuery = query.trim().lowercase().removePrefix("#")
    val unblockedPosts = remember(allPosts, blockedIds) { allPosts.filter { it.authorId !in blockedIds } }
    val reelPosts = remember(unblockedPosts, cleanQuery) {
        val candidates = unblockedPosts.filter { it.postType == "REEL" || !it.mediaData.isNullOrBlank() }
        if (cleanQuery.isEmpty()) candidates
        else candidates.filter {
            it.content.lowercase().contains(cleanQuery) ||
                it.hashtags.any { tag -> tag.contains(cleanQuery) } ||
                it.authorName.lowercase().contains(cleanQuery)
        }
    }
    val filteredPosts = remember(unblockedPosts, cleanQuery) {
        if (cleanQuery.isEmpty()) unblockedPosts
        else unblockedPosts.filter {
            it.content.lowercase().contains(cleanQuery) ||
                it.authorName.lowercase().contains(cleanQuery) ||
                it.authorHandle.lowercase().contains(cleanQuery) ||
                it.hashtags.any { tag -> tag.contains(cleanQuery) }
        }
    }
    val filteredPeople = remember(profiles, blockedIds, cleanQuery) {
        profiles
            .filter { it.userId != viewModel.currentUserId && it.userId !in blockedIds }
            .filter {
                cleanQuery.isEmpty() ||
                    it.displayName.lowercase().contains(cleanQuery) ||
                    it.username.lowercase().contains(cleanQuery) ||
                    it.bio.lowercase().contains(cleanQuery)
            }
    }
    val realHashtags = remember(unblockedPosts) {
        unblockedPosts
            .flatMap { it.hashtags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Unified Discovery Search Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search people, posts, reels, #hashtags...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore_search_input")
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExploreTab.entries) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("explore_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (selectedTab) {
            ExploreTab.REELS -> {
                if (reelPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        WafaEmptyState(
                            icon = Icons.Rounded.PlayCircle,
                            title = "Vertical Reels Feed",
                            subtitle = "Publish a Reel or visual showcase in Create to populate the swipeable full-screen Reels stream.",
                            actionLabel = "Create a Reel",
                            onAction = onNavigateCreate
                        )
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { reelPosts.size })
                    LaunchedEffect(pagerState.currentPage, reelPosts) {
                        reelPosts.getOrNull(pagerState.currentPage)?.let { post ->
                            viewModel.recordPostView(post.id)
                        }
                    }
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("reels_vertical_pager")
                    ) { page ->
                        val post = reelPosts[page]
                        ReelShowcaseCard(
                            post = post,
                            liked = post.id in likedIds,
                            saved = post.id in savedIds,
                            onLike = { viewModel.toggleLike(post) },
                            onComment = {
                                commentingPost = post
                                viewModel.openComments(post.id)
                            },
                            onBookmark = { viewModel.toggleBookmark(post.id) }
                        )
                    }
                }
            }

            ExploreTab.PEOPLE -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredPeople.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.PersonSearch,
                                title = "No matching creators yet",
                                subtitle = "As more people join Wafa Social with public profiles, they appear here for instant connection."
                            )
                        }
                    } else {
                        items(filteredPeople, key = { it.userId }) { person ->
                            PersonDirectoryCard(
                                profile = person,
                                followStatus = followingMap[person.userId]?.status,
                                onToggleFollow = { viewModel.toggleFollow(person) },
                                onMessage = {
                                    viewModel.startConversation(listOf(person), null) { convId ->
                                        onOpenChat(convId)
                                    }
                                },
                                onBlock = { viewModel.blockUser(person) }
                            )
                        }
                    }
                }
            }

            ExploreTab.POSTS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (filteredPosts.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.Search,
                                title = "No posts matched",
                                subtitle = "Try a different keyword or clear your search filter."
                            )
                        }
                    } else {
                        items(filteredPosts, key = { it.id }) { post ->
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
                                onReport = { reportingPost = post },
                                onHashtagClick = { tag -> query = "#$tag" }
                            )
                        }
                    }
                }
            }

            ExploreTab.HASHTAGS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (realHashtags.isEmpty()) {
                        item {
                            WafaEmptyState(
                                icon = Icons.Rounded.Tag,
                                title = "No live hashtags yet",
                                subtitle = "Hashtag metrics are computed exclusively from published posts — add #tags to a post to start a topic.",
                                actionLabel = "Write a Tagged Post",
                                onAction = onNavigateCreate
                            )
                        }
                    } else {
                        items(realHashtags, key = { it.key }) { (tag, count) ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = "#$tag"
                                        selectedTab = ExploreTab.POSTS
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Text("#$tag", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "$count ${if (count == 1) "post" else "posts"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
}

@Composable
private fun ReelShowcaseCard(
    post: SocialPost,
    liked: Boolean,
    saved: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF132234), Color(0xFF0A0F18))
                )
            )
    ) {
        if (!post.mediaData.isNullOrBlank()) {
            WafaMediaImage(
                mediaData = post.mediaData,
                contentDescription = "Reel visual by ${post.authorName}",
                aspectRatio = 3f / 4f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x33000000), Color.Transparent, Color(0xE60B0E14))
                        )
                    )
            )
        }

        // Top badge
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${post.viewsCount} views · Swipe up for next",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Right vertical interaction column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like reel",
                        tint = if (liked) Color(0xFFF87171) else Color.White
                    )
                }
                Text(post.likesCount.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onComment,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = "Comment on reel", tint = Color.White)
                }
                Text(post.commentsCount.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            IconButton(
                onClick = onBookmark,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save reel",
                    tint = if (saved) Color(0xFFF59E0B) else Color.White
                )
            }
        }

        // Bottom creator & caption overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 78.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WafaAvatar(name = post.authorName, avatarData = post.authorAvatar, size = 38.dp, ringActive = true)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        if (post.authorVerified) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge()
                        }
                    }
                    Text("@${post.authorHandle}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (post.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = post.hashtags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2DD4BF)
                )
            }
        }
    }
}

@Composable
private fun PersonDirectoryCard(
    profile: PublicProfile,
    followStatus: String?,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
    onBlock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WafaAvatar(name = profile.displayName, avatarData = profile.avatarData, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (profile.verified) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedBadge()
                        }
                    }
                    Text(
                        text = "@${profile.username} · ${profile.followersCount} followers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (followStatus == null) {
                    Button(
                        onClick = onToggleFollow,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Text(if (profile.visibility == "private") "Request" else "Follow")
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Text(if (followStatus == "pending") "Requested" else "Following")
                    }
                }
                FilledTonalButton(
                    onClick = onMessage,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Message ${profile.displayName}", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Message")
                }
                IconButton(onClick = onBlock, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Rounded.Block, contentDescription = "Block ${profile.displayName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
