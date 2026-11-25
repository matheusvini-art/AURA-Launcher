# AURA Launcher

AURA is a native Android home-screen launcher built with Kotlin and Jetpack Compose. The design direction is a refined, dark personal dashboard with a clear visual hierarchy, restrained color, and local-first preferences.

## Current version

**0.3.0 — visual redesign in development**

### Current features
- Android Home role request with an activity-result check and Settings fallback for OEM launchers.
- Redesigned home dashboard with greeting, live clock, date, search entry, favorites, recent apps, collections, and bottom dock.
- Dark Midnight and black AMOLED backgrounds.
- Five accent colors, icon size, grid density, and app-label settings.
- Redesigned personalization screen with theme previews and organized sections.
- App drawer with search and All / Favorites / Hidden filters.
- Long-press app actions: favorite, hide/unhide, app info, and uninstall request.
- Favorites and recently opened apps on the home screen.
- User-created folders with selected installed apps.
- Alphabetical and recent-first drawer sorting.
- Horizontal swipe to open the app drawer.
- Adaptive launcher icon.
- Local preference storage; no analytics, advertising SDK, account, or network permission in this version.

## Build

Requirements: Android Studio, JDK 17, Android SDK 35.

Open in Android Studio or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK for pushes to `main`, pull requests, and manual runs. The artifact is retained for 14 days.

## Set AURA as the default launcher

Open AURA and tap **Definir AURA como principal**. On Android 10 and later, AURA requests the system Home role and checks whether Android granted it after the system sheet closes. On devices that do not expose that role prompt, AURA opens the system's Home / Default apps settings as a fallback. Select **AURA Launcher** there.

## Roadmap

Planned work includes real Android AppWidgetHost support, compatible icon-pack support, drag-and-drop placement and folder editing, notification dots with explicit notification-listener opt-in, backup/restore of layout, and deeper gesture customization.

## Privacy

Preferences are stored locally. The app currently has no internet permission, analytics, advertising SDK, or user account. App details and uninstall actions are handed to Android system screens.

## License

No license has been selected yet. Do not redistribute modified copies under an assumed license.
