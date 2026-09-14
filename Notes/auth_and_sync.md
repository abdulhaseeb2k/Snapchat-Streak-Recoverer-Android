# Authentication & Database Module (Auth & Sync)

## Logic & Workflow (How things are currently working)
- **Local Storage Architecture:** The app currently relies exclusively on local offline persistence using Android Room (`RecoveryDatabase`).
- **Data Entities:**
  - `Profile`: Stores user configuration for streak recovery (ID, profile name, Snapchat username, email, phone number, device model, and form delay).
  - `Friend`: Linked to `Profile` via foreign key with cascading deletes (ID, profileId, username, display name, selection state).
- **Data Access & Reactivity:** Handled asynchronously via `RecoveryDao` returning Kotlin coroutine `Flow` streams directly to ViewModels (`ProfileViewModel`, `FriendViewModel`).
- **Authentication Status:** Currently, there is no authentication layer or cloud account system. All profiles and friend lists are stored solely on the local device, with manual JSON import/export capabilities (`ExportedProfile`).

## Bugs (Actionable issues to fix)
*(No active bugs currently tracked for this module)*

## Suggestions & Features (Future ideas and enhancements)
- [ ] **Google Authentication and Firebase Database integration for cross-device syncing** #suggestion #todo #firebase-sync
  - **Feature:** Implement Google Authentication and Firebase Database integration to synchronize profiles, friend lists, and settings across multiple devices.
  - **Workflow:** When a user logs in via Google Auth, the app authenticates against Firebase and establishes a connection to the user's remote cloud datastore.
  - **Data Strategy:** Implement real-time data syncing.
    - *Strategy Evaluation:* 
      - *Approach 1 (Offline-First with Local Cache & Real-time Listeners - Recommended):* Cache data locally in Room upon login and register real-time snapshot listeners with Firebase. Any local modifications write to Room and dispatch to Firebase; remote updates stream directly into Room. This minimizes UI latency, ensures full offline capability, and significantly cuts down Firebase read/write costs.
      - *Approach 2 (Direct Continuous Fetch):* Query Firebase directly on every read and write without a local caching layer. Higher latency and risk of exceeding free-tier read quotas under frequent form submissions.
