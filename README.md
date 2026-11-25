# AURA Launcher

AURA is a native Android home-screen launcher built with Kotlin and Jetpack Compose. Its direction is a calm, minimal interface with practical customization and local-first preferences.

## Current version

**0.2.0 — in development**

### Available features
- Android Home role request to set AURA as the default launcher.
- Dark Midnight and black AMOLED backgrounds.
- Accent color, icon size, grid density, and app-label settings.
- App drawer with search and All / Favorites / Hidden filters.
- Long-press app actions: favorite, hide/unhide, app info, and uninstall request.
- Favorites and recently opened apps on the home screen.
- User-created folders with selected installed apps.
- Alphabetical and recent-first drawer sorting.
- Horizontal swipe from the home content to open the app drawer.
- Local preference storage; no analytics, advertising SDK, account, or network permission in this version.
- Adaptive launcher icon.

## Build

Requirements: Android Studio, JDK 17, Android SDK 35.

Open this project in Android Studio, or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK for pushes to `main`, pull requests, and manual runs. The artifact is retained for 14 days.

## Set as the default launcher

Install AURA, open it, choose **Personalizar → Definir AURA como principal**, then approve the Android system prompt. You can also select AURA in Android Settings under Default apps / Home app. Menu wording differs by device manufacturer.

## Roadmap

Planned work includes Android AppWidgetHost support for real home-screen widgets, compatible icon-pack support, drag-and-drop placement and folder editing, notification dots with explicit notification-listener opt-in, backup/restore of layout, and additional gesture/action customization.

## Privacy

Preferences are stored locally on the device. The app currently has no internet permission, analytics, advertising SDK, or user account. App details and uninstall actions are handed to Android's system settings/package installer.

## License

No license has been selected yet. Do not redistribute modified copies under an assumed license.
