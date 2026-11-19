package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class FeedCategory(val label: String, val explanation: String) {
    FOR_YOU("For You", "Ranked by recency, interactions, and accounts you follow"),
    FOLLOWING("Following", "Posts from accounts you follow and your own updates"),
    LATEST("Latest", "Pure reverse-chronological timeline"),
    TRENDING("Trending", "Ranked by total likes, comments, reposts, and views")
}

enum class PostType(val wireValue: String, val label: String) {
    TEXT("TEXT", "Post"),
    PHOTO("PHOTO", "Photo"),
    REEL("REEL", "Reel"),
    POLL("POLL", "Poll"),
    QUESTION("QUESTION", "Question");

    companion object {
        fun fromWire(raw: String?): PostType =
            entries.firstOrNull { it.wireValue == raw } ?: TEXT
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val birthYear: Int? = null,
    val privateAccount: Boolean = false,
    val allowMessagesFrom: String = "everyone",
    val teenSafetyMode: Boolean = false,
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true,
    val notifyFollows: Boolean = true,
    val notifyMessages: Boolean = true,
    val deactivated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSession(
    @DocumentId val id: String = "",
    val deviceName: String = "",
    val platform: String = "",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Bookmark(
    @DocumentId val id: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LikedPost(
    @DocumentId val id: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUserId: String = "",
    val blockedName: String = "",
    val blockedHandle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val type: String = "like",
    val message: String = "",
    val targetId: String? = null,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiUsageLog(
    @DocumentId val id: String = "",
    val taskType: String = "caption",
    val modelName: String = "gemini-3.8-flash",
    val promptChars: Long = 0L,
    val responseChars: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val location: String? = null,
    val website: String? = null,
    val interests: List<String> = emptyList(),
    val avatarData: String? = null,
    val coverData: String? = null,
    val visibility: String = "public",
    val verified: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowRelation(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followingId: String = "",
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SocialPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatar: String? = null,
    val authorVerified: Boolean = false,
    val content: String = "",
    val postType: String = "TEXT",
    val mediaData: String? = null,
    val mediaAspect: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollVoteCounts: List<Long> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val visibility: String = "public",
    val communityId: String? = null,
    val communityName: String? = null,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val repostsCount: Long = 0L,
    val viewsCount: Long = 0L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatar: String? = null,
    val content: String = "",
    val parentCommentId: String? = null,
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatar: String? = null,
    val caption: String = "",
    val overlayText: String? = null,
    val stickerTag: String? = null,
    val mediaData: String? = null,
    val accentHex: String = "#0F766E",
    val visibility: String = "public",
    val highlighted: Boolean = false,
    val viewsCount: Long = 0L,
    val reactionsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String? = null,
    val groupChat: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val participantLabels: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val typingUserIds: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    val attachmentData: String? = null,
    val replyToText: String? = null,
    val reactionCode: String? = null,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "General",
    val visibility: String = "public",
    val rulesText: String = "",
    val announcement: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberIds: List<String> = emptyList(),
    val moderatorIds: List<String> = emptyList(),
    val pendingMemberIds: List<String> = emptyList(),
    val memberCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SocialEvent(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val description: String = "",
    val eventType: String = "EVENT",
    val locationOrLink: String = "",
    val startTimeText: String = "",
    val visibility: String = "public",
    val rsvpGoingIds: List<String> = emptyList(),
    val rsvpInterestedIds: List<String> = emptyList(),
    val streamProviderNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SafetyReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "post",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val category: String = "",
    val reason: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetPath: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class AiDraftSuggestion(
    val polishedText: String,
    val caption: String,
    val hashtags: List<String>,
    val tips: String
)
