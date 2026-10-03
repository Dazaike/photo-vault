# Changelog

All notable changes to Photo Vault.

## [v1.12.23] - 2026-10-03

### Added
- Export backup / Import backup (More menu): one password-protected, encrypted file with all photos, videos,
  trash, folders and settings. Import merges: photos already in the vault are skipped.
- The app now asks for notification permission at startup.

### Changed
- All appearance settings (haptics, motion, brightness) are now remembered between launches, not just theme and accent.
- README rewritten (`f5651da`, `1ec51fd`, `9bd8f06`).

## [v1.12.22] - 2026-10-02

First release tracked in git, so this summarises everything since v1.12.20.

### Added
- New look: liquid-glass buttons, sheets and dialogs, a new font, an accent colour and a very dark grey background.
- Settings (More, then Settings): theme and accent colour, remembered between launches.
- Hold a photo for a quick menu: select, open, copy, share, save to storage, move to folder, trash. In Trash:
  restore or delete forever.
- Video length badge on thumbnails.
- 30 second auto-delete option, now the default.
- Optional countdown notification for auto-delete, with a **Delete now** button. Shows as a Live Update on
  Android 16 and newer.

### Changed
- Save to storage: save location and auto-delete timer are now tile grids, and the sheet slides up and
  resizes smoothly. The Save button shows progress while saving.
- All popup sheets now frost whatever is behind them, including video.
- The "Saved to storage" toast is gone.
- The release build is now signed with a dedicated release key. If you are on an earlier build you will need to
  uninstall it first, which deletes the vault, so export anything you want to keep.

### Fixed
- Auto-delete sometimes not deleting while the app was in the background.
- Lag when loading lots of photos.
