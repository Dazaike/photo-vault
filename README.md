# Photo Vault

Encrypted on-device photo and video vault for Android, styled with the Prism design language.

**Current version:** 1.12.22 (versionCode 39) · see [CHANGELOG.md](CHANGELOG.md)

## Features
- AES-256 encrypted storage for photos and videos, behind a biometric lock
- Folders, trash with 30-day retention, copy / share / save to storage
- Save to storage with an auto-delete timer (30 sec – 5 min) and an optional live countdown notification with **Delete now**
- Hold a photo for a quick-action menu
- Theme and accent colour settings

## Build
Requires the Android SDK (platform 37) and JDK 17+. Create `local.properties` with
`sdk.dir=<path to your Android SDK>` (not committed).

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # install on a connected device
```

## Release build
`./gradlew assembleRelease` signs with the key described in `keystore.properties` (git-ignored; keys:
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without that file the release APK is left unsigned.
Keep the keystore backed up: updates must be signed with the same key.
