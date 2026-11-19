package com.example.data.repository

import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AdminAuditLog
import com.example.data.model.ChatMessage
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.PublicProfile
import com.example.data.model.SafetyReport
import com.example.data.model.SocialEvent
import com.example.data.model.VerificationRequest
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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

class WafaCommunityMessagingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logErrors(uid: String, path: String, op: OperationType): Flow<T> =
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

    // --- Real-Time Messaging (Conversations & Messages) ---

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, estimate)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "conversations", OperationType.LIST)

    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(conversationId).collection("messages")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, estimate)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "conversations/$conversationId/messages", OperationType.LIST)

    suspend fun startConversation(
        myProfile: PublicProfile,
        otherProfiles: List<PublicProfile>,
        groupTitle: String? = null
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val participants = (listOf(uid) + otherProfiles.map { it.userId }).distinct().take(20)
            require(participants.size >= 2) { "Select at least one other member." }
            val labels = (listOf(myProfile.displayName.ifBlank { "You" }) +
                otherProfiles.map { it.displayName.ifBlank { it.username } }).distinct().take(20)
            val isGroup = participants.size > 2 || !groupTitle.isNullOrBlank()
            val ref = db.collection("conversations").document()
            val payload = buildMap<String, Any> {
                groupTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(80)?.let { put("title", it) }
                put("groupChat", isGroup)
                put("participantIds", participants)
                put("participantLabels", labels)
                put("lastMessageText", "")
                put("lastMessageSenderId", "")
                put("typingUserIds", emptyList<String>())
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "conversations")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        attachmentData: String? = null,
        replyToText: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val cleanText = text.trim()
            require(cleanText.isNotEmpty() || !attachmentData.isNullOrBlank()) { "Message cannot be empty." }
            require(cleanText.length <= 2000) { "Message exceeds 2000 characters." }

            val convRef = db.collection("conversations").document(conversation.id)
            val msgRef = convRef.collection("messages").document()
            val msgPayload = buildMap<String, Any> {
                put("conversationId", conversation.id)
                put("senderId", uid)
                put("senderName", senderName.ifBlank { "Member" }.take(80))
                put("participantIds", conversation.participantIds.take(20))
                put("text", cleanText)
                attachmentData?.take(235000)?.let { put("attachmentData", it) }
                replyToText?.trim()?.takeIf { it.isNotEmpty() }?.take(200)?.let { put("replyToText", it) }
                put("edited", false)
                put("deleted", false)
                put("readBy", listOf(uid))
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val preview = cleanText.ifBlank { "Shared a photo" }.take(140)
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                convRef,
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageSenderId" to uid,
                    "typingUserIds" to conversation.typingUserIds.filterNot { it == uid },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "conversations/${conversation.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun setTyping(conversation: Conversation, isTyping: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val updated = if (isTyping) {
                (conversation.typingUserIds + uid).distinct().take(5)
            } else {
                conversation.typingUserIds.filterNot { it == uid }
            }
            if (updated == conversation.typingUserIds) return Result.success(Unit)
            db.collection("conversations").document(conversation.id)
                .update(mapOf("typingUserIds" to updated, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/${conversation.id}")
            Result.failure(e)
        }
    }

    suspend fun reactToMessage(conversationId: String, message: ChatMessage, reactionCode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val updatedReadBy = (message.readBy + uid).distinct().take(20)
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "reactionCode" to reactionCode.take(24),
                        "readBy" to updatedReadBy,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun editOrSoftDeleteMessage(
        conversationId: String,
        messageId: String,
        newText: String,
        softDelete: Boolean
    ): Result<Unit> {
        return try {
            requireUserId()
            val payload = if (softDelete) {
                mapOf(
                    "text" to "This message was deleted",
                    "deleted" to true,
                    "edited" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                require(newText.trim().length in 1..2000) { "Message must be 1–2000 characters." }
                mapOf(
                    "text" to newText.trim(),
                    "edited" to true,
                    "deleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun markMessageRead(conversationId: String, message: ChatMessage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (uid in message.readBy) return Result.success(Unit)
        return try {
            val updated = (message.readBy + uid).distinct().take(20)
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(mapOf("readBy" to updated, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages/${message.id}")
            Result.failure(e)
        }
    }

    // --- Communities & Groups ---

    fun observePublicCommunities(uid: String): Flow<List<Community>> =
        db.collection("communities")
            .whereEqualTo("visibility", "public")
            .limit(80)
            .snapshots()
            .map { snap ->
                snap.toObjects(Community::class.java, estimate)
                    .sortedByDescending { it.memberCount }
            }
            .logErrors(uid, "communities", OperationType.LIST)

    fun observeMyCommunities(uid: String): Flow<List<Community>> =
        db.collection("communities")
            .whereArrayContains("memberIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Community::class.java, estimate)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "communities?member=$uid", OperationType.LIST)

    suspend fun createCommunity(
        ownerName: String,
        name: String,
        description: String,
        category: String,
        visibility: String,
        rulesText: String,
        announcement: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(name.trim().length in 2..80) { "Community name must be 2–80 characters." }
            require(description.trim().length in 1..600) { "Description must be 1–600 characters." }
            val vis = if (visibility in listOf("public", "private", "invite_only")) visibility else "public"
            db.collection("communities").document().set(
                mapOf(
                    "name" to name.trim(),
                    "description" to description.trim(),
                    "category" to category.trim().ifBlank { "General" }.take(40),
                    "visibility" to vis,
                    "rulesText" to rulesText.trim().take(1000),
                    "announcement" to announcement.trim().take(500),
                    "ownerId" to uid,
                    "ownerName" to ownerName.ifBlank { "Member" }.take(80),
                    "memberIds" to listOf(uid),
                    "moderatorIds" to emptyList<String>(),
                    "pendingMemberIds" to emptyList<String>(),
                    "memberCount" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "communities")
            Result.failure(e)
        }
    }

    suspend fun toggleCommunityMembership(community: Community): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val ref = db.collection("communities").document(community.id)
            if (community.visibility == "private" && uid !in community.memberIds) {
                val pending = (community.pendingMemberIds + uid).distinct().take(30)
                ref.update(mapOf("pendingMemberIds" to pending, "updatedAt" to FieldValue.serverTimestamp())).await()
            } else {
                val isMember = uid in community.memberIds
                if (isMember && community.ownerId == uid) {
                    return Result.failure(IllegalStateException("Community owners cannot leave without transferring ownership."))
                }
                val newMembers = if (isMember) {
                    community.memberIds.filterNot { it == uid }
                } else {
                    (community.memberIds + uid).distinct().take(50)
                }
                ref.update(
                    mapOf(
                        "memberIds" to newMembers,
                        "memberCount" to newMembers.size.coerceAtLeast(1).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "communities/${community.id}")
            Result.failure(e)
        }
    }

    suspend fun updateCommunityAnnouncement(communityId: String, announcement: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("communities").document(communityId).update(
                mapOf(
                    "announcement" to announcement.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "communities/$communityId")
            Result.failure(e)
        }
    }

    suspend fun approveCommunityMember(community: Community, memberUid: String): Result<Unit> {
        return try {
            requireUserId()
            val newMembers = (community.memberIds + memberUid).distinct().take(50)
            val newPending = community.pendingMemberIds.filterNot { it == memberUid }
            db.collection("communities").document(community.id).update(
                mapOf(
                    "memberIds" to newMembers,
                    "pendingMemberIds" to newPending,
                    "memberCount" to newMembers.size.coerceAtLeast(1).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "communities/${community.id}")
            Result.failure(e)
        }
    }

    // --- Events & Scheduled Live Sessions ---

    fun observePublicEvents(uid: String): Flow<List<SocialEvent>> =
        db.collection("events")
            .whereEqualTo("visibility", "public")
            .limit(80)
            .snapshots()
            .map { snap ->
                snap.toObjects(SocialEvent::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "events", OperationType.LIST)

    suspend fun createEvent(
        hostName: String,
        title: String,
        description: String,
        eventType: String,
        locationOrLink: String,
        startTimeText: String,
        streamProviderNote: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(title.trim().length in 2..120) { "Title must be 2–120 characters." }
            require(description.trim().length in 1..1000) { "Description must be 1–1000 characters." }
            require(locationOrLink.trim().length in 1..200) { "Provide a location or event link." }
            require(startTimeText.trim().length in 1..80) { "Provide a date and time." }
            val payload = buildMap<String, Any> {
                put("hostId", uid)
                put("hostName", hostName.ifBlank { "Host" }.take(80))
                put("title", title.trim())
                put("description", description.trim())
                put("eventType", if (eventType == "LIVE_SCHEDULED") "LIVE_SCHEDULED" else "EVENT")
                put("locationOrLink", locationOrLink.trim())
                put("startTimeText", startTimeText.trim())
                put("visibility", "public")
                put("rsvpGoingIds", listOf(uid))
                put("rsvpInterestedIds", emptyList<String>())
                streamProviderNote?.trim()?.takeIf { it.isNotEmpty() }?.take(200)?.let { put("streamProviderNote", it) }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("events").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "events")
            Result.failure(e)
        }
    }

    suspend fun toggleEventRsvp(event: SocialEvent, going: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val newGoing = if (going) {
                if (uid in event.rsvpGoingIds) event.rsvpGoingIds.filterNot { it == uid }
                else (event.rsvpGoingIds + uid).distinct().take(50)
            } else {
                event.rsvpGoingIds.filterNot { it == uid }
            }
            val newInterested = if (!going) {
                if (uid in event.rsvpInterestedIds) event.rsvpInterestedIds.filterNot { it == uid }
                else (event.rsvpInterestedIds + uid).distinct().take(50)
            } else {
                event.rsvpInterestedIds.filterNot { it == uid }
            }
            db.collection("events").document(event.id).update(
                mapOf(
                    "rsvpGoingIds" to newGoing,
                    "rsvpInterestedIds" to newInterested,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "events/${event.id}")
            Result.failure(e)
        }
    }

    // --- Safety Reports, Verification Requests & Admin Moderation ---

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(reason.trim().length in 2..80) { "Select a report reason." }
            db.collection("reports").document().set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "reason" to reason.trim(),
                    "details" to details.trim().take(800),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports")
            Result.failure(e)
        }
    }

    suspend fun submitVerificationRequest(
        username: String,
        category: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(category.trim().length in 2..60) { "Provide a creator category." }
            require(reason.trim().length in 5..500) { "Explain why your account should be verified (5+ chars)." }
            db.collection("verification_requests").document().set(
                mapOf(
                    "userId" to uid,
                    "username" to username.take(32),
                    "category" to category.trim(),
                    "reason" to reason.trim(),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "verification_requests")
            Result.failure(e)
        }
    }

    fun observeMyReports(uid: String): Flow<List<SafetyReport>> =
        db.collection("reports")
            .whereEqualTo("reporterId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(SafetyReport::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "reports?reporter=$uid", OperationType.LIST)

    fun observeAllReportsForAdmin(uid: String): Flow<List<SafetyReport>> =
        db.collection("reports")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(SafetyReport::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "reports", OperationType.LIST)

    fun observeAllVerificationRequestsForAdmin(uid: String): Flow<List<VerificationRequest>> =
        db.collection("verification_requests")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(VerificationRequest::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "verification_requests", OperationType.LIST)

    fun observeAuditLogsForAdmin(uid: String): Flow<List<AdminAuditLog>> =
        db.collection("admin_audit_logs")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(AdminAuditLog::class.java, estimate)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logErrors(uid, "admin_audit_logs", OperationType.LIST)

    suspend fun resolveReportAsAdmin(report: SafetyReport, status: String, note: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("reports").document(report.id),
                mapOf(
                    "status" to status,
                    "resolutionNote" to note.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("admin_audit_logs").document(),
                mapOf(
                    "adminId" to user.uid,
                    "adminEmail" to (user.email ?: "admin@wafasocial.app"),
                    "action" to "REPORT_${status.uppercase()}",
                    "targetPath" to "reports/${report.id}",
                    "summary" to "Marked ${report.targetType} report as $status: ${note.trim().take(200)}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/${report.id}")
            Result.failure(e)
        }
    }

    suspend fun reviewVerificationAsAdmin(request: VerificationRequest, approve: Boolean): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val newStatus = if (approve) "approved" else "rejected"
            val batch = db.batch()
            batch.update(
                db.collection("verification_requests").document(request.id),
                mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp())
            )
            if (approve) {
                batch.update(
                    db.collection("public_profiles").document(request.userId),
                    mapOf("verified" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.set(
                db.collection("admin_audit_logs").document(),
                mapOf(
                    "adminId" to user.uid,
                    "adminEmail" to (user.email ?: "admin@wafasocial.app"),
                    "action" to if (approve) "APPROVE_VERIFICATION" else "REJECT_VERIFICATION",
                    "targetPath" to "public_profiles/${request.userId}",
                    "summary" to "${if (approve) "Verified" else "Rejected"} @${request.username} (${request.category})",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "verification_requests/${request.id}")
            Result.failure(e)
        }
    }
}
