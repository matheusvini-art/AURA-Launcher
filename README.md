# AURA Launcher

AURA is a native Android home-screen launcher built with Kotlin and Jetpack Compose. The visual direction combines graphite surfaces, warm stone tones and a restrained terracotta accent, with a dark AMOLED option.

## Current version

**0.3.1 — visual redesign in development**

### Current features
- Android Home role request with a result check and system Settings fallback for manufacturer-specific launchers.
- Redesigned home dashboard with greeting, live clock, date, app-library entry, favorites, recent apps, collections and a bottom dock.
- Graphite and black AMOLED appearances, five accent colors, icon sizing, grid density and app-label settings.
- Redesigned personalization screen with theme previews and organized sections.
- App library with search, All / Favorites / Hidden filters and app tiles.
- Long-press app actions: favorite, hide/unhide, app info and uninstall request.
- User-created folders with selected installed apps.
- Alphabetical and recent-first app ordering.
- Horizontal swipe to open the app library.
- Custom adaptive launcher icon.
- Local preference storage; no analytics, advertising SDK, account or internet permission in this version.

## Build

Requirements: Android Studio, JDK 17 and Android SDK 35.

Open in Android Studio or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK for pushes to `main`, pull requests and manual runs. The artifact is retained for 14 days.

## Set AURA as the default launcher

Open AURA and tap **Definir AURA como principal**. On Android 10 and later, AURA requests the system Home role and checks whether Android granted it after the system confirmation closes. If that role prompt is unavailable on the device, AURA opens the system's Home / Default apps settings as a fallback. Select **AURA Launcher** there.

## Roadmap

Planned work includes Android AppWidgetHost support for real widgets, compatible icon-pack support, drag-and-drop placement and folder editing, notification dots with explicit notification-listener opt-in, backup/restore of layout and deeper gesture customization.

## Privacy

Preferences are stored locally. The app currently has no internet permission, analytics, advertising SDK or user account. App information and uninstall actions are handed to Android system screens.

## License

No license has been selected yet. Do not redistribute modified copies under an assumed license.
