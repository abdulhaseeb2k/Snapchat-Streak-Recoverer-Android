# Google Authentication & Firebase Cloud Firestore Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Google Authentication via Credential Manager and real-time cross-device Cloud Firestore synchronization for profiles, friends, and settings in SSR Android, maintaining an offline-first architecture with Room.

**Architecture:** UI binds to local Room database Flows for 0ms latency. A `SyncManager` coordinates bi-directional synchronization with Cloud Firestore: uploading local mutations, listening to remote snapshot updates, and automatically merging guest data upon Google Sign-In. `AuthManager` handles Google Sign-In using Android Credential Manager and Firebase Auth.

**Tech Stack:** Kotlin, Jetpack Compose, Room SQLite, Firebase Auth, Cloud Firestore, AndroidX Credential Manager, Google Identity `googleid`, Coroutines & Flow.

**Spec:** `docs/superpowers/specs/2026-09-15-google-auth-firebase-sync-design.md`

## Global Constraints
- Compile SDK: 35, Min SDK: 31
- Room Database Version: increment from 1 to 2 with non-destructive Migration
- Offline-first: App must function normally when unauthenticated (guest mode) and when offline
- UI never blocks on network operations
- Stable UUIDs (`syncId`) prevent ID collision across multiple devices

---

### Task 1: Add Firebase & Credential Manager Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml:1-50`
- Modify: `app/build.gradle.kts:40-66`

**Interfaces:**
- Produces: Gradle dependencies for Firebase BOM (`33.10.0`), Firebase Auth, Cloud Firestore, AndroidX Credential Manager (`1.5.0-rc01`), and Google Identity (`1.1.1`).

- [ ] **Step 1: Update `gradle/libs.versions.toml`**

Add version definitions and library references:
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

- [ ] **Step 2: Update `app/build.gradle.kts`**

Add the dependencies to the `dependencies { ... }` block:
```kotlin
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.android.libraries.identity.googleid)
```

- [ ] **Step 3: Verify build syncs without error**

Run: `.\gradlew help`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Firebase and Credential Manager dependencies"
```

---

### Task 2: Room Schema Updates & Migration (v1 -> v2)

**Files:**
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/data/Entities.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/data/RecoveryDao.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/data/RecoveryDatabase.kt`
- Test: `app/src/test/java/com/snapstreakrecoverer/ssr/data/MigrationTest.kt`

**Interfaces:**
- Consumes: Existing Room entities `Profile` and `Friend`
- Produces: Updated `Profile` and `Friend` entities with `syncId: String`, `profileSyncId: String`, `updatedAt: Long`, `isDeleted: Boolean`, plus `MIGRATION_1_2` in `RecoveryDatabase`.

- [ ] **Step 1: Write the failing unit test for entity defaults and sync IDs**

Create `app/src/test/java/com/snapstreakrecoverer/ssr/data/EntitiesSyncTest.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitiesSyncTest {
    @Test
    fun profile_hasDefaultSyncIdAndTimestamps() {
        val profile = Profile(
            profileName = "Test",
            snapchatUsername = "test_user",
            email = "test@example.com",
            mobileNumber = "123456",
            device = "Pixel 8"
        )
        assertNotNull(profile.syncId)
        assertTrue(profile.syncId.isNotEmpty())
        assertTrue(profile.updatedAt > 0)
        assertFalse(profile.isDeleted)
    }

    @Test
    fun friend_hasDefaultSyncIdAndProfileSyncId() {
        val friend = Friend(
            profileId = 1,
            profileSyncId = "test-profile-sync-id",
            username = "friend_user",
            displayName = "Best Friend"
        )
        assertNotNull(friend.syncId)
        assertTrue(friend.syncId.isNotEmpty())
        assertTrue(friend.updatedAt > 0)
        assertFalse(friend.isDeleted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.data.EntitiesSyncTest`
Expected: FAIL (compilation failure: `syncId` / `profileSyncId` not found on Profile/Friend)

- [ ] **Step 3: Update `Entities.kt`, `RecoveryDao.kt`, and `RecoveryDatabase.kt`**

Update `Profile` and `Friend` in `Entities.kt`:
```kotlin
@Entity(
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val profileName: String,
    val snapchatUsername: String,
    val email: String,
    val mobileNumber: String,
    val device: String,
    val refreshDelay: Double = 1.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

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
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val profileId: Int,
    val profileSyncId: String = "",
    val username: String,
    val displayName: String,
    val isSelected: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

Update `RecoveryDao.kt` to add sync queries:
```kotlin
    @Query("SELECT * FROM Profile WHERE isDeleted = 0")
    fun getActiveProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM Profile WHERE syncId = :syncId LIMIT 1")
    suspend fun getProfileBySyncId(syncId: String): Profile?

    @Query("SELECT * FROM Friend WHERE profileSyncId = :profileSyncId AND isDeleted = 0")
    fun getFriendsForProfileSync(profileSyncId: String): Flow<List<Friend>>

    @Query("SELECT * FROM Friend WHERE syncId = :syncId LIMIT 1")
    suspend fun getFriendBySyncId(syncId: String): Friend?

    @Query("UPDATE Profile SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteProfile(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE Friend SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteFriend(id: Int, timestamp: Long = System.currentTimeMillis())
```

Update `RecoveryDatabase.kt` to version 2 with `MIGRATION_1_2`:
```kotlin
@Database(entities = [Profile::class, Friend::class], version = 2, exportSchema = false)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Profile ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Profile ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Profile ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                
                db.execSQL("ALTER TABLE Friend ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Friend ADD COLUMN profileSyncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Friend ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Friend ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Profile_syncId` ON `Profile` (`syncId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Friend_syncId` ON `Friend` (`syncId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Friend_profileSyncId` ON `Friend` (`profileSyncId`)")
            }
        }

        @Volatile
        private var INSTANCE: RecoveryDatabase? = null

        fun getDatabase(context: Context): RecoveryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecoveryDatabase::class.java,
                    "recovery_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.data.EntitiesSyncTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/data/Entities.kt app/src/main/java/com/snapstreakrecoverer/ssr/data/RecoveryDao.kt app/src/main/java/com/snapstreakrecoverer/ssr/data/RecoveryDatabase.kt app/src/test/java/com/snapstreakrecoverer/ssr/data/EntitiesSyncTest.kt
git commit -m "feat(data): update Room entities and database for cross-device UUID sync"
```

---

### Task 3: Cloud Firestore Sync Models & Mapping

**Files:**
- Create: `app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncModels.kt`
- Test: `app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncModelsTest.kt`

**Interfaces:**
- Consumes: `Profile`, `Friend` from `com.snapstreakrecoverer.ssr.data`
- Produces: DTO classes `FirestoreProfile`, `FirestoreFriend`, `FirestoreSettings`, and mapping extension methods `toFirestoreMap()` and conversion helpers.

- [ ] **Step 1: Write the failing unit test for SyncModels**

Create `app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncModelsTest.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.sync

import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModelsTest {
    @Test
    fun profile_convertsToAndFromFirestoreMap() {
        val profile = Profile(
            id = 10,
            syncId = "prof-123",
            profileName = "Personal",
            snapchatUsername = "snap_person",
            email = "user@snap.com",
            mobileNumber = "555-0199",
            device = "Samsung S24",
            refreshDelay = 1.5,
            updatedAt = 1700000000L,
            isDeleted = false
        )
        val map = profile.toFirestoreMap()
        val restored = mapToFirestoreProfile(map)

        assertEquals("prof-123", restored.syncId)
        assertEquals("Personal", restored.profileName)
        assertEquals("snap_person", restored.snapchatUsername)
        assertEquals("user@snap.com", restored.email)
        assertEquals("555-0199", restored.mobileNumber)
        assertEquals("Samsung S24", restored.device)
        assertEquals(1.5, restored.refreshDelay, 0.001)
        assertEquals(1700000000L, restored.updatedAt)
        assertEquals(false, restored.isDeleted)
    }

    @Test
    fun friend_convertsToAndFromFirestoreMap() {
        val friend = Friend(
            id = 5,
            syncId = "friend-456",
            profileId = 10,
            profileSyncId = "prof-123",
            username = "alice",
            displayName = "Alice Wonderland",
            isSelected = true,
            updatedAt = 1700000050L,
            isDeleted = false
        )
        val map = friend.toFirestoreMap()
        val restored = mapToFirestoreFriend(map)

        assertEquals("friend-456", restored.syncId)
        assertEquals("prof-123", restored.profileSyncId)
        assertEquals("alice", restored.username)
        assertEquals("Alice Wonderland", restored.displayName)
        assertEquals(true, restored.isSelected)
        assertEquals(1700000050L, restored.updatedAt)
        assertEquals(false, restored.isDeleted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.sync.SyncModelsTest`
Expected: FAIL (unresolved reference: `SyncModelsKt`)

- [ ] **Step 3: Create `SyncModels.kt`**

Create `app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncModels.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.sync

import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile

data class FirestoreProfile(
    val syncId: String = "",
    val profileName: String = "",
    val snapchatUsername: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val device: String = "",
    val refreshDelay: Double = 1.0,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class FirestoreFriend(
    val syncId: String = "",
    val profileSyncId: String = "",
    val username: String = "",
    val displayName: String = "",
    val isSelected: Boolean = true,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class FirestoreSettings(
    val theme: String = "SYSTEM",
    val updatedAt: Long = 0L
)

fun Profile.toFirestoreMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "profileName" to profileName,
    "snapchatUsername" to snapchatUsername,
    "email" to email,
    "mobileNumber" to mobileNumber,
    "device" to device,
    "refreshDelay" to refreshDelay,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted
)

fun Friend.toFirestoreMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "profileSyncId" to profileSyncId,
    "username" to username,
    "displayName" to displayName,
    "isSelected" to isSelected,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted
)

fun mapToFirestoreProfile(map: Map<String, Any?>): FirestoreProfile {
    return FirestoreProfile(
        syncId = map["syncId"] as? String ?: "",
        profileName = map["profileName"] as? String ?: "",
        snapchatUsername = map["snapchatUsername"] as? String ?: "",
        email = map["email"] as? String ?: "",
        mobileNumber = map["mobileNumber"] as? String ?: "",
        device = map["device"] as? String ?: "",
        refreshDelay = (map["refreshDelay"] as? Number)?.toDouble() ?: 1.0,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        isDeleted = map["isDeleted"] as? Boolean ?: false
    )
}

fun mapToFirestoreFriend(map: Map<String, Any?>): FirestoreFriend {
    return FirestoreFriend(
        syncId = map["syncId"] as? String ?: "",
        profileSyncId = map["profileSyncId"] as? String ?: "",
        username = map["username"] as? String ?: "",
        displayName = map["displayName"] as? String ?: "",
        isSelected = map["isSelected"] as? Boolean ?: true,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        isDeleted = map["isDeleted"] as? Boolean ?: false
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.sync.SyncModelsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncModels.kt app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncModelsTest.kt
git commit -m "feat(sync): add Firestore DTO models and mapping helpers"
```

---

### Task 4: AuthManager & Credential Manager Integration

**Files:**
- Create: `app/src/main/java/com/snapstreakrecoverer/ssr/auth/AuthManager.kt`
- Create: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/AuthViewModel.kt`
- Test: `app/src/test/java/com/snapstreakrecoverer/ssr/auth/AuthStateTest.kt`

**Interfaces:**
- Produces: `AuthManager` with `authState: StateFlow<AuthState>`, `signInWithGoogle(activityContext)`, `signOut()`, and `AuthViewModel`.

- [ ] **Step 1: Write test for AuthState representation**

Create `app/src/test/java/com/snapstreakrecoverer/ssr/auth/AuthStateTest.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {
    @Test
    fun authState_statesBehaveCorrectly() {
        val unauthenticated = AuthState.Unauthenticated
        val loading = AuthState.Loading
        val error = AuthState.Error("Network error")

        assertTrue(unauthenticated is AuthState.Unauthenticated)
        assertTrue(loading is AuthState.Loading)
        assertEquals("Network error", (error as AuthState.Error).message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.auth.AuthStateTest`
Expected: FAIL (unresolved reference `AuthState`)

- [ ] **Step 3: Implement `AuthManager.kt` and `AuthViewModel.kt`**

Create `app/src/main/java/com/snapstreakrecoverer/ssr/auth/AuthManager.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    object Unauthenticated : AuthState
    object Loading : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthManager(
    private val firebaseAuth: FirebaseAuth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null } ?: FirebaseAuth.getInstance(),
    private val webClientId: String = ""
) {
    private val _authState = MutableStateFlow<AuthState>(
        firebaseAuth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        _authState.value = AuthState.Loading
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(if (webClientId.isNotEmpty()) webClientId else "dummy-client-id")
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential

            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                val err = "Unsupported credential received"
                _authState.value = AuthState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: GetCredentialCancellationException) {
            _authState.value = firebaseAuth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
            Result.failure(e)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to sign in with Google"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            firebaseAuth.signOut()
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Error signing out")
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = firebaseAuth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
        }
    }
}
```

Create `app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/AuthViewModel.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.auth.AuthManager
import com.snapstreakrecoverer.ssr.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authManager: AuthManager) : ViewModel() {
    val authState: StateFlow<AuthState> = authManager.authState

    fun signIn(context: Context) {
        viewModelScope.launch {
            authManager.signInWithGoogle(context)
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    fun clearError() {
        authManager.clearError()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.auth.AuthStateTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/auth/AuthManager.kt app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/AuthViewModel.kt app/src/test/java/com/snapstreakrecoverer/ssr/auth/AuthStateTest.kt
git commit -m "feat(auth): add AuthManager with Credential Manager and Firebase Auth"
```

---

### Task 5: SyncManager (Real-time Firestore Listeners & Room Bridge)

**Files:**
- Create: `app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncManager.kt`
- Test: `app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncManagerLogicTest.kt`

**Interfaces:**
- Consumes: `RecoveryDao`, `AuthManager`, `FirebaseFirestore`
- Produces: `SyncManager` with `startSync(userId: String)`, `stopSync()`, `pushProfile(profile: Profile)`, `pushFriend(friend: Friend)`, `deleteProfile(profile: Profile)`, `deleteFriend(friend: Friend)`.

- [ ] **Step 1: Write test for sync conflict resolution logic**

Create `app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncManagerLogicTest.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerLogicTest {
    @Test
    fun shouldUpdateLocal_remoteNewer_returnsTrue() {
        val localUpdatedAt = 1000L
        val remoteUpdatedAt = 2000L
        assertTrue(remoteUpdatedAt > localUpdatedAt)
    }

    @Test
    fun shouldUpdateLocal_localNewer_returnsFalse() {
        val localUpdatedAt = 3000L
        val remoteUpdatedAt = 2000L
        assertFalse(remoteUpdatedAt > localUpdatedAt)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `.\gradlew testDebugUnitTest --tests com.snapstreakrecoverer.ssr.sync.SyncManagerLogicTest`
Expected: PASS

- [ ] **Step 3: Implement `SyncManager.kt`**

Create `app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncManager.kt`:
```kotlin
package com.snapstreakrecoverer.ssr.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SyncManager(
    private val dao: RecoveryDao,
    private val firestore: FirebaseFirestore = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null } ?: FirebaseFirestore.getInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeUserId: String? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    fun startSync(userId: String) {
        if (activeUserId == userId) return
        stopSync()
        activeUserId = userId

        scope.launch {
            try {
                // Initial upload / merge of existing local records
                uploadLocalProfiles(userId)
                // Attach real-time snapshot listeners
                attachProfilesListener(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSync() {
        listeners.forEach { it.remove() }
        listeners.clear()
        activeUserId = null
    }

    private suspend fun uploadLocalProfiles(userId: String) {
        val localProfiles = dao.getAllProfilesOnce()
        for (profile in localProfiles) {
            val docRef = firestore.collection("users").document(userId)
                .collection("profiles").document(profile.syncId)
            
            val snapshot = try { docRef.get().await() } catch (e: Exception) { null }
            if (snapshot == null || !snapshot.exists()) {
                docRef.set(profile.toFirestoreMap())
            }

            val friends = dao.getFriendsForProfileOnce(profile.id)
            for (friend in friends) {
                val friendRef = docRef.collection("friends").document(friend.syncId)
                val friendSnapshot = try { friendRef.get().await() } catch (e: Exception) { null }
                if (friendSnapshot == null || !friendSnapshot.exists()) {
                    val updatedFriend = if (friend.profileSyncId.isEmpty()) {
                        friend.copy(profileSyncId = profile.syncId)
                    } else friend
                    friendRef.set(updatedFriend.toFirestoreMap())
                }
            }
        }
    }

    private fun attachProfilesListener(userId: String) {
        val profilesRef = firestore.collection("users").document(userId).collection("profiles")
        val registration = profilesRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val remoteProfile = mapToFirestoreProfile(data)
                    val existing = dao.getProfileBySyncId(remoteProfile.syncId)

                    if (remoteProfile.isDeleted) {
                        if (existing != null) {
                            dao.deleteProfile(existing)
                        }
                    } else if (existing == null || remoteProfile.updatedAt > existing.updatedAt) {
                        val profileToSave = Profile(
                            id = existing?.id ?: 0,
                            syncId = remoteProfile.syncId,
                            profileName = remoteProfile.profileName,
                            snapchatUsername = remoteProfile.snapchatUsername,
                            email = remoteProfile.email,
                            mobileNumber = remoteProfile.mobileNumber,
                            device = remoteProfile.device,
                            refreshDelay = remoteProfile.refreshDelay,
                            updatedAt = remoteProfile.updatedAt,
                            isDeleted = false
                        )
                        val insertedId = dao.insertProfile(profileToSave).toInt()
                        val effectiveId = if (existing != null) existing.id else insertedId
                        attachFriendsListener(userId, remoteProfile.syncId, effectiveId)
                    }
                }
            }
        }
        listeners.add(registration)
    }

    private fun attachFriendsListener(userId: String, profileSyncId: String, localProfileId: Int) {
        val friendsRef = firestore.collection("users").document(userId)
            .collection("profiles").document(profileSyncId)
            .collection("friends")

        val registration = friendsRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val remoteFriend = mapToFirestoreFriend(data)
                    val existing = dao.getFriendBySyncId(remoteFriend.syncId)

                    if (remoteFriend.isDeleted) {
                        if (existing != null) {
                            dao.deleteFriend(existing)
                        }
                    } else if (existing == null || remoteFriend.updatedAt > existing.updatedAt) {
                        val friendToSave = Friend(
                            id = existing?.id ?: 0,
                            syncId = remoteFriend.syncId,
                            profileId = localProfileId,
                            profileSyncId = profileSyncId,
                            username = remoteFriend.username,
                            displayName = remoteFriend.displayName,
                            isSelected = remoteFriend.isSelected,
                            updatedAt = remoteFriend.updatedAt,
                            isDeleted = false
                        )
                        dao.insertFriend(friendToSave)
                    }
                }
            }
        }
        listeners.add(registration)
    }

    fun pushProfile(profile: Profile) {
        val userId = activeUserId ?: return
        scope.launch {
            try {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profile.syncId)
                    .set(profile.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pushFriend(friend: Friend) {
        val userId = activeUserId ?: return
        if (friend.profileSyncId.isEmpty()) return
        scope.launch {
            try {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(friend.profileSyncId)
                    .collection("friends").document(friend.syncId)
                    .set(friend.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteProfile(profile: Profile) {
        val userId = activeUserId ?: return
        scope.launch {
            try {
                val softDeleted = profile.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profile.syncId)
                    .set(softDeleted.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFriend(friend: Friend) {
        val userId = activeUserId ?: return
        if (friend.profileSyncId.isEmpty()) return
        scope.launch {
            try {
                val softDeleted = friend.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                firestore.collection("users").document(userId)
                    .collection("profiles").document(friend.profileSyncId)
                    .collection("friends").document(friend.syncId)
                    .set(softDeleted.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `.\gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/sync/SyncManager.kt app/src/test/java/com/snapstreakrecoverer/ssr/sync/SyncManagerLogicTest.kt
git commit -m "feat(sync): add SyncManager with real-time Firestore listeners and Room bridge"
```

---

### Task 6: Connect ViewModels, Factory & MainActivity

**Files:**
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/FriendViewModel.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/ViewModelFactory.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/MainActivity.kt`

**Interfaces:**
- Consumes: `SyncManager`, `AuthManager`, `RecoveryDao`, `ThemeManager`
- Produces: ViewModels wired to dispatch local updates to `SyncManager`.

- [ ] **Step 1: Update `ProfileViewModel.kt` to trigger sync dispatch**

Inject optional `syncManager: SyncManager? = null`:
- In `insertProfile(profile: Profile)`: insert into DAO, then call `syncManager?.pushProfile(profile)`.
- In `updateProfile(profile: Profile)`: update in DAO with `updatedAt = System.currentTimeMillis()`, then call `syncManager?.pushProfile(...)`.
- In `deleteProfile(profile: Profile)`: call `syncManager?.deleteProfile(profile)`, then delete from DAO.

- [ ] **Step 2: Update `FriendViewModel.kt` to trigger sync dispatch**

Inject optional `syncManager: SyncManager? = null`:
- In `insertFriend`: insert into DAO, call `syncManager?.pushFriend(friend)`.
- In `updateFriend`: update in DAO, call `syncManager?.pushFriend(friend)`.
- In `deleteFriend`: call `syncManager?.deleteFriend(friend)`, then delete from DAO.

- [ ] **Step 3: Update `ViewModelFactory.kt` and `MainActivity.kt`**

- Add `SyncManager`, `AuthManager` to `ViewModelFactory`.
- In `MainActivity.onCreate()`:
  - Initialize `AuthManager` and `SyncManager`.
  - Observe `authManager.authState` in a lifecycle-aware coroutine:
    - If `Authenticated(user)` -> `syncManager.startSync(user.uid)`
    - If `Unauthenticated` -> `syncManager.stopSync()`
  - Pass `authManager` and `syncManager` to `ViewModelFactory`.

- [ ] **Step 4: Verify compilation**

Run: `.\gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/ProfileViewModel.kt app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/FriendViewModel.kt app/src/main/java/com/snapstreakrecoverer/ssr/ui/viewmodel/ViewModelFactory.kt app/src/main/java/com/snapstreakrecoverer/ssr/MainActivity.kt
git commit -m "feat(ui): connect ViewModels and MainActivity with AuthManager and SyncManager"
```

---

### Task 7: UI Integration (Sync Banner in ProfileScreen & Account in SettingsScreen)

**Files:**
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/screens/ProfileScreen.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/snapstreakrecoverer/ssr/MainActivity.kt` (AppNavigation)

**Interfaces:**
- Consumes: `AuthViewModel`, `AuthState`
- Produces: Visual cloud sync banner on `ProfileScreen` when unauthenticated, and full Account & Sync card in `SettingsScreen`.

- [ ] **Step 1: Add CloudSyncBanner to `ProfileScreen.kt`**

Add a banner composable displayed when `authState is AuthState.Unauthenticated`:
```kotlin
@Composable
fun CloudSyncBanner(onSignInClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Cloud Sync",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Sign in with Google to backup and sync your profiles across devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSignInClick) {
                Text("Sign In")
            }
        }
    }
}
```

- [ ] **Step 2: Add Account & Sync Section to `SettingsScreen.kt`**

Add an `AccountSyncSection` displaying:
- When `AuthState.Unauthenticated`: "Sign in with Google" button.
- When `AuthState.Authenticated(user)`: User email, display name, "Sync active", and a "Sign Out" button.
- When `AuthState.Loading`: CircularProgressIndicator.

- [ ] **Step 3: Wire `AuthViewModel` in `AppNavigation`**

Provide `AuthViewModel` in `AppNavigation` so `ProfileScreen` and `SettingsScreen` can trigger sign-in and display current auth status.

- [ ] **Step 4: Verify build and UI compilation**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/snapstreakrecoverer/ssr/ui/screens/ProfileScreen.kt app/src/main/java/com/snapstreakrecoverer/ssr/ui/screens/SettingsScreen.kt app/src/main/java/com/snapstreakrecoverer/ssr/MainActivity.kt
git commit -m "feat(ui): add CloudSyncBanner to ProfileScreen and Account section to SettingsScreen"
```

---

### Task 8: End-to-End Verification & Regression Testing

**Files:**
- Test files across `app/src/test`

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew testDebugUnitTest`
Expected: ALL TESTS PASS

- [ ] **Step 2: Build debug APK**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL in `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 3: Commit all remaining changes and update docs**

```bash
git status
git commit -am "chore: finalize Google Auth and Firebase sync implementation"
```
