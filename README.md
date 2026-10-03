# photo-vault

Your gallery app is a glass house with a "private" folder that is one swipe from anyone holding your phone. This repository fixes that.

Photo Vault is a private photo and video vault for Android. Everything is encrypted, nothing leaves your phone, and the UI is liquid glass because we have standards.

**Current version:** 1.12.23 · [changelog](CHANGELOG.md) · [releases](../../releases)

## What it does

- **Locks your stuff down.** Photos and videos are encrypted on the device. No account, no cloud, no tracking. The vault re-locks when the screen turns off, and screenshots are blocked.
- **Folders and trash.** Organise into folders. Deleted items sit in trash for 30 days, then they are gone.
- **Hold a photo for the quick menu.** Select, open, copy, share, save to storage, move to folder, trash. Long-press a photo, tap the thing. Revolutionary.
- **Save to storage with a self-destruct timer.** Export a copy to your gallery, then have it deleted after 30 seconds, 1, 3 or 5 minutes. Or keep it forever. Your call.
- **Live countdown notification.** Optional. Turn it on in More, then Settings. It counts down to the deletion, and **Delete now** is right there when 30 seconds is still too long.
- **Video length badges** on thumbnails, so you stop opening a 40 minute video to find a 4 second clip.
- **Password-protected backups.** More, then Export backup, writes every photo, video, folder, trash item and setting into one encrypted file. More, then Import backup restores it on any device with the password. Photos already in the vault are skipped.
- **Stays fast with huge libraries.** Scrolling does not fall over.
- **Looks good.** Glass buttons and sheets, one accent colour you pick, a very dark grey background. Theme and accent live in More, then Settings.

## Install

Grab the APK from the [latest release](../../releases/latest) and install it. Needs Android 8.0 or newer.

If you are switching from a different build of the app, Android will make you uninstall first, and uninstalling deletes the vault. Use Export backup first, then Import backup after.

## The caveats

- A file you save to storage is a plain, unencrypted copy until its timer ends. That is the entire point of saving it. Pick the timer accordingly.
- Uninstall the app and the vault is gone unless you made a backup. Forget a backup's password and nobody can recover it, including us.
