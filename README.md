# photo-vault

Your gallery app is a glass house with a "private" folder that is one swipe from anyone holding your phone. This repository fixes that.

Photo Vault is an encrypted on-device photo and video vault for Android. Everything is encrypted at rest, nothing phones home, and the UI is liquid glass because we have standards.

**Current version:** 1.12.22 (versionCode 39) · [changelog](CHANGELOG.md) · [releases](../../releases)

## What it does

- **Encrypts everything.** Photos, videos and their thumbnails are stored with AES-256-GCM (Jetpack Security `EncryptedFile`, key in the Android Keystore). No cloud, no account, no analytics. There is no server to breach because there is no server.
- **Locks itself.** The vault re-locks when the screen turns off. Screenshots and screen recording are blocked (`FLAG_SECURE`).
- **Folders and trash.** Organise into folders. Deleted items sit in trash for 30 days, then they are purged.
- **Hold a photo for the quick menu.** Select, open, copy, share, save to storage, move to folder, trash. Long-press a photo, tap the thing. Revolutionary.
- **Save to storage with a self-destruct timer.** Export a copy back to the gallery, then have it deleted after 30 sec, 1, 3 or 5 minutes. Or keep it forever, your call.
- **Live countdown notification (optional).** Turn it on in More, then Settings. On Android 16+ it is a promoted Live Update with a status-bar countdown chip. It has a **Delete now** button for when 30 seconds is still too long.
- **Video duration badges** on thumbnails, so you stop opening a 40 minute video to find a 4 second clip.
- **Fast on big libraries.** Thumbnails decode off-thread with a capped number of decodes in flight and a 64 MiB in-memory cache. Scrolling does not fall over.
- **Prism design.** Glass buttons and sheets, Outfit type, one accent colour you pick, a very dark grey background, subtle haptics, no ripples. Theme and accent live in More, then Settings.

## Install

Grab `PhotoVault-<version>.apk` from the [latest release](../../releases/latest) and install it.

Android only updates an app in place when the new APK is signed with the same key as the installed one. Release APKs are signed with the release key. If you are on a debug build, switching means uninstalling, and uninstalling deletes the vault and its encryption key. There is no backup path (`allowBackup` is off, on purpose). Export what you care about first.

Requires Android 8.0 (API 26) or newer.

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Unlocking the vault |
| `MANAGE_EXTERNAL_STORAGE` | Optionally deleting originals from the gallery after import |
| `SCHEDULE_EXACT_ALARM` | Making auto-delete fire on time instead of whenever Doze feels like it |
| `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` | The optional countdown notification |
| `VIBRATE` | Haptics |

WorkManager and the biometric library add a few of their own (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE`, `USE_FINGERPRINT`). The app does not request `INTERNET`, so it cannot reach the network at all.

## The caveats

Snark is earned by honesty, so:

- A file you save to storage is a plain, unencrypted copy until its timer ends. That is the entire point of saving it. Pick the timer accordingly.
- Auto-delete uses an exact alarm, a WorkManager job, an in-app timer and a cleanup on app open. If every one of those is blocked, the file stays until the next time any of them runs.
- Lose the phone's lock or uninstall the app and the vault is gone. Nobody can recover it, including us.

## Build

Requires JDK 17+ and the Android SDK (platform 37). Create `local.properties` with `sdk.dir=<path to your Android SDK>`. It is git-ignored.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # straight onto a connected device
```

### Release build

```bash
./gradlew assembleRelease
```

Signing comes from a git-ignored `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. No file, no signature, and the APK is left unsigned. Back the keystore up. Updates must be signed with the same key, and no amount of wishing changes that.

## Stack

Kotlin, Jetpack Compose, Room, WorkManager, Media3 ExoPlayer, Jetpack Security, and the Kyant0 Backdrop library for the glass. Gradle 9.3.1, Android Gradle Plugin 9.1.1, Kotlin 2.4.20.
