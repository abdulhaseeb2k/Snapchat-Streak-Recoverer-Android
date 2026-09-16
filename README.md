<div align="center">

# 🔥 Snapchat Streak Recoverer (Android)

**Production-grade Android application automating Snapchat streak recovery with real-time Firebase Cloud Sync and multi-device persistence.**

[![Version](https://img.shields.io/badge/version-1.1.0-FFFC00?style=for-the-badge&logo=android&logoColor=black)](https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases/latest)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%26%20Firestore-FFA611?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com)
[![License](https://img.shields.io/badge/license-MIT-10A37F?style=for-the-badge)](LICENSE)

> Built with modern Android development best practices: **100% Jetpack Compose**, **Clean Architecture + MVVM**, **Room SQLite v2**, **AndroidX Credential Manager (Android 14+)**, and **Cloud Firestore Real-Time Bi-Directional Synchronization**.

</div>

---

## 📱 What is SSR?

**Snapchat Streak Recoverer (SSR)** eliminates the manual, tedious process of submitting individual streak restoration tickets on the Snapchat Support Portal. Whether you manage multiple personal accounts, client accounts, or extensive friend lists, SSR streamlines recovery submissions into automated bulk processing.

Together with the companion [**SSR Chrome Extension**](https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Extension), your accounts, friend selections, and settings seamlessly sync across desktop and mobile devices via **Google Sign-In** and **Cloud Firestore**.

---

## 🌟 Key Features

### 🔐 Google Authentication & Cloud Sync
- **Modern Credential Manager**: Integrated with `androidx.credentials` and `GoogleId` API, tailored specifically for **Android 14 (OneUI 6/7, Samsung Galaxy S23/S24 series)** with graceful dialog fallback.
- **Real-Time Cross-Device Sync**: Profiles and friend lists sync bi-directionally between Android and Chrome Extension via Cloud Firestore.
- **Offline-First Resilience**: Full local persistence using Room SQLite v2 with UUID-based conflict resolution (Last-Write-Wins timestamps) and soft-deletion tracking.

### 👤 Multi-Account Management
- Store distinct Snapchat credentials (Username, Recovery Email, Mobile Number, Device Type, and submission throttle delays).
- Create, modify, switch, and back up multiple profiles with Material 3 input validation.

### 👥 Friend Selection & Bulk Recovery
- Filter and search friends by display name or Snapchat username.
- Batch select/deselect contacts with single-click bulk toggles.
- Automated WebView workflow with dynamic in-page execution, human-interaction delay tuning, and progress overlays.

### 🎨 Modern Material 3 UI/UX
- Stateful animated sync cards (Guest mode, Loading, Synchronized, and Error states).
- Dark mode, Light mode, and System Dynamic Theme compatibility.
- In-app crash resilience and real-time step diagnostics.

---

## 🛠️ Architecture & Tech Stack

The project adheres strictly to **Clean Architecture** principles and Android recommended guidelines:

```
app/
 ├── auth/                 # Google Credential Manager & Firebase Authentication
 ├── data/                 # Room Database v2, Entities, DAOs, SQLite Migrations
 ├── sync/                 # Bi-directional Firestore Sync Engine & DTO Serializers
 ├── ui/
 │    ├── components/      # Reusable Material 3 Composables (Banners, Modals, Cards)
 │    ├── navigation/      # Jetpack Compose Navigation Graph
 │    ├── screens/         # Profile, Friends, Recovery WebView, and Settings
 │    └── viewmodel/       # MVVM StateFlow ViewModels & Factory Layer
 └── utils/                # Automation scripts, Network monitors, and formatters
```

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0.21 (Coroutines, StateFlow) |
| **UI** | 100% Jetpack Compose + Material 3 |
| **Database** | Room SQLite (Automated v1 -> v2 Migration) |
| **Cloud Backend** | Firebase Authentication + Cloud Firestore |
| **Identity** | AndroidX Credential Manager + Google Identity Services |
| **Preferences** | AndroidX Jetpack DataStore |
| **Build & CI** | Gradle 9.0+, AGP, Automated Release Signing (`apksigner` v2) |

---

## 📥 Download & Installation

The production-ready release is compiled and cryptographically signed:

1. Download **[`app-release.apk`](https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases/latest)**.
2. Transfer or open the file on your device (Android 12.0+ / API Level 31 to 35).
3. If prompted, allow **Install unknown apps** for your browser or file manager.
4. Launch the app and tap **"Sign In with Google"** to activate cloud synchronization.

For verification checksums and certificate details, check out the [Release Notes v1.1.0](RELEASE_NOTES_v1.1.0.md).

---

## ⚙️ Building from Source

### Prerequisites
- Android Studio Meerkat (2024.3+) or newer
- JDK 17+
- Android SDK 35

### Steps
```bash
# 1. Clone the repository
git clone https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android.git
cd Snapchat-Streak-Recoverer-Android

# 2. Add your Firebase google-services.json to the /app directory

# 3. Run automated tests
./gradlew testDebugUnitTest

# 4. Build debug APK
./gradlew assembleDebug
```

---

## 🔒 Security & Privacy

- **No Passwords Stored**: SSR does **not** store or request your Snapchat account password.
- **User-Scoped Cloud Storage**: Firestore documents are scoped strictly to the authenticated user ID (`users/{userId}/...`).
- **Cryptographic Signing**: Official release binaries are signed with a protected 2048-bit RSA key.

---

## 👨‍💻 Author & Maintainer

**Abdul Haseeb**  
- GitHub: [@abdulhaseeb2k](https://github.com/abdulhaseeb2k)
- Android & Full-Stack Developer

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
