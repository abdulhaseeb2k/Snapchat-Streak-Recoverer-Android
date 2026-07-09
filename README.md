# Snapchat Streak Recoverer (Android)

An Android application designed to automate the process of submitting Snapchat streak recovery requests. This app helps users manage multiple accounts and automate the tedious form-filling process on the Snapchat Support website.

## 📥 Download

You can download the latest version of the app from the [Releases](https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases) section. 

1. Go to [Latest Release](https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases/latest).
2. Under the **Assets** section, download `app-release-signed.apk`.
3. Open the APK on your Android device to install.

## 🚀 Features

- **Multi-Account Management**: Add, edit, and delete multiple Snapchat profiles.
- **Friend Lists**: Import or manually add friends for each profile to recover streaks with.
- **Automated Recovery**: Uses WebView automation to automatically fill and submit the Snapchat Support form for selected friends.
- **JSON Import**: Easily import your account and friend data from a JSON file.
- **Theme Customization**: Support for Light, Dark, and System Default themes.
- **Real-time Progress**: Visual feedback and progress indicators during the automated recovery process.
- **Detailed Logging**: Built-in debug logging for troubleshooting automation scripts.

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (for local data persistence)
- **Settings**: DataStore (for theme preferences)
- **Network**: WebView (for form automation)
- **Build System**: Gradle (with AGP 9.0+)

## 📦 Getting Started

### Prerequisites

- Android Studio Meerkat or newer.
- Android device or emulator running API 31 (Android 12) or higher.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Build and run the app on your device.

## 📄 Usage

1. **Add a Profile**: Tap the "+ Add New" button or use the "Import" button to load data from a JSON file.
2. **Select Friends**: Click on a profile to view the friends list. Select the friends you want to recover streaks with.
3. **Start Recovery**: Tap the "RECOVER SELECTED STREAKS" button. The app will open a WebView and start filling the forms automatically.
4. **Monitor Progress**: You can see the current progress on the overlay. If any error occurs, a "Go Back" button will appear with debug information.

## ⚙️ Configuration

The app uses an automated script to fill the Snapchat Support form. The fields targeted include:
- Username
- Email
- Mobile Number
- Device
- Friend's Username
- Date of issue
- Description

## 🤝 Contributing

Contributions are welcome! If you'd like to improve the automation script or add new features:
1. Fork the project.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 🛡️ Disclaimer

This tool is intended for personal use to automate the submission of support requests. Users should ensure they comply with Snapchat's Terms of Service. The developers are not responsible for any misuse of this application.

---
Developed by [Abdul Haseeb](https://github.com/abdulhaseeb2k)
