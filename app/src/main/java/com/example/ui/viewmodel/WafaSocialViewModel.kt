package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminAuditLog
import com.example.data.model.AiUsageLog
import com.example.data.model.BlockedUser
import com.example.data.model.Bookmark
import com.example.data.model.ChatMessage
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.FeedCategory
import com.example.data.model.FollowRelation
import com.example.data.model.LikedPost
import com.example.data.model.NotificationItem
import com.example.data.model.PostComment
import com.example.data.model.PublicProfile
import com.example.data.model.SafetyReport
import com.example.data.model.SocialEvent
import com.example.data.model.SocialPost
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.model.UserSession
import com.example.data.model.VerificationRequest
import com.example.data.repository.WafaCommunityMessagingRepository
import com.example.data.repository.WafaSocialRepository
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val VM_TAG = "WafaSocialVM"
private const val STOP_TIMEOUT_MS = 5000L
private const val BOOTSTRAPPED_ADMIN_EMAIL = "useable.me2@gmail.com"

data class CreatorAnalyticsSummary(
    val followers: Long,
    val totalPosts: Int,
    val totalViews: Long,
    val totalLikes: Long,
    val totalComments: Long,
    val totalReposts: Long,
    val engagementRatePercent: Double,
    val topPosts: List<SocialPost>
)

@OptIn(ExperimentalCoroutinesApi::class)
class WafaSocialViewModel(
    private val socialRepo: WafaSocialRepository,
    private val communityRepo: WafaCommunityMessagingRepository,
    val currentUserId: String
) : ViewModel() {

    private val currentEmail: String = Firebase.auth.currentUser?.email.orEmpty()
    private val isEmailVerified: Boolean = Firebase.auth.currentUser?.isEmailVerified == true
    val isAdminUser: Boolean = currentEmail.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) && isEmailVerified

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _selectedFeedCategory = MutableStateFlow(FeedCategory.FOR_YOU)
    val selectedFeedCategory: StateFlow<FeedCategory> = _selectedFeedCategory.asStateFlow()

    private val _composerDraftText = MutableStateFlow("")
    val composerDraftText: StateFlow<String> = _composerDraftText.asStateFlow()

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    val activeCommentPostId: StateFlow<String?> = _activeCommentPostId.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    // Helper: Two-tier Flow error handling preserving CancellationException
    private fun <T> Flow<T>.toSafeUiState(fallbackLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(VM_TAG, "Error observing $fallbackLabel", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $fallbackLabel"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private fun <T> Flow<T>.toSafeValue(initial: T, label: String): StateFlow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(VM_TAG, "Stream warning ($label)", firestoreError ?: error)
            emit(initial)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = initial
        )

    // --- Core Reactive StateFlows ---
    val userAccount: StateFlow<UserAccount?> =
        socialRepo.observeUserAccount(currentUserId).toSafeValue(null, "userAccount")

    val myProfile: StateFlow<PublicProfile?> =
        socialRepo.observeOwnProfile(currentUserId).toSafeValue(null, "ownProfile")

    val publicProfiles: StateFlow<List<PublicProfile>> =
        socialRepo.observePublicProfiles(currentUserId).toSafeValue(emptyList(), "publicProfiles")

    val feedPostsState: StateFlow<UiState<List<SocialPost>>> =
        socialRepo.observePublicPosts(currentUserId).toSafeUiState("social feed")

    val myPosts: StateFlow<List<SocialPost>> =
        socialRepo.observeMyPosts(currentUserId).toSafeValue(emptyList(), "myPosts")

    val stories: StateFlow<List<StoryItem>> =
        socialRepo.observeStories(currentUserId).toSafeValue(emptyList(), "stories")

    val following: StateFlow<List<FollowRelation>> =
        socialRepo.observeFollowing(currentUserId).toSafeValue(emptyList(), "following")

    val followers: StateFlow<List<FollowRelation>> =
        socialRepo.observeFollowers(currentUserId).toSafeValue(emptyList(), "followers")

    val bookmarks: StateFlow<List<Bookmark>> =
        socialRepo.observeBookmarks(currentUserId).toSafeValue(emptyList(), "bookmarks")

    val likedPosts: StateFlow<List<LikedPost>> =
        socialRepo.observeLikedPosts(currentUserId).toSafeValue(emptyList(), "likedPosts")

    val blockedUsers: StateFlow<List<BlockedUser>> =
        socialRepo.observeBlockedUsers(currentUserId).toSafeValue(emptyList(), "blockedUsers")

    val notifications: StateFlow<List<NotificationItem>> =
        socialRepo.observeNotifications(currentUserId).toSafeValue(emptyList(), "notifications")

    val sessions: StateFlow<List<UserSession>> =
        socialRepo.observeSessions(currentUserId).toSafeValue(emptyList(), "sessions")

    val aiUsageLogs: StateFlow<List<AiUsageLog>> =
        socialRepo.observeAiUsageLogs(currentUserId).toSafeValue(emptyList(), "aiUsageLogs")

    val conversations: StateFlow<UiState<List<Conversation>>> =
        communityRepo.observeConversations(currentUserId).toSafeUiState("conversations")

    val publicCommunities: StateFlow<List<Community>> =
        communityRepo.observePublicCommunities(currentUserId).toSafeValue(emptyList(), "publicCommunities")

    val myCommunities: StateFlow<List<Community>> =
        communityRepo.observeMyCommunities(currentUserId).toSafeValue(emptyList(), "myCommunities")

    val events: StateFlow<List<SocialEvent>> =
        communityRepo.observePublicEvents(currentUserId).toSafeValue(emptyList(), "events")

    val myReports: StateFlow<List<SafetyReport>> =
        communityRepo.observeMyReports(currentUserId).toSafeValue(emptyList(), "myReports")

    // Admin streams (ONLY attached when isAdminUser == true)
    val adminReports: StateFlow<List<SafetyReport>> =
        if (isAdminUser) communityRepo.observeAllReportsForAdmin(currentUserId).toSafeValue(emptyList(), "adminReports")
        else MutableStateFlow(emptyList())

    val adminVerificationRequests: StateFlow<List<VerificationRequest>> =
        if (isAdminUser) communityRepo.observeAllVerificationRequestsForAdmin(currentUserId).toSafeValue(emptyList(), "adminVerifications")
        else MutableStateFlow(emptyList())

    val adminAuditLogs: StateFlow<List<AdminAuditLog>> =
        if (isAdminUser) communityRepo.observeAuditLogsForAdmin(currentUserId).toSafeValue(emptyList(), "adminAuditLogs")
        else MutableStateFlow(emptyList())

    // Active post comments stream
    val activePostComments: StateFlow<List<PostComment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId.isNullOrBlank()) flowOf(emptyList())
            else socialRepo.observePostComments(currentUserId, postId)
        }
        .toSafeValue(emptyList(), "postComments")

    // Active conversation messages stream
    val activeMessages: StateFlow<UiState<List<ChatMessage>>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else communityRepo.observeMessages(currentUserId, convId)
        }
        .toSafeUiState("messages")

    // Derived: Filtered & Ranked Feed respecting blocks and category
    val visibleFeedState: StateFlow<UiState<List<SocialPost>>> = combine(
        feedPostsState,
        selectedFeedCategory,
        following,
        blockedUsers
    ) { postsState, category, followingList, blockedList ->
        when (postsState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> postsState
            is UiState.Success -> {
                val blockedIds = blockedList.map { it.blockedUserId }.toSet()
                val activeFollowingIds = followingList
                    .filter { it.status == "active" }
                    .map { it.followingId }
                    .toSet()

                val unblocked = postsState.data.filter { it.authorId !in blockedIds }
                val filtered = when (category) {
                    FeedCategory.LATEST ->
                        unblocked.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    FeedCategory.FOLLOWING ->
                        unblocked
                            .filter { it.authorId in activeFollowingIds || it.authorId == currentUserId }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    FeedCategory.TRENDING ->
                        unblocked.sortedByDescending {
                            (it.likesCount * 3L) + (it.commentsCount * 4L) + (it.repostsCount * 5L) + it.viewsCount
                        }
                    FeedCategory.FOR_YOU ->
                        unblocked.sortedByDescending { post ->
                            val followBoost = if (post.authorId in activeFollowingIds) 25L else 0L
                            val engagement = (post.likesCount * 2L) + (post.commentsCount * 3L) + (post.repostsCount * 4L)
                            val ageHours = ((System.currentTimeMillis() - (post.createdAt?.toDate()?.time ?: 0L)) / 3_600_000L).coerceAtLeast(0L)
                            val recencyBoost = (48L - ageHours).coerceAtLeast(0L)
                            followBoost + engagement + recencyBoost
                        }
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val creatorAnalytics: StateFlow<CreatorAnalyticsSummary> = combine(myProfile, myPosts) { profile, posts ->
        val views = posts.sumOf { it.viewsCount }
        val likes = posts.sumOf { it.likesCount }
        val comments = posts.sumOf { it.commentsCount }
        val reposts = posts.sumOf { it.repostsCount }
        val interactions = likes + comments + reposts
        val rate = if (views > 0) (interactions.toDouble() / views.toDouble()) * 100.0
        else if (posts.isNotEmpty()) interactions.toDouble() / posts.size.toDouble()
        else 0.0
        CreatorAnalyticsSummary(
            followers = profile?.followersCount ?: 0L,
            totalPosts = posts.size,
            totalViews = views,
            totalLikes = likes,
            totalComments = comments,
            totalReposts = reposts,
            engagementRatePercent = rate,
            topPosts = posts.sortedByDescending { it.likesCount + it.commentsCount * 2 + it.viewsCount }.take(5)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        CreatorAnalyticsSummary(0, 0, 0, 0, 0, 0, 0.0, emptyList())
    )

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                socialRepo.ensureUserProvisioned(user).onFailure {
                    emitToast(it.message ?: "Account initialization warning")
                }
                socialRepo.cleanupExpiredOwnStories()
            }
        }
    }

    fun emitToast(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    fun selectFeedCategory(category: FeedCategory) {
        _selectedFeedCategory.value = category
    }

    fun updateComposerDraft(text: String) {
        _composerDraftText.value = text
    }

    fun openComments(postId: String?) {
        _activeCommentPostId.value = postId
    }

    fun openConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    // --- Actions ---

    fun publishPost(
        content: String,
        postType: String,
        mediaData: String?,
        mediaAspect: String?,
        pollOptions: List<String>,
        visibility: String,
        community: Community?,
        onSuccess: () -> Unit
    ) {
        val author = myProfile.value ?: PublicProfile(
            userId = currentUserId,
            displayName = Firebase.auth.currentUser?.displayName ?: "Wafa Member",
            username = "member_${currentUserId.take(5).lowercase()}"
        )
        viewModelScope.launch {
            socialRepo.createPost(
                author = author,
                content = content,
                postType = postType,
                mediaData = mediaData,
                mediaAspect = mediaAspect,
                pollOptions = pollOptions,
                visibility = visibility,
                communityId = community?.id,
                communityName = community?.name
            ).onSuccess {
                _composerDraftText.value = ""
                emitToast("Published to Wafa Social")
                onSuccess()
            }.onFailure {
                emitToast(it.message ?: "Failed to publish post")
            }
        }
    }

    fun editPost(postId: String, newContent: String, pinned: Boolean) {
        viewModelScope.launch {
            socialRepo.editPost(postId, newContent, pinned)
                .onSuccess { emitToast("Post updated") }
                .onFailure { emitToast(it.message ?: "Could not update post") }
        }
    }

    fun deletePost(post: SocialPost) {
        viewModelScope.launch {
            socialRepo.deletePost(post)
                .onSuccess { emitToast("Post deleted") }
                .onFailure { emitToast(it.message ?: "Could not delete post") }
        }
    }

    fun toggleLike(post: SocialPost) {
        val currentlyLiked = likedPosts.value.any { it.postId == post.id }
        val actorName = myProfile.value?.displayName ?: "Member"
        viewModelScope.launch {
            socialRepo.toggleLike(post, actorName, currentlyLiked)
                .onFailure { emitToast(it.message ?: "Could not update like") }
        }
    }

    fun toggleBookmark(postId: String) {
        val currentlySaved = bookmarks.value.any { it.postId == postId }
        viewModelScope.launch {
            socialRepo.toggleBookmark(postId, currentlySaved)
                .onSuccess { emitToast(if (currentlySaved) "Removed from Saved" else "Saved to bookmarks") }
                .onFailure { emitToast(it.message ?: "Could not update bookmark") }
        }
    }

    fun repost(post: SocialPost) {
        val actor = myProfile.value ?: return
        viewModelScope.launch {
            socialRepo.repost(post, actor)
                .onSuccess { emitToast("Reposted to your timeline") }
                .onFailure { emitToast(it.message ?: "Repost failed") }
        }
    }

    fun votePoll(post: SocialPost, optionIndex: Int) {
        viewModelScope.launch {
            socialRepo.votePoll(post, optionIndex)
                .onSuccess { emitToast("Vote recorded") }
                .onFailure { emitToast(it.message ?: "Could not vote") }
        }
    }

    fun recordPostView(postId: String) {
        viewModelScope.launch {
            socialRepo.incrementPostView(postId)
        }
    }

    fun addComment(post: SocialPost, content: String, parentCommentId: String? = null) {
        val actor = myProfile.value ?: return
        viewModelScope.launch {
            socialRepo.addComment(post, actor, content, parentCommentId)
                .onFailure { emitToast(it.message ?: "Could not post comment") }
        }
    }

    fun createStory(
        caption: String,
        overlayText: String?,
        stickerTag: String?,
        mediaData: String?,
        accentHex: String,
        highlighted: Boolean,
        onSuccess: () -> Unit
    ) {
        val author = myProfile.value ?: return
        viewModelScope.launch {
            socialRepo.createStory(author, caption, overlayText, stickerTag, mediaData, accentHex, highlighted)
                .onSuccess {
                    emitToast(if (highlighted) "Story added to Highlights" else "24h Story published")
                    onSuccess()
                }
                .onFailure { emitToast(it.message ?: "Could not publish story") }
        }
    }

    fun reactToStory(storyId: String) {
        viewModelScope.launch {
            socialRepo.reactToStory(storyId)
                .onSuccess { emitToast("Reaction sent") }
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch { socialRepo.markStoryViewed(storyId) }
    }

    fun toggleStoryHighlight(storyId: String, highlighted: Boolean) {
        viewModelScope.launch {
            socialRepo.toggleStoryHighlight(storyId, highlighted)
                .onSuccess { emitToast(if (highlighted) "Pinned to Highlights" else "Removed from Highlights") }
        }
    }

    fun toggleFollow(target: PublicProfile) {
        val me = myProfile.value ?: return
        val existing = following.value.firstOrNull { it.followingId == target.userId }
        viewModelScope.launch {
            socialRepo.toggleFollow(me, target, existing)
                .onSuccess {
                    val msg = when {
                        existing != null -> "Unfollowed @${target.username}"
                        target.visibility == "private" -> "Follow request sent to @${target.username}"
                        else -> "Following @${target.username}"
                    }
                    emitToast(msg)
                }
                .onFailure { emitToast(it.message ?: "Follow action failed") }
        }
    }

    fun acceptFollowRequest(relation: FollowRelation) {
        viewModelScope.launch {
            socialRepo.acceptFollowRequest(relation)
                .onSuccess { emitToast("Follow request accepted") }
                .onFailure { emitToast(it.message ?: "Could not accept request") }
        }
    }

    fun declineFollowRequest(relation: FollowRelation) {
        viewModelScope.launch {
            socialRepo.declineFollowRequest(relation)
                .onSuccess { emitToast("Follow request declined") }
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        location: String?,
        website: String?,
        interests: List<String>,
        avatarData: String?,
        coverData: String?,
        privateAccount: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            socialRepo.updateProfile(
                displayName, username, bio, location, website,
                interests, avatarData, coverData, privateAccount
            ).onSuccess {
                emitToast("Profile saved")
                onSuccess()
            }.onFailure { emitToast(it.message ?: "Profile update failed") }
        }
    }

    fun updatePrivacySettings(
        privateAccount: Boolean,
        allowMessagesFrom: String,
        birthYear: Int?,
        teenSafetyMode: Boolean,
        notifyLikes: Boolean,
        notifyComments: Boolean,
        notifyFollows: Boolean,
        notifyMessages: Boolean
    ) {
        viewModelScope.launch {
            socialRepo.updatePrivacySettings(
                privateAccount, allowMessagesFrom, birthYear, teenSafetyMode,
                notifyLikes, notifyComments, notifyFollows, notifyMessages
            ).onSuccess { emitToast("Privacy & safety settings saved") }
                .onFailure { emitToast(it.message ?: "Could not save settings") }
        }
    }

    fun setDeactivated(deactivated: Boolean) {
        viewModelScope.launch {
            socialRepo.setDeactivated(deactivated)
                .onSuccess { emitToast(if (deactivated) "Account hidden & deactivated" else "Account reactivated") }
                .onFailure { emitToast(it.message ?: "Could not update status") }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            socialRepo.revokeSession(sessionId)
                .onSuccess { emitToast("Session removed") }
        }
    }

    fun blockUser(target: PublicProfile) {
        viewModelScope.launch {
            socialRepo.blockUser(target)
                .onSuccess { emitToast("Blocked @${target.username}") }
                .onFailure { emitToast(it.message ?: "Could not block user") }
        }
    }

    fun unblockUser(blockedUserId: String) {
        viewModelScope.launch {
            socialRepo.unblockUser(blockedUserId)
                .onSuccess { emitToast("Account unblocked") }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { socialRepo.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        val unreadIds = notifications.value.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            socialRepo.markAllNotificationsRead(unreadIds)
                .onSuccess { emitToast("All notifications marked read") }
        }
    }

    // --- Messaging Actions ---

    fun startConversation(
        others: List<PublicProfile>,
        groupTitle: String?,
        onCreated: (String) -> Unit
    ) {
        val me = myProfile.value ?: return
        viewModelScope.launch {
            communityRepo.startConversation(me, others, groupTitle)
                .onSuccess { id ->
                    _activeConversationId.value = id
                    onCreated(id)
                }
                .onFailure { emitToast(it.message ?: "Could not start conversation") }
        }
    }

    fun sendMessage(
        conversation: Conversation,
        text: String,
        attachmentData: String?,
        replyToText: String?
    ) {
        val name = myProfile.value?.displayName ?: "Member"
        viewModelScope.launch {
            communityRepo.sendMessage(conversation, name, text, attachmentData, replyToText)
                .onFailure { emitToast(it.message ?: "Could not send message") }
        }
    }

    fun setTyping(conversation: Conversation, typing: Boolean) {
        viewModelScope.launch { communityRepo.setTyping(conversation, typing) }
    }

    fun reactToMessage(conversationId: String, message: ChatMessage, reactionCode: String) {
        viewModelScope.launch { communityRepo.reactToMessage(conversationId, message, reactionCode) }
    }

    fun editOrDeleteMessage(conversationId: String, messageId: String, newText: String, softDelete: Boolean) {
        viewModelScope.launch {
            communityRepo.editOrSoftDeleteMessage(conversationId, messageId, newText, softDelete)
                .onSuccess { emitToast(if (softDelete) "Message deleted" else "Message edited") }
        }
    }

    fun markMessageRead(conversationId: String, message: ChatMessage) {
        viewModelScope.launch { communityRepo.markMessageRead(conversationId, message) }
    }

    // --- Community & Event Actions ---

    fun createCommunity(
        name: String,
        description: String,
        category: String,
        visibility: String,
        rulesText: String,
        announcement: String,
        onSuccess: () -> Unit
    ) {
        val ownerName = myProfile.value?.displayName ?: "Member"
        viewModelScope.launch {
            communityRepo.createCommunity(ownerName, name, description, category, visibility, rulesText, announcement)
                .onSuccess {
                    emitToast("Community created")
                    onSuccess()
                }
                .onFailure { emitToast(it.message ?: "Could not create community") }
        }
    }

    fun toggleCommunityMembership(community: Community) {
        viewModelScope.launch {
            communityRepo.toggleCommunityMembership(community)
                .onSuccess {
                    val label = when {
                        community.visibility == "private" && currentUserId !in community.memberIds -> "Join request submitted"
                        currentUserId in community.memberIds -> "Left ${community.name}"
                        else -> "Joined ${community.name}"
                    }
                    emitToast(label)
                }
                .onFailure { emitToast(it.message ?: "Membership update failed") }
        }
    }

    fun updateCommunityAnnouncement(communityId: String, announcement: String) {
        viewModelScope.launch {
            communityRepo.updateCommunityAnnouncement(communityId, announcement)
                .onSuccess { emitToast("Announcement posted") }
        }
    }

    fun approveCommunityMember(community: Community, memberUid: String) {
        viewModelScope.launch {
            communityRepo.approveCommunityMember(community, memberUid)
                .onSuccess { emitToast("Member approved") }
        }
    }

    fun createEvent(
        title: String,
        description: String,
        eventType: String,
        locationOrLink: String,
        startTimeText: String,
        streamProviderNote: String?,
        onSuccess: () -> Unit
    ) {
        val hostName = myProfile.value?.displayName ?: "Host"
        viewModelScope.launch {
            communityRepo.createEvent(hostName, title, description, eventType, locationOrLink, startTimeText, streamProviderNote)
                .onSuccess {
                    emitToast(if (eventType == "LIVE_SCHEDULED") "Live session scheduled" else "Event created")
                    onSuccess()
                }
                .onFailure { emitToast(it.message ?: "Could not create event") }
        }
    }

    fun toggleEventRsvp(event: SocialEvent, going: Boolean) {
        viewModelScope.launch {
            communityRepo.toggleEventRsvp(event, going)
                .onSuccess { emitToast(if (going) "RSVP Going updated" else "Interested status updated") }
        }
    }

    // --- Safety, Verification, Data Export & Admin ---

    fun submitReport(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            communityRepo.submitReport(targetType, targetId, reason, details)
                .onSuccess { emitToast("Safety report submitted for review") }
                .onFailure { emitToast(it.message ?: "Report failed") }
        }
    }

    fun submitVerificationRequest(category: String, reason: String, onSuccess: () -> Unit) {
        val username = myProfile.value?.username ?: "member"
        viewModelScope.launch {
            communityRepo.submitVerificationRequest(username, category, reason)
                .onSuccess {
                    emitToast("Verification application submitted")
                    onSuccess()
                }
                .onFailure { emitToast(it.message ?: "Application failed") }
        }
    }

    fun resolveReportAsAdmin(report: SafetyReport, status: String, note: String) {
        viewModelScope.launch {
            communityRepo.resolveReportAsAdmin(report, status, note)
                .onSuccess { emitToast("Report marked $status & logged") }
                .onFailure { emitToast(it.message ?: "Admin action failed") }
        }
    }

    fun reviewVerificationAsAdmin(request: VerificationRequest, approve: Boolean) {
        viewModelScope.launch {
            communityRepo.reviewVerificationAsAdmin(request, approve)
                .onSuccess { emitToast(if (approve) "User verified & logged" else "Request rejected & logged") }
                .onFailure { emitToast(it.message ?: "Admin action failed") }
        }
    }

    fun buildAccountDataExportJson(): String {
        val profile = myProfile.value
        val account = userAccount.value
        val json = JSONObject()
        json.put("platform", "Wafa Social")
        json.put("exportedAtMillis", System.currentTimeMillis())
        json.put("userId", currentUserId)
        json.put("email", account?.email ?: currentEmail)
        json.put("displayName", profile?.displayName.orEmpty())
        json.put("username", profile?.username.orEmpty())
        json.put("bio", profile?.bio.orEmpty())
        json.put("privateAccount", account?.privateAccount ?: false)
        json.put("teenSafetyMode", account?.teenSafetyMode ?: false)
        json.put("postsCount", myPosts.value.size)
        json.put("bookmarksCount", bookmarks.value.size)
        json.put("activeSessionsCount", sessions.value.size)
        val postsArr = JSONArray()
        myPosts.value.take(25).forEach { post ->
            postsArr.put(
                JSONObject().apply {
                    put("id", post.id)
                    put("type", post.postType)
                    put("content", post.content)
                    put("likes", post.likesCount)
                }
            )
        }
        json.put("recentPosts", postsArr)
        return json.toString(2)
    }
}
