# Firebase Integration & AI Architecture for SCROLL DOWN

**SCROLL DOWN** is backed by a live, hardened **Firebase** architecture:

- **Firebase Project ID**: `gen-lang-client-0937867251`
- **Cloud Firestore Named Database**: `ai-studio-android-scrolldo-f46e85d5-94b1-4b23-94f0-40d827b43eaa`
- **Package / Application ID**: `com.aistudio.scrolldown.xkqmzv`

## 1. Authentication, App Check & Security
- **Google Sign-In via Jetpack Credential Manager**: Uses `GetGoogleIdOption` (silent sign-in) and `GetSignInWithGoogleOption` paired with `FirebaseAuth.signInWithCredential`.
- **Firebase App Check**: Configured in `AppCheckInitializer` (`configureAppCheck(this, intent)`) at the very start of `MainActivity.onCreate()`, using `DebugAppCheckProviderFactory` for debug builds and `RecaptchaEnterpriseAppCheckProviderFactory` for release builds (plus reCAPTCHA key in `firebase_applet_config.xml`).
- **Server-Side Super Admin & Golden Verification**: Only the bootstrapped verified Super Admin (`@utkarshrai` / `utkarshgaming8029@gmail.com` with `email_verified == true`) or entries in `/admins/{uid}` can grant or revoke `isGoldenVerified` badges, review verification requests, ban/unban accounts, end live streams, moderate reports, or broadcast official announcements.

## 2. Firebase AI Logic (`gemini-3.8-flash`)
- **AI Caption & Hashtag Studio** (`CreateScreen`): Uses `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")` with structured `Schema.obj(...)` JSON generation to produce luxury captions and hashtags.
- **AI Caption Translation** (`HomeFeedScreen`): Translates post captions on demand into the active app language (`English`, `Hindi`, `Spanish`) with zero hardcoded API keys.

## 3. Cloud Firestore Collections & Deployed Rules
- `/users/{userId}` — Public profile data (`displayName`, `username`, `bio`, `avatarUri`, `isPrivate`, `isGoldenVerified`, `role`, `demoFollowersDisplay`, `isBanned`, `lastActiveAt`).
- `/users/{userId}/private/info` — Owner-only PII (`email`, `dateOfBirth`, `securityPinHash`, `twoFactorEnabled`, `backupCodes`).
- `/users/{userId}/saved/{postId}`, `liked/{postId}`, `close_friends/{targetId}`, `watch_history/{postId}`, `login_activity/{id}`, `blocks/{targetId}`, `notifications/{id}` — Private per-user subcollections.
- `/usernames/{username}` — Atomic username uniqueness registry.
- `/posts/{postId}`, `likes/{userId}`, `comments/{id}` — Feed posts, carousels, Reels, scheduled posts (`scheduledAtMs`), Close Friends visibility (`audience` / `allowedViewerIds`), and `isArchived`.
- `/stories/{storyId}`, `views/{userId}` — 24h ephemeral stories, highlights, reactions, Close Friends audience, and viewership tracking.
- `/notes/{userId}` — 24h ephemeral DM notes with optional music status and Close Friends audience.
- `/follows/{relationId}`, `/conversations/{convId}`, `/messages/{msgId}` — Social graph, 1:1 & group chats (up to 25 members), photo/video/voice (`AUDIO`) messages, emoji reactions, typing indicators, and unread tracking via `lastSenderId` / `lastReadMs`.
- `/calls/{callId}` — Voice and video call signaling (`RINGING`, `ACCEPTED`, `DECLINED`, `ENDED`).
- `/live_streams/{streamId}`, `comments/{commentId}` — Real-time live broadcasts and viewer comments.
- `/verification_requests/{requestId}` — Creator/brand Golden Verification applications reviewed exclusively by Super Admin.
- `/reports/{reportId}`, `/support_tickets/{ticketId}` — Moderation reports and support tickets.

## 4. Verification & Release Signing
- Security rules in `firestore.rules` are validated with both **JavaScript (`firestore.test.js`)** and **Kotlin Robolectric (`ScrollDownFirestoreRuleTest`)** emulator suites and deployed via `fax.DeployRules`.
- **Debug APK / AAB**: Build locally with `gradle :app:assembleDebug :app:bundleDebug`.
- **Signed Release APK / AAB**: Configure `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` (or GitHub Actions secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` in `.github/workflows/android.yml`) and run `gradle :app:assembleRelease :app:bundleRelease`.
