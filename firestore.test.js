const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_utkarsh";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 2000);
}

function validUserPayload(uid, username, overrides = {}) {
  const now = tsNow();
  return {
    userId: uid,
    username,
    displayName: "Test User",
    bio: "Hello",
    role: "USER",
    isGoldenVerified: false,
    isPrivate: false,
    isBanned: false,
    demoFollowersDisplay: 0,
    followersCount: 0,
    followingCount: 0,
    postsCount: 0,
    lastActiveAt: now,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot read users or posts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("posts").get());
});

test("Ordinary user: cannot self-assign Golden Verification or SUPER_ADMIN role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "alice", { isGoldenVerified: true })
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "alice", { role: "SUPER_ADMIN" })
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "alice", { demoFollowersDisplay: 300000 })
    )
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "alice", { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Valid ordinary user creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice"))
  );

  // Shadow update on golden verification fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ isGoldenVerified: true })
  );
});

test("Email spoofing test: unverified admin email cannot create SUPER_ADMIN or admins doc", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "utkarshgaming8029@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid").set({
      userId: "spoof_uid",
      email: "utkarshgaming8029@gmail.com",
      role: "SUPER_ADMIN",
      createdAt: tsNow(),
    })
  );
});

test("Verified Super Admin can register, get golden tick, and grant/revoke golden tick for others", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "utkarshgaming8029@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "utkarshgaming8029@gmail.com",
      role: "SUPER_ADMIN",
      createdAt: tsNow(),
    })
  );

  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set(
      validUserPayload(ADMIN_UID, "utkarshrai", {
        displayName: "Utkarsh Rai",
        role: "SUPER_ADMIN",
        isGoldenVerified: true,
        demoFollowersDisplay: 300000,
      })
    )
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice"))
  );

  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      isGoldenVerified: true,
      updatedAt: tsNow(),
    })
  );
});

test("PII Isolation: Bob cannot read Alice's private/info or saved posts or login activity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      dateOfBirth: "2000-01-01",
      twoFactorEnabled: false,
      backupCodes: [],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("login_activity").doc("act1").set({
      userId: ALICE_UID,
      deviceModel: "Pixel 9",
      authMethod: "GOOGLE",
      createdAt: tsNow(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("login_activity").get()
  );
});

test("Posts, Comments, Conversations, Reports, and Support Tickets enforce ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice"))
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob"))
  );

  // Alice creates post
  await assertSucceeds(
    aliceDb.collection("posts").doc("post1").set({
      authorId: ALICE_UID,
      caption: "Golden hour #scrolldown",
      mediaUrls: ["data:image/jpeg;base64,abc"],
      mediaType: "PHOTO",
      hashtags: ["scrolldown"],
      taggedUsernames: [],
      musicTitle: "",
      likesCount: 0,
      commentsCount: 0,
      viewsCount: 0,
      isReel: false,
      isArchived: false,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post1").delete());

  // Conversation isolation (third user charlie cannot read Alice-Bob chat)
  const convId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hi Bob",
      lastMessageMediaType: "TEXT",
      typingUids: [],
      lastMessageAt: tsNow(),
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get()
  );

  // Reports query must match reporterId unless admin
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep1").set({
      reporterId: ALICE_UID,
      targetType: "POST",
      targetId: "post1",
      targetSummary: "Spam",
      reason: "Test reason",
      status: "OPEN",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("reports").get());
  await assertSucceeds(aliceDb.collection("reports").where("reporterId", "==", ALICE_UID).get());

  // Support ticket ownership
  await assertSucceeds(
    aliceDb.collection("support_tickets").doc("tick1").set({
      userId: ALICE_UID,
      subject: "Help needed",
      message: "Need assistance with badge",
      status: "OPEN",
      createdAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("support_tickets").doc("tick1").get());
  await assertSucceeds(aliceDb.collection("support_tickets").where("userId", "==", ALICE_UID).get());
});

test("Verification requests cannot be self-approved; only admin can review", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "utkarshgaming8029@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Ordinary user cannot submit APPROVED request
  await assertFails(
    aliceDb.collection("verification_requests").doc("vr1").set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice Creator",
      category: "CREATOR",
      reason: "Publishing daily luxury travel reels on SCROLL DOWN.",
      portfolioLink: "https://example.com",
      status: "APPROVED",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Valid PENDING request succeeds
  await assertSucceeds(
    aliceDb.collection("verification_requests").doc("vr1").set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice Creator",
      category: "CREATOR",
      reason: "Publishing daily luxury travel reels on SCROLL DOWN.",
      portfolioLink: "https://example.com",
      status: "PENDING",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Alice cannot self-approve, Bob cannot read Alice's request
  await assertFails(
    aliceDb.collection("verification_requests").doc("vr1").update({
      status: "APPROVED",
      updatedAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("verification_requests").doc("vr1").get());

  // Super Admin approves
  await assertSucceeds(
    adminDb.collection("verification_requests").doc("vr1").update({
      status: "APPROVED",
      adminNote: "Verified creator account.",
      updatedAt: tsNow(),
    })
  );
});

test("Group chat, voice messages, reactions, calls, notes, close friends & live streams enforce isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();

  // Group conversation with 3 participants + AUDIO message + reaction
  const groupConvId = "grp_team_1";
  await assertSucceeds(
    aliceDb.collection("conversations").doc(groupConvId).set({
      participantIds: [ALICE_UID, BOB_UID, "charlie_789"],
      isGroup: true,
      groupName: "Luxury Creators",
      groupAdminId: ALICE_UID,
      lastSenderId: ALICE_UID,
      lastMessage: "Voice note",
      lastMessageMediaType: "AUDIO",
      typingUids: [],
      lastReadMs: { [ALICE_UID]: Date.now() },
      lastMessageAt: tsNow(),
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(groupConvId).collection("messages").doc("m1").set({
      participantIds: [ALICE_UID, BOB_UID, "charlie_789"],
      senderId: ALICE_UID,
      recipientId: groupConvId,
      text: "Voice message (4s)",
      mediaUrl: "data:audio/mp4;base64,AAA",
      mediaType: "AUDIO",
      reactions: {},
      isSeen: false,
      createdAt: tsNow(),
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc(groupConvId).collection("messages").doc("m1").update({
      isSeen: true,
      reactions: { [BOB_UID]: "🔥" },
    })
  );

  // Outsider Dave cannot read group chat
  const daveDb = testEnv.authenticatedContext("dave_999").firestore();
  await assertFails(daveDb.collection("conversations").doc(groupConvId).get());

  // Close Friends & Watch History are strictly owner-only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("close_friends").doc(BOB_UID).set({
      targetId: BOB_UID,
      createdAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("close_friends").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watch_history").doc("post1").set({
      postId: "post1",
      mediaType: "REEL",
      watchedAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("watch_history").get());

  // Call signaling isolated to participants
  await assertSucceeds(
    aliceDb.collection("calls").doc("call1").set({
      callerId: ALICE_UID,
      calleeId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertFails(charlieDb.collection("calls").doc("call1").get());
  await assertSucceeds(
    bobDb.collection("calls").doc("call1").update({
      status: "ACCEPTED",
      updatedAt: tsNow(),
    })
  );

  // Notes & Live Streams
  await assertSucceeds(
    aliceDb.collection("notes").doc(ALICE_UID).set({
      authorId: ALICE_UID,
      text: "Editing evening reel in Obsidian Gold!",
      musicTitle: "Midnight Gold",
      audience: "ALL",
      expiresAtMs: Date.now() + 86400000,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    bobDb.collection("notes").doc(ALICE_UID).set({
      authorId: ALICE_UID,
      text: "Hijack",
      musicTitle: "",
      audience: "ALL",
      expiresAtMs: Date.now() + 86400000,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live1").set({
      hostId: ALICE_UID,
      title: "Behind the Lens Q&A",
      isLive: true,
      status: "LIVE",
      viewerIds: [ALICE_UID],
      viewerCount: 1,
      peakViewerCount: 1,
      startedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    bobDb.collection("live_streams").doc("live1").collection("comments").doc("lc1").set({
      streamId: "live1",
      authorId: BOB_UID,
      text: "Great lighting setup!",
      createdAt: tsNow(),
    })
  );

  // Non-host Bob cannot end Alice's live stream
  await assertFails(
    bobDb.collection("live_streams").doc("live1").update({
      isLive: false,
      status: "ENDED",
      viewerCount: 0,
      endedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Host Alice ends her live stream
  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live1").update({
      isLive: false,
      status: "ENDED",
      viewerIds: [],
      viewerCount: 0,
      endedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Cannot re-open, join, or comment on ended live stream
  await assertFails(
    aliceDb.collection("live_streams").doc("live1").update({
      isLive: true,
      status: "LIVE",
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    bobDb.collection("live_streams").doc("live1").update({
      viewerIds: [ALICE_UID, BOB_UID],
      viewerCount: 2,
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    bobDb.collection("live_streams").doc("live1").collection("comments").doc("lc2").set({
      streamId: "live1",
      authorId: BOB_UID,
      text: "Too late!",
      createdAt: tsNow(),
    })
  );
});

test("Super Admin can repeatedly toggle Golden Verification ON/OFF; non-admin cannot", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "utkarshgaming8029@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    adminDb.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "utkarshgaming8029@gmail.com",
      role: "SUPER_ADMIN",
      createdAt: tsNow(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice"))
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob"))
  );

  // Toggle ON -> OFF -> ON
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({ isGoldenVerified: true, updatedAt: tsNow() })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({ isGoldenVerified: false, updatedAt: tsNow() })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({ isGoldenVerified: true, updatedAt: tsNow() })
  );

  // Bob cannot toggle Alice's badge
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ isGoldenVerified: false, updatedAt: tsNow() })
  );
});

test("Story owner can delete own story and its viewer records; non-owner cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice"))
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob"))
  );

  await assertSucceeds(
    aliceDb.collection("stories").doc("story1").set({
      authorId: ALICE_UID,
      mediaUrl: "data:image/jpeg;base64,abc",
      mediaType: "PHOTO",
      caption: "Sunset",
      highlightTitle: null,
      audience: "ALL",
      allowedViewerIds: [],
      isArchived: false,
      expiresAtMs: Date.now() + 86400000,
      createdAt: tsNow(),
    })
  );

  // Bob records view
  await assertSucceeds(
    bobDb.collection("stories").doc("story1").collection("views").doc(BOB_UID).set({
      viewerId: BOB_UID,
      reactionEmoji: "🔥",
      viewedAt: tsNow(),
    })
  );

  // Non-owner Bob cannot delete Alice's story or Charlie's view
  await assertFails(bobDb.collection("stories").doc("story1").delete());

  // Owner Alice can delete Bob's view record and the parent story
  await assertSucceeds(
    aliceDb.collection("stories").doc("story1").collection("views").doc(BOB_UID).delete()
  );
  await assertSucceeds(aliceDb.collection("stories").doc("story1").delete());
});


