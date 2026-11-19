package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiUsageLog
import com.example.data.model.BlockedUser
import com.example.data.model.Bookmark
import com.example.data.model.FollowRelation
import com.example.data.model.LikedPost
import com.example.data.model.NotificationItem
import com.example.data.model.PostComment
import com.example.data.model.PublicProfile
import com.example.data.model.SocialPost
import com.example.data.model.StoryItem
import com.example.data.model.UserAccount
import com.example.data.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class WafaSocialRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logFirestoreErrors(uid: String, path: String, op: OperationType): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    // --- Bootstrap user private account, public profile, and current device session ---
    suspend fun ensureUserProvisioned(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        return try {
            val email = (user.email ?: "$uid@wafasocial.app").take(254)
            val displayName = (user.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore('@')).take(80)
            val sanitizedHandle = displayName
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .ifBlank { "wafa_${uid.take(6).lowercase()}" }
                .take(24)

            val accountRef = db.collection("users").document(uid)
            val accountSnap = accountRef.get().await()
            if (!accountSnap.exists()) {
                val accountMap = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "privateAccount" to false,
                    "allowMessagesFrom" to "everyone",
                    "teenSafetyMode" to false,
                    "notifyLikes" to true,
                    "notifyComments" to true,
                    "notifyFollows" to true,
                    "notifyMessages" to true,
                    "deactivated" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                accountRef.set(accountMap).await()
            }

            val profileRef = db.collection("public_profiles").document(uid)
            val profileSnap = profileRef.get().await()
            if (!profileSnap.exists()) {
                val profileMap = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", displayName)
                    put("username", sanitizedHandle)
                    put("bio", "Exploring Wafa Social — One Identity. One Community.")
                    put("interests", listOf("Community", "Creators", "Technology"))
                    user.photoUrl?.toString()?.take(150000)?.let { put("avatarData", it) }
                    put("visibility", "public")
                    put("verified", false)
                    put("followersCount", 0L)
                    put("followingCount", 0L)
                    put("postsCount", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                profileRef.set(profileMap).await()
            }

            // Record active device session
            val sessionId = "android_${Build.MODEL.replace(Regex("[^a-zA-Z0-9_]"), "_").take(32)}"
            val sessionRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
            val sessionSnap = sessionRef.get().await()
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120).ifBlank { "Android Device" }
            val platformLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})".take(60)
            if (!sessionSnap.exists()) {
                sessionRef.set(
                    mapOf(
                        "deviceName" to deviceLabel,
                        "platform" to platformLabel,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                sessionRef.update(
                    mapOf(
                        "deviceName" to deviceLabel,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    // --- Observers (Single-field queries -> ZERO composite indexes required!) ---

    fun observeUserAccount(uid: String): Flow<UserAccount?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(UserAccount::class.java, estimate) }
            .logFirestoreErrors(uid, "users/$uid", OperationType.GET)

    fun observeOwnProfile(uid: String): Flow<PublicProfile?> =
        db.collection("public_profiles").document(uid)
            .snapshots()
            .map { it.toObject(PublicProfile::class.java, estimate) }
            .logFirestoreErrors(uid, "public_profiles/$uid", OperationType.GET)

    fun observePublicProfiles(uid: String): Flow<List<PublicProfile>> =
        db.collection("public_profiles")
            .whereEqualTo("visibility", "public")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(PublicProfile::class.java, estimate)
                    .sortedByDescending { it.followersCount }
            }
            .logFirestoreErrors(uid, "public_profiles", OperationType.LIST)

    fun observePublicPosts(uid: String): Flow<List<SocialPost>> =
        db.collection("posts")
            .whereEqualTo("visibility", "public")
            .limit(120)
            .snapshots()
            .map { snap ->
                snap.toObjects(SocialPost::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "posts", OperationType.LIST)

    fun observeMyPosts(uid: String): Flow<List<SocialPost>> =
        db.collection("posts")
            .whereEqualTo("authorId", uid)
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(SocialPost::class.java, estimate)
                    .sortedWith(
                        compareByDescending<SocialPost> { it.pinned }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
            }
            .logFirestoreErrors(uid, "posts?authorId=$uid", OperationType.LIST)

    fun observePostComments(uid: String, postId: String): Flow<List<PostComment>> =
        db.collection("posts").document(postId).collection("comments")
            .whereEqualTo("visibility", "public")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(PostComment::class.java, estimate)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "posts/$postId/comments", OperationType.LIST)

    fun observeStories(uid: String): Flow<List<StoryItem>> =
        db.collection("stories")
            .whereEqualTo("visibility", "public")
            .limit(80)
            .snapshots()
            .map { snap ->
                val now = Timestamp.now()
                snap.toObjects(StoryItem::class.java, estimate)
                    .filter { story ->
                        story.highlighted || (story.expiresAt != null && story.expiresAt > now)
                    }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "stories", OperationType.LIST)

    fun observeFollowing(uid: String): Flow<List<FollowRelation>> =
        db.collection("follows")
            .whereEqualTo("followerId", uid)
            .snapshots()
            .map { it.toObjects(FollowRelation::class.java, estimate) }
            .logFirestoreErrors(uid, "follows?followerId=$uid", OperationType.LIST)

    fun observeFollowers(uid: String): Flow<List<FollowRelation>> =
        db.collection("follows")
            .whereEqualTo("followingId", uid)
            .snapshots()
            .map { it.toObjects(FollowRelation::class.java, estimate) }
            .logFirestoreErrors(uid, "follows?followingId=$uid", OperationType.LIST)

    fun observeBookmarks(uid: String): Flow<List<Bookmark>> =
        db.collection("users").document(uid).collection("bookmarks")
            .snapshots()
            .map { it.toObjects(Bookmark::class.java, estimate) }
            .logFirestoreErrors(uid, "users/$uid/bookmarks", OperationType.LIST)

    fun observeLikedPosts(uid: String): Flow<List<LikedPost>> =
        db.collection("users").document(uid).collection("liked_posts")
            .snapshots()
            .map { it.toObjects(LikedPost::class.java, estimate) }
            .logFirestoreErrors(uid, "users/$uid/liked_posts", OperationType.LIST)

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> =
        db.collection("users").document(uid).collection("blocked")
            .snapshots()
            .map { it.toObjects(BlockedUser::class.java, estimate) }
            .logFirestoreErrors(uid, "users/$uid/blocked", OperationType.LIST)

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        db.collection("users").document(uid).collection("notifications")
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "users/$uid/notifications", OperationType.LIST)

    fun observeSessions(uid: String): Flow<List<UserSession>> =
        db.collection("users").document(uid).collection("sessions")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserSession::class.java, estimate)
                    .sortedByDescending { it.lastActiveAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "users/$uid/sessions", OperationType.LIST)

    fun observeAiUsageLogs(uid: String): Flow<List<AiUsageLog>> =
        db.collection("users").document(uid).collection("ai_usage")
            .snapshots()
            .map { snap ->
                snap.toObjects(AiUsageLog::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(uid, "users/$uid/ai_usage", OperationType.LIST)

    // --- Profile & Privacy Mutations ---

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        location: String?,
        website: String?,
        interests: List<String>,
        avatarData: String?,
        coverData: String?,
        privateAccount: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(displayName.trim().length in 1..80) { "Display name must be 1–80 characters." }
            val cleanHandle = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            require(cleanHandle.length in 2..32) { "Username must be 2–32 letters, numbers, or underscores." }
            require(bio.length <= 300) { "Bio must be under 300 characters." }

            val profileUpdates = buildMap<String, Any> {
                put("displayName", displayName.trim())
                put("username", cleanHandle)
                put("bio", bio.trim())
                put("interests", interests.map { it.trim().take(28) }.filter { it.isNotEmpty() }.take(8))
                put("visibility", if (privateAccount) "private" else "public")
                put("updatedAt", FieldValue.serverTimestamp())
                location?.trim()?.take(80)?.let { put("location", it) }
                website?.trim()?.take(200)?.let { put("website", it) }
                avatarData?.take(150000)?.let { put("avatarData", it) }
                coverData?.take(180000)?.let { put("coverData", it) }
            }

            val batch = db.batch()
            batch.update(db.collection("public_profiles").document(uid), profileUpdates)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "privateAccount" to privateAccount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        privateAccount: Boolean,
        allowMessagesFrom: String,
        birthYear: Int?,
        teenSafetyMode: Boolean,
        notifyLikes: Boolean,
        notifyComments: Boolean,
        notifyFollows: Boolean,
        notifyMessages: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val currentYear = 2026
            val enforcedTeenMode = teenSafetyMode || (birthYear != null && (currentYear - birthYear) < 18)
            val effectiveMessagePolicy = if (enforcedTeenMode && allowMessagesFrom == "everyone") "following" else allowMessagesFrom
            val accountUpdates = buildMap<String, Any> {
                put("privateAccount", privateAccount)
                put("allowMessagesFrom", effectiveMessagePolicy)
                put("teenSafetyMode", enforcedTeenMode)
                put("notifyLikes", notifyLikes)
                put("notifyComments", notifyComments)
                put("notifyFollows", notifyFollows)
                put("notifyMessages", notifyMessages)
                put("updatedAt", FieldValue.serverTimestamp())
                birthYear?.coerceIn(1900, currentYear)?.let { put("birthYear", it) }
            }
            val batch = db.batch()
            batch.update(db.collection("users").document(uid), accountUpdates)
            batch.update(
                db.collection("public_profiles").document(uid),
                mapOf(
                    "visibility" to if (privateAccount) "private" else "public",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun setDeactivated(deactivated: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf("deactivated" to deactivated, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("public_profiles").document(uid),
                mapOf(
                    "visibility" to if (deactivated) "private" else "public",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            db.collection("users").document(uid).collection("sessions").document(sessionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/sessions/$sessionId")
            Result.failure(e)
        }
    }

    // --- Follow System & Follow Requests ---

    suspend fun toggleFollow(
        myProfile: PublicProfile,
        targetProfile: PublicProfile,
        existingRelation: FollowRelation?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val targetUid = targetProfile.userId
        if (uid == targetUid) return Result.failure(IllegalArgumentException("Cannot follow yourself."))
        val followId = "${uid}_${targetUid}"
        return try {
            val followRef = db.collection("follows").document(followId)
            val myRef = db.collection("public_profiles").document(uid)
            val targetRef = db.collection("public_profiles").document(targetUid)
            val batch = db.batch()
            if (existingRelation == null) {
                val status = if (targetProfile.visibility == "private") "pending" else "active"
                batch.set(
                    followRef,
                    mapOf(
                        "followerId" to uid,
                        "followingId" to targetUid,
                        "status" to status,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                if (status == "active") {
                    batch.update(myRef, "followingCount", FieldValue.increment(1))
                    batch.update(targetRef, "followersCount", FieldValue.increment(1))
                }
                batch.commit().await()
                sendNotification(
                    targetUserId = targetUid,
                    actorName = myProfile.displayName.ifBlank { "Member" },
                    type = if (status == "pending") "follow_request" else "follow",
                    message = if (status == "pending") "requested to follow you" else "started following you",
                    targetId = uid
                )
            } else {
                batch.delete(followRef)
                if (existingRelation.status == "active") {
                    if (myProfile.followingCount > 0) batch.update(myRef, "followingCount", FieldValue.increment(-1))
                    if (targetProfile.followersCount > 0) batch.update(targetRef, "followersCount", FieldValue.increment(-1))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "follows/$followId")
            Result.failure(e)
        }
    }

    suspend fun acceptFollowRequest(relation: FollowRelation): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.update(db.collection("follows").document(relation.id), mapOf("status" to "active"))
            batch.update(db.collection("public_profiles").document(uid), "followersCount", FieldValue.increment(1))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "follows/${relation.id}")
            Result.failure(e)
        }
    }

    suspend fun declineFollowRequest(relation: FollowRelation): Result<Unit> {
        return try {
            requireUserId()
            db.collection("follows").document(relation.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "follows/${relation.id}")
            Result.failure(e)
        }
    }

    // --- Posts, Likes, Comments, Polls, Bookmarks, Reposts ---

    suspend fun createPost(
        author: PublicProfile,
        content: String,
        postType: String,
        mediaData: String?,
        mediaAspect: String?,
        pollOptions: List<String>,
        visibility: String,
        communityId: String?,
        communityName: String?
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val trimmed = content.trim()
            require(trimmed.length in 1..2000) { "Post content must be 1–2000 characters." }
            val cleanPollOptions = if (postType == "POLL") {
                pollOptions.map { it.trim().take(80) }.filter { it.isNotEmpty() }.take(4)
            } else {
                emptyList()
            }
            if (postType == "POLL") {
                require(cleanPollOptions.size >= 2) { "Polls require at least 2 options." }
            }
            val extractedHashtags = Regex("#([a-zA-Z0-9_]{2,32})")
                .findAll(trimmed)
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(10)
                .toList()

            val postRef = db.collection("posts").document()
            val payload = buildMap<String, Any> {
                put("authorId", uid)
                put("authorName", author.displayName.ifBlank { "Wafa Member" }.take(80))
                put("authorHandle", author.username.ifBlank { "member" }.take(32))
                author.avatarData?.take(150000)?.let { put("authorAvatar", it) }
                put("authorVerified", author.verified)
                put("content", trimmed)
                put("postType", postType)
                mediaData?.take(255000)?.let { put("mediaData", it) }
                mediaAspect?.takeIf { it in listOf("16:9", "4:3", "1:1", "9:16") }?.let { put("mediaAspect", it) }
                put("pollOptions", cleanPollOptions)
                put("pollVoteCounts", List(cleanPollOptions.size) { 0L })
                put("hashtags", extractedHashtags)
                put("visibility", if (visibility == "private") "private" else "public")
                communityId?.takeIf { it.isNotBlank() }?.let { put("communityId", it) }
                communityName?.takeIf { it.isNotBlank() }?.let { put("communityName", it.take(80)) }
                put("likesCount", 0L)
                put("commentsCount", 0L)
                put("repostsCount", 0L)
                put("viewsCount", 0L)
                put("pinned", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val batch = db.batch()
            batch.set(postRef, payload)
            batch.update(db.collection("public_profiles").document(uid), "postsCount", FieldValue.increment(1))
            batch.commit().await()
            Result.success(postRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts")
            Result.failure(e)
        }
    }

    suspend fun editPost(postId: String, newContent: String, pinned: Boolean): Result<Unit> {
        return try {
            requireUserId()
            val trimmed = newContent.trim()
            require(trimmed.length in 1..2000) { "Post content must be 1–2000 characters." }
            val hashtags = Regex("#([a-zA-Z0-9_]{2,32})")
                .findAll(trimmed)
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(10)
                .toList()
            db.collection("posts").document(postId).update(
                mapOf(
                    "content" to trimmed,
                    "hashtags" to hashtags,
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/$postId")
            Result.failure(e)
        }
    }

    suspend fun deletePost(post: SocialPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.delete(db.collection("posts").document(post.id))
            if (post.authorId == uid) {
                batch.update(db.collection("public_profiles").document(uid), "postsCount", FieldValue.increment(-1))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun toggleLike(post: SocialPost, actorName: String, currentlyLiked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val likeRef = db.collection("users").document(uid).collection("liked_posts").document(post.id)
            val postRef = db.collection("posts").document(post.id)
            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                if (post.likesCount > 0) batch.update(postRef, "likesCount", FieldValue.increment(-1))
            } else {
                batch.set(likeRef, mapOf("postId" to post.id, "createdAt" to FieldValue.serverTimestamp()))
                batch.update(postRef, "likesCount", FieldValue.increment(1))
            }
            batch.commit().await()
            if (!currentlyLiked && post.authorId != uid) {
                sendNotification(post.authorId, actorName, "like", "liked your post", post.id)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(postId: String, currentlyBookmarked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val ref = db.collection("users").document(uid).collection("bookmarks").document(postId)
            if (currentlyBookmarked) {
                ref.delete().await()
            } else {
                ref.set(mapOf("postId" to postId, "createdAt" to FieldValue.serverTimestamp())).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/bookmarks/$postId")
            Result.failure(e)
        }
    }

    suspend fun repost(post: SocialPost, actor: PublicProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val newRef = db.collection("posts").document()
            val quoteText = "Reposted from @${post.authorHandle}:\n\n${post.content}".take(2000)
            val payload = buildMap<String, Any> {
                put("authorId", uid)
                put("authorName", actor.displayName.ifBlank { "Wafa Member" }.take(80))
                put("authorHandle", actor.username.ifBlank { "member" }.take(32))
                actor.avatarData?.take(150000)?.let { put("authorAvatar", it) }
                put("authorVerified", actor.verified)
                put("content", quoteText)
                put("postType", if (!post.mediaData.isNullOrBlank()) "PHOTO" else "TEXT")
                post.mediaData?.take(255000)?.let { put("mediaData", it) }
                put("pollOptions", emptyList<String>())
                put("pollVoteCounts", emptyList<Long>())
                put("hashtags", post.hashtags.take(10))
                put("visibility", "public")
                put("likesCount", 0L)
                put("commentsCount", 0L)
                put("repostsCount", 0L)
                put("viewsCount", 0L)
                put("pinned", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val batch = db.batch()
            batch.set(newRef, payload)
            batch.update(db.collection("posts").document(post.id), "repostsCount", FieldValue.increment(1))
            batch.update(db.collection("public_profiles").document(uid), "postsCount", FieldValue.increment(1))
            batch.commit().await()
            if (post.authorId != uid) {
                sendNotification(post.authorId, actor.displayName.ifBlank { "Member" }, "repost", "reposted your post", post.id)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun votePoll(post: SocialPost, optionIndex: Int): Result<Unit> {
        return try {
            requireUserId()
            require(post.postType == "POLL") { "Not a poll." }
            require(optionIndex in post.pollVoteCounts.indices) { "Invalid option index." }
            val updated = post.pollVoteCounts.toMutableList()
            updated[optionIndex] = updated[optionIndex] + 1L
            db.collection("posts").document(post.id)
                .update(mapOf("pollVoteCounts" to updated))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun incrementPostView(postId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("posts").document(postId)
                .update("viewsCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/$postId")
            Result.failure(e)
        }
    }

    suspend fun addComment(
        post: SocialPost,
        author: PublicProfile,
        content: String,
        parentCommentId: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val trimmed = content.trim()
            require(trimmed.length in 1..1000) { "Comment must be 1–1000 characters." }
            val commentRef = db.collection("posts").document(post.id).collection("comments").document()
            val payload = buildMap<String, Any> {
                put("postId", post.id)
                put("authorId", uid)
                put("authorName", author.displayName.ifBlank { "Member" }.take(80))
                put("authorHandle", author.username.ifBlank { "member" }.take(32))
                author.avatarData?.take(150000)?.let { put("authorAvatar", it) }
                put("content", trimmed)
                parentCommentId?.takeIf { it.isNotBlank() }?.let { put("parentCommentId", it) }
                put("visibility", "public")
                put("createdAt", FieldValue.serverTimestamp())
            }
            val batch = db.batch()
            batch.set(commentRef, payload)
            batch.update(db.collection("posts").document(post.id), "commentsCount", FieldValue.increment(1))
            batch.commit().await()
            if (post.authorId != uid) {
                sendNotification(post.authorId, author.displayName.ifBlank { "Member" }, "comment", "commented: ${trimmed.take(80)}", post.id)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts/${post.id}/comments")
            Result.failure(e)
        }
    }

    // --- Stories (24h Server-Checked Expiration + Highlights) ---

    suspend fun createStory(
        author: PublicProfile,
        caption: String,
        overlayText: String?,
        stickerTag: String?,
        mediaData: String?,
        accentHex: String,
        highlighted: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(caption.length <= 280) { "Caption must be 280 characters or fewer." }
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
            val ref = db.collection("stories").document()
            val payload = buildMap<String, Any> {
                put("authorId", uid)
                put("authorName", author.displayName.ifBlank { "Member" }.take(80))
                put("authorHandle", author.username.ifBlank { "member" }.take(32))
                author.avatarData?.take(150000)?.let { put("authorAvatar", it) }
                put("caption", caption.trim())
                overlayText?.trim()?.takeIf { it.isNotEmpty() }?.take(140)?.let { put("overlayText", it) }
                stickerTag?.trim()?.takeIf { it.isNotEmpty() }?.take(40)?.let { put("stickerTag", it) }
                mediaData?.take(255000)?.let { put("mediaData", it) }
                put("accentHex", accentHex.take(16))
                put("visibility", "public")
                put("highlighted", highlighted)
                put("viewsCount", 0L)
                put("reactionsCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("expiresAt", expiresAt)
            }
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories")
            Result.failure(e)
        }
    }

    suspend fun reactToStory(storyId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("stories").document(storyId)
                .update("reactionsCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(storyId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("stories").document(storyId)
                .update("viewsCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun toggleStoryHighlight(storyId: String, highlighted: Boolean): Result<Unit> {
        return try {
            requireUserId()
            db.collection("stories").document(storyId)
                .update(mapOf("highlighted" to highlighted))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun cleanupExpiredOwnStories(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val now = Timestamp.now()
            val snap = db.collection("stories")
                .whereEqualTo("authorId", uid)
                .get()
                .await()
            val expired = snap.toObjects(StoryItem::class.java, estimate)
                .filter { !it.highlighted && it.expiresAt != null && it.expiresAt <= now }
            for (story in expired) {
                db.collection("stories").document(story.id).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "stories")
            Result.failure(e)
        }
    }

    // --- Blocking & Notifications ---

    suspend fun blockUser(target: PublicProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (uid == target.userId) return Result.failure(IllegalArgumentException("Cannot block yourself."))
        return try {
            db.collection("users").document(uid).collection("blocked").document(target.userId)
                .set(
                    mapOf(
                        "blockedUserId" to target.userId,
                        "blockedName" to target.displayName.ifBlank { "Member" }.take(80),
                        "blockedHandle" to target.username.ifBlank { "user" }.take(40),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/blocked/${target.userId}")
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            db.collection("users").document(uid).collection("blocked").document(blockedUserId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/blocked/$blockedUserId")
            Result.failure(e)
        }
    }

    suspend fun sendNotification(
        targetUserId: String,
        actorName: String,
        type: String,
        message: String,
        targetId: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val payload = buildMap<String, Any> {
                put("actorId", uid)
                put("actorName", actorName.take(80))
                put("type", type)
                put("message", message.take(300))
                targetId?.take(128)?.let { put("targetId", it) }
                put("read", false)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(targetUserId).collection("notifications")
                .document()
                .set(payload)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$targetUserId/notifications")
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            db.collection("users").document(uid).collection("notifications")
                .document(notificationId)
                .update(mapOf("read" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications/$notificationId")
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(unreadIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            for (id in unreadIds.take(450)) {
                val ref = db.collection("users").document(uid).collection("notifications").document(id)
                batch.update(ref, mapOf("read" to true))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications")
            Result.failure(e)
        }
    }

    suspend fun logAiUsage(taskType: String, modelName: String, promptChars: Int, responseChars: Int) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("ai_usage").document().set(
                mapOf(
                    "taskType" to taskType,
                    "modelName" to modelName.take(80),
                    "promptChars" to promptChars.coerceIn(0, 50000),
                    "responseChars" to responseChars.coerceIn(0, 50000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/ai_usage")
        }
    }
}
