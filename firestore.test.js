const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";
const ADMIN_UID = "admin_uid_999";
const ADMIN_EMAIL = "useable.me2@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (msAgo = 5000) => new Date(Date.now() - msAgo);
const futureDate = (msAhead = 60000) => new Date(Date.now() + msAhead);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("public_profiles").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("stories").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("communities").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("events").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("reports").where("reporterId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("admin_audit_logs").get());
});

test("Private user PII account & subcollections enforce strict owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      birthYear: 1998,
      privateAccount: false,
      allowMessagesFrom: "everyone",
      teenSafetyMode: false,
      notifyLikes: true,
      notifyComments: true,
      notifyFollows: true,
      notifyMessages: true,
      deactivated: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner read & partial update succeed
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      privateAccount: true,
      teenSafetyMode: true,
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read/update denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ privateAccount: false, updatedAt: pastDate(1000) }));

  // Owner subcollections: sessions, bookmarks, liked_posts, blocked, ai_usage
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      deviceName: "Pixel 9 Pro",
      platform: "Android 16",
      lastActiveAt: ts,
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("post_1").set({
      postId: "post_1",
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_usage").doc("ai_1").set({
      taskType: "caption",
      modelName: "gemini-3.8-flash",
      promptChars: 64,
      responseChars: 240,
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("ai_usage").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_usage").get());
});

test("Public profiles: anti-self-verification, discovery query alignment, and follow counter sync", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  // Reject self-verification on create
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_dev",
      bio: "Building communities",
      interests: ["Tech"],
      visibility: "public",
      verified: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject ghost field (shadow update attack)
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_dev",
      bio: "Building communities",
      interests: ["Tech"],
      visibility: "public",
      verified: false,
      isAdmin: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid Kotlin create payload succeeds
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Rahman",
      username: "alice_dev",
      bio: "Designing Wafa Social",
      location: "Dhaka",
      interests: ["Design", "Android"],
      visibility: "public",
      verified: false,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner cannot self-verify on update
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Owner valid edit succeeds
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      bio: "Principal Mobile Architect",
      updatedAt: pastDate(1000),
    })
  );

  // Filtered discovery query succeeds; unfiltered list fails
  await assertSucceeds(bobDb.collection("public_profiles").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("public_profiles").get());

  // Follower count +/-1 allowed for Bob, jump by +50 rejected
  await assertSucceeds(bobDb.collection("public_profiles").doc(ALICE_UID).update({ followersCount: 1 }));
  await assertFails(bobDb.collection("public_profiles").doc(ALICE_UID).update({ followersCount: 50 }));
});

test("Posts, comments, polls, likes, and stories enforce rules and query parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(8000);

  // Valid post creation (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_101").set({
      authorId: ALICE_UID,
      authorName: "Alice Rahman",
      authorHandle: "alice_dev",
      authorVerified: false,
      content: "Which stack powers your favorite mobile social app? #WafaSocial",
      postType: "POLL",
      pollOptions: ["Jetpack Compose", "Flutter"],
      pollVoteCounts: [0, 0],
      hashtags: ["wafasocial"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      repostsCount: 0,
      viewsCount: 0,
      pinned: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb.collection("posts").doc("post_future").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_dev",
      authorVerified: false,
      content: "Future post",
      postType: "TEXT",
      pollOptions: [],
      pollVoteCounts: [],
      hashtags: [],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      repostsCount: 0,
      viewsCount: 0,
      pinned: false,
      createdAt: futureDate(60000),
      updatedAt: futureDate(60000),
    })
  );

  // Filtered feed queries match Kotlin repositories
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());
  await assertSucceeds(aliceDb.collection("posts").where("authorId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("posts").get());

  // Bob can like (+1), vote on poll, and add a comment
  await assertSucceeds(bobDb.collection("posts").doc("post_101").update({ likesCount: 1 }));
  await assertSucceeds(bobDb.collection("posts").doc("post_101").update({ pollVoteCounts: [1, 0] }));
  await assertFails(bobDb.collection("posts").doc("post_101").update({ content: "Hacked by Bob", updatedAt: pastDate(1000) }));

  await assertSucceeds(
    bobDb.collection("posts").doc("post_101").collection("comments").doc("c_1").set({
      postId: "post_101",
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_builder",
      content: "Jetpack Compose all day!",
      visibility: "public",
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_101").collection("comments").where("visibility", "==", "public").get()
  );

  // Story creation with 24h expiration
  const storyCreated = pastDate(2000);
  const storyExpires = new Date(Date.now() + 24 * 60 * 60 * 1000);
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_dev",
      caption: "Behind the scenes of Wafa Social",
      overlayText: "Live from Studio",
      stickerTag: "BUILD",
      accentHex: "#0F766E",
      visibility: "public",
      highlighted: false,
      viewsCount: 0,
      reactionsCount: 0,
      createdAt: storyCreated,
      expiresAt: storyExpires,
    })
  );
  await assertSucceeds(bobDb.collection("stories").where("visibility", "==", "public").get());
  await assertSucceeds(bobDb.collection("stories").doc("story_1").update({ reactionsCount: 1 }));
});

test("Messaging conversations and messages isolate strictly to participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      groupChat: false,
      participantIds: [ALICE_UID, BOB_UID],
      participantLabels: ["Alice", "Bob"],
      lastMessageText: "",
      lastMessageSenderId: "",
      typingUserIds: [],
      createdBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
      conversationId: "conv_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      participantIds: [ALICE_UID, BOB_UID],
      text: "Hey Bob, welcome to Wafa Social!",
      edited: false,
      deleted: false,
      readBy: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can query conversation & messages; Charlie is strictly blocked
  await assertSucceeds(bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("conversations").doc("conv_1").collection("messages").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("conversations").doc("conv_1").get());
  await assertFails(
    charlieDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get()
  );
});

test("Communities, events, reports, verification requests, and anti-spoofing admin RBAC", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const ts = pastDate(5000);

  // Community create & Bob joins public community
  await assertSucceeds(
    aliceDb.collection("communities").doc("comm_1").set({
      name: "Android Builders",
      description: "Kotlin, Compose, and clean architecture discussions",
      category: "Technology",
      visibility: "public",
      rulesText: "Be respectful and share real code.",
      announcement: "Welcome to our founding week!",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      memberIds: [ALICE_UID],
      moderatorIds: [],
      pendingMemberIds: [],
      memberCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(bobDb.collection("communities").where("visibility", "==", "public").get());
  await assertSucceeds(
    bobDb.collection("communities").doc("comm_1").update({
      memberIds: [ALICE_UID, BOB_UID],
      memberCount: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Event create & RSVP
  await assertSucceeds(
    aliceDb.collection("events").doc("evt_1").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      title: "Wafa Social Town Hall",
      description: "Live community Q&A and roadmap review.",
      eventType: "LIVE_SCHEDULED",
      locationOrLink: "External RTMP stream pending configuration",
      startTimeText: "Friday, 19:00 UTC",
      visibility: "public",
      rsvpGoingIds: [ALICE_UID],
      rsvpInterestedIds: [],
      streamProviderNote: "Configure RTMP/WebRTC provider before going live",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(bobDb.collection("events").where("visibility", "==", "public").get());
  await assertSucceeds(
    bobDb.collection("events").doc("evt_1").update({
      rsvpGoingIds: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Report & verification request + Admin email spoofing check
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      targetType: "post",
      targetId: "post_101",
      reason: "Spam",
      details: "Suspicious repetitive links",
      status: "open",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(spoofedAdminDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(realAdminDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(
    realAdminDb.collection("reports").doc("rep_1").update({
      status: "resolved",
      resolutionNote: "Reviewed and actioned",
      updatedAt: pastDate(1000),
    })
  );

  // Immutable admin audit log
  await assertFails(
    spoofedAdminDb.collection("admin_audit_logs").doc("log_1").set({
      adminId: "spoof_1",
      adminEmail: ADMIN_EMAIL,
      action: "RESOLVE_REPORT",
      targetPath: "reports/rep_1",
      summary: "Spoofed attempt",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    realAdminDb.collection("admin_audit_logs").doc("log_1").set({
      adminId: ADMIN_UID,
      adminEmail: ADMIN_EMAIL,
      action: "RESOLVE_REPORT",
      targetPath: "reports/rep_1",
      summary: "Resolved report rep_1",
      createdAt: ts,
    })
  );
  await assertFails(realAdminDb.collection("admin_audit_logs").doc("log_1").delete());
});
