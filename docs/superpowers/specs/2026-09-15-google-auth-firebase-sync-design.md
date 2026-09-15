# Technical Specification: Google Authentication & Firebase Cloud Firestore Sync

## 1. Overview
This specification details the architecture, data models, synchronization protocol, and UI integration for adding Google Authentication and Firebase Cloud Firestore synchronization to the Snapchat Streak Recoverer (SSR) Android app.

### Goals
- Enable users to sign in with their Google account to back up and synchronize profiles, friends, and settings across multiple devices.
- Maintain an **offline-first** architecture: the app remains fully functional offline as a guest without blocking the user.
- Provide instant, zero-latency local UI updates using Room SQLite as the single source of truth.
- Synchronize data bi-directionally in real time via Cloud Firestore snapshot listeners and background writes.
- Seamlessly merge existing local guest data into the user's Google account upon their first sign-in.

---

## 2. Architecture & Components

```
+-----------------------------------------------------------------------+
|                              UI Layer                                 |
|   (Compose Screens: ProfileScreen, FriendScreen, SettingsScreen)      |
+-----------------------------------------------------------------------+
        |                                                 |
        v                                                 v
+------------------------+                     +-----------------------+
|  ViewModels            |                     |  AuthViewModel        |
|  (Profile, Friend,     |                     |  - StateFlow<AuthState|
|   Settings)            |                     |  - signInWithGoogle() |
+------------------------+                     |  - signOut()          |
        |                                       +-----------------------+
        v                                                 |
+------------------------------------+                    v
|          SyncManager               | <-------> +---------------------+
|  - Real-time Firestore Listeners   |           |    AuthManager      |
|  - Bidirectional Room <-> Firestore|           | - CredentialManager |
|  - Conflict resolution (LWW)       |           | - FirebaseAuth      |
+------------------------------------+           +---------------------+
        |                     |
        v                     v
+---------------+     +-----------------------------------+
|  RecoveryDao  |     |  Cloud Firestore                  |
|  (Room DB)    |     |  users/{uid}/profiles/...         |
+---------------+     +-----------------------------------+
```

### Core Components
1. **`AuthManager`**:
   - Manages authentication state using `FirebaseAuth`.
   - Coordinates with Android's `androidx.credentials.CredentialManager` and `GetGoogleIdOption` to prompt the Google account picker.
   - Exposes `authState: StateFlow<AuthState>` (`Unauthenticated`, `Loading`, `Authenticated(FirebaseUser)`, `Error(String)`).
2. **`SyncManager`**:
   - Central coordinator for cloud synchronization.
   - Listens to `AuthManager.authState`. When authenticated, starts synchronization for the active user ID.
   - Attaches snapshot listeners to `users/{uid}/profiles` (and subcollections) to stream cloud updates into Room.
   - Dispatches local inserts, updates, and deletes to Firestore.
   - Handles guest data merging on first login.
3. **`RecoveryDao` & `RecoveryDatabase` (Room)**:
   - Remains the single source of truth for all UI reads.
   - All queries return reactive `Flow`s.
4. **UI**:
   - `ProfileScreen`: Displays an optional "Enable Cloud Sync" banner when unauthenticated.
   - `SettingsScreen`: Displays account status, user avatar, email, sync indicator, and Sign In / Sign Out actions.

---

## 3. Data Models & Schemas

### 3.1 Room Schema Updates & Migration (v1 -> v2)
To allow conflict-free cross-device syncing, stable UUIDs (`syncId`) replace integer auto-increment IDs as the global cross-device identity.

#### `Profile` Entity
```kotlin
@Entity(
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val profileName: String,
    val snapchatUsername: String,
    val email: String,
    val mobileNumber: String,
    val device: String,
    val refreshDelay: Double = 1.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

#### `Friend` Entity
```kotlin
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["profileId"]),
        Index(value = ["profileSyncId"])
    ]
)
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val profileId: Int,
    val profileSyncId: String,
    val username: String,
    val displayName: String,
    val isSelected: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

#### Room Database Migration (1 -> 2)
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add syncId, updatedAt, isDeleted to Profile
        db.execSQL("ALTER TABLE Profile ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Profile ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Profile ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        
        // Add syncId, profileSyncId, updatedAt, isDeleted to Friend
        db.execSQL("ALTER TABLE Friend ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Friend ADD COLUMN profileSyncId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Friend ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Friend ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        
        // Populate syncId for existing rows
    }
}
```

### 3.2 Cloud Firestore Structure
```
users/{userId}/
  ├── settings/preferences
  │     ├── theme: String ("LIGHT" | "DARK" | "SYSTEM")
  │     └── updatedAt: Long
  │
  └── profiles/{profileSyncId}
        ├── syncId: String
        ├── profileName: String
        ├── snapchatUsername: String
        ├── email: String
        ├── mobileNumber: String
        ├── device: String
        ├── refreshDelay: Double
        ├── updatedAt: Long
        ├── isDeleted: Boolean
        │
        └── friends/{friendSyncId}
              ├── syncId: String
              ├── profileSyncId: String
              ├── username: String
              ├── displayName: String
              ├── isSelected: Boolean
              ├── updatedAt: Long
              └── isDeleted: Boolean
```

---

## 4. Synchronization Protocol

### 4.1 Sign-In & Initial Merge
1. User logs in with Google.
2. `SyncManager.startSync(userId)` is triggered.
3. **Local-to-Cloud Upload**:
   - Query all active Room profiles (`isDeleted == 0`).
   - For each profile and its friends, upsert to Firestore batch.
4. **Cloud-to-Local Listeners**:
   - Register snapshot listener on `users/{userId}/profiles`.
   - Register snapshot listener on `users/{userId}/settings/preferences`.
   - For each profile document change:
     - If `isDeleted == true`: delete from local Room database.
     - If remote `updatedAt > local.updatedAt`: upsert to Room.
     - Listen to the `friends` subcollection for each profile.

### 4.2 Local Mutation Dispatch
- When `ProfileViewModel` or `FriendViewModel` creates, edits, or deletes an item:
  - Writes to Room immediately with `updatedAt = System.currentTimeMillis()`.
  - If authenticated, dispatches an asynchronous `set()` or soft-delete update to Firestore.
  - If offline, Firestore's local cache automatically queues the mutation until connectivity is restored.

### 4.3 Sign-Out
- Detach all Firestore snapshot listeners.
- Cancel all active synchronization coroutines.
- Keep local Room database intact (user continues in guest mode).

---

## 5. Dependencies to Add

### `gradle/libs.versions.toml`
```toml
[versions]
firebaseBom = "33.10.0"
credentials = "1.5.0-rc01"
googleid = "1.1.1"

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
google-android-libraries-identity-googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

---

## 6. Verification & Testing Plan

1. **Room Migration Test**: Verify migration from database version 1 to 2 preserves existing profiles and friends, generates valid UUIDs, and properly associates `profileSyncId`.
2. **Offline Guest Mode Test**: Verify that adding, updating, and deleting profiles and friends works without any network connection or Firebase account.
3. **Google Sign-In Flow**: Verify Credential Manager triggers the Google account selection sheet and Firebase Auth successfully signs in with Google ID token.
4. **Cloud Sync Verification**:
   - Create profile on Device A -> verify it appears in Firestore and immediately on Device B.
   - Edit friend on Device B -> verify update reflects on Device A.
   - Delete profile on Device A -> verify soft-deletion cascades and removes it on Device B.
5. **Build Verification**: Run `./gradlew assembleDebug` to confirm build succeeds without deprecation errors or dependency conflicts.
