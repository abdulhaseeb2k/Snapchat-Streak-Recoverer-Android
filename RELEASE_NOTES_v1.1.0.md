# Snapchat Streak Recoverer (SSR) — Release Notes v1.1.0

**Release Date:** September 16, 2026  
**Version:** 1.1.0  
**Version Code:** 2  
**Target Platform:** Android 12.0+ (API Level 31 to 35)  
**Package Name:** \com.abdulhaseeb2k.ssr\  
**Build Status:** Production Signed Release  

---

## 📦 Binary Artifact Details

| Property | Value |
| :--- | :--- |
| **File Name** | \pp-release.apk\ |
| **Relative Path** | \pp/build/outputs/apk/release/app-release.apk\ |
| **File Size** | 62.6 MB (62,646,612 bytes) |
| **Signature Scheme** | APK Signature Scheme v2 (Verified) |
| **SHA-256 Hash** | \59D3179F3B6233576937E24C87D6CD1A911639C59A5156849AD179A3793B3495\ |
| **MD5 Hash** | \1B901CC69B66FC3C5D24A31EB86641E7\ |

---

## 🔑 Signing & Certificate Information

The APK has been officially compiled and signed with the production release keystore:

* **Keystore Alias:** \ssr_release_key\
* **Validity:** Valid until Sunday, February 1, 2054
* **Signing Algorithm:** SHA384withRSA (2048-bit RSA)
* **Release Certificate SHA-1:**
  \\\	ext
  5E:17:5B:CD:86:09:70:B7:4E:84:9B:17:FF:F3:24:16:C1:8B:1C:F4
  \\\
* **Release Certificate SHA-256:**
  \\\	ext
  65:16:E2:BF:45:24:FC:00:12:38:22:EF:5A:4C:B1:74:03:E6:26:F4:65:1F:A2:0D:2C:97:31:21:A6:52:92:9C
  \\\

---

## 🌟 What's New in v1.1.0

### 1. Google Authentication & Cloud Synchronization
* **Google Sign-In Integration:** Log in using your Google account to back up and sync your Snapchat profiles, friends lists, and recovery submissions across all your devices.
* **Android 14 & Samsung S23 Ultra Optimization:** Upgraded to the modern \GetSignInWithGoogleOption\ button flow with fallback to \GetGoogleIdOption\, ensuring flawless account picker dialog rendering on Samsung OneUI 6/7 and Android 14+ devices.
* **Bi-directional Real-time Cloud Sync:** Powered by Cloud Firestore with UUID-based synchronization (\syncId\), soft-delete support, and automatic timestamp conflict resolution.

### 2. UI / UX Redesign & Modernization
* **Stateful Cloud Sync Cards:** Displays real-time status for Unauthenticated, Loading, Authenticated, and Error states.
* **Enhanced Error Handling:** Human-readable explanations for network errors, missing Google accounts, and configuration statuses, complete with "Retry" and "Dismiss" controls.
* **Modern Compose Material 3 Dialogs:** New profile creation and editing dialogs with input validation and clear leading icons.
* **Accounts Counter & Quick Actions:** Improved home screen with account count badges, import/export buttons, and quick-add actions.
* **Settings & Appearance:** Dynamic app version display linked directly to \BuildConfig.VERSION_NAME\, with customizable Light, Dark, and System Default themes.

---

## 📲 Installation Instructions

1. **Transfer the APK to your device:**
   Copy \pp-release.apk\ to your Android phone via USB cable, Google Drive, or local file sharing.
2. **Enable Unknown Sources:**
   If prompted by Android, go to **Settings ➔ Security / Privacy** and allow **Install unknown apps** for your file manager or browser.
3. **Install the App:**
   Tap \pp-release.apk\ and select **Install**.
4. **Launch & Sign In:**
   Open the **Snapchat Streak Recoverer** app and tap **"Sign In with Google"** on the home screen to activate Cloud Sync.

---

## ⚙️ Backend & Firebase Checklist

Ensure the following are configured in your [Firebase Console](https://console.firebase.google.com/):
1. **Firebase Authentication:** Google Provider enabled with project support email selected.
2. **SHA Fingerprints:** Both Debug SHA-1 and Release SHA-1 registered under \com.abdulhaseeb2k.ssr\.
3. **Firestore Database:** Initialized in **asia-south1** (or default region) with user-scoped security rules.
