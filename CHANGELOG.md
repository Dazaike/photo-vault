# Changelog

All notable changes to Photo Vault. Format: [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Changed
- README rewritten (permissions table, install and signing notes, honest caveats). Documentation only.


## [v1.12.22] - 2026-10-02

Official signed release. No app behaviour changes since v1.12.21; the app code is identical.

### Changed
- Release APK is now signed with a dedicated release key (`PhotoVault-1.12.22.apk`) instead of the debug key.
  Android treats this as a different signing identity from the v1.12.21 debug APK, so an installed debug build
  must be uninstalled first (this deletes the vault's data on the device).
- `app/build.gradle.kts` reads release signing details from a git-ignored `keystore.properties`.

## [v1.12.21] - 2026-10-02

First release tracked in git (there is no earlier tag or history), so this entry summarises the work
done since v1.12.20 (versionCode 37) rather than a commit range.

### Added
- Prism design language throughout: liquid-glass buttons, sheets and dialogs, Outfit font, accent colour,
  haptics, solid very dark grey background (`#0D0D0D`).
- Settings sheet (More → Settings): theme (System/Light/Dark), accent colour picker, persisted between launches.
- Hold-photo menu: long-press a photo for Select, Open, Copy, Share, Save to storage, Move to folder / Remove
  from folder, Move to trash (Restore / Delete forever in Trash). Long-press still toggles once a selection is active.
- Video duration badge on grid thumbnails; durations stored in the database (schema v3, with a migration) and
  measured in the background for existing videos.
- 30 second auto-delete option; auto-delete timer now runs in seconds end to end. Default is 30 sec.
- Optional auto-delete countdown notification (Settings → Auto-delete countdown), requested as an Android 16+
  Live Update with a status-bar chip, with a **Delete now** action that removes the batch immediately.
- Exact-alarm based auto-delete (asks for the "Alarms & reminders" permission once).

### Changed
- Save-to-storage dialog: save location and auto-delete timer are tile grids; the sheet shrinks after pressing
  Save; the Save button shows a spinner and the action row fades between states; the sheet slides up on open.
- Every popup sheet now renders over the whole app so it frosts and refracts what is behind it.
- Video player uses a texture view so sheets can blur it.
- "Saved … to storage" toast removed from the save flow.
- Toolchain: Gradle 9.3.1, Android Gradle Plugin 9.1.1, Kotlin 2.4.20, KSP 2.3.12, Room 2.8.5,
  Compose BOM 2026.05.01, `compileSdk` 37.

### Fixed
- Auto-delete sometimes not firing when the app was in the background: the persisted record is now the source
  of truth, failed deletes are retried, cleanup also runs on app foreground, and an exact alarm covers a dead process.
- Lag when loading many photos: bounded-parallelism thumbnail decoding, a 64 MiB in-memory thumbnail cache, and
  selection animations moved out of composition.

### Removed
- Legacy Material 3 button helpers (`VaultButtons.kt`) and Material 3 screens in favour of the Prism kit.
