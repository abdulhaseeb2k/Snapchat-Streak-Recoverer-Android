# Codebase Memory - Snapchat Streak Recoverer (SSR)

## Project Overview
An Android application designed to automate the process of submitting Snapchat streak recovery requests via WebView automation.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM
- **Database:** Room
- **Preferences:** DataStore
- **Automation:** WebView + JavaScript injection

## Key Components

### 1. UI Layer (`com.snapstreakrecoverer.ssr.ui`)
- Contains Compose screens and ViewModels for managing profiles, friends, and the recovery process.

### 2. Data Layer (`com.snapstreakrecoverer.ssr.data`)
- **Entities:** `Profile`, `Friend`
- **Room Database:** Manages persistence for account and friend data.
- **Repository:** Abstracts data access for the UI layer.

### 3. Recovery Logic (`com.snapstreakrecoverer.ssr.recovery`)
- **`RecoveryManager`:** Manages the lifecycle of the recovery process. It handles WebView initialization, navigation, and state management (Processing, Complete, Error).
- **`RecoveryScript`:** Generates the JavaScript injected into the Snapchat Support WebView to automate form filling and submission.

## Automation Flow
1. **Initialize:** `RecoveryManager` sets up a WebView with necessary settings (JS enabled, cookies, custom User-Agent).
2. **Start:** User selects a profile and friends, then triggers recovery.
3. **Form Loading:** `RecoveryManager` loads the Snapchat support form URL.
4. **Injection:** Once the page finishes loading, `RecoveryScript.getFillFormScript` generates a JS script that is injected into the WebView.
5. **Form Filling:** The JS script finds form fields by ID or label and populates them with data from the selected `Profile` and `Friend`.
6. **Submission:** The script clicks the submit button.
7. **Detection:** `RecoveryManager` detects successful submission via URL changes or a JS bridge.
8. **Iteration:** The process repeats for the next selected friend after a configurable delay.

## Configuration
- **Form URL:** `https://help.snapchat.com/hc/en-us/requests/new?co=true&ticket_form_id=149423`
- **Fields Targeted:** Username, Email, Mobile Number, Device, Friend's Username, Date, Description.

## Important Notes
- The app uses `CookieManager` to handle reCAPTCHA by accepting third-party cookies.
- It employs a `JavascriptInterface` (`Android`) to communicate between the WebView and the native Kotlin code.
- Resiliency is built-in: if a form fails to load or fill, it records the failure and moves to the next friend.
