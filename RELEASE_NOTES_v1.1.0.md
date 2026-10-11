# PixPet 1.1.0

## What changed

- Reworked the pixel artwork for the original Clawd character and made the cat and dog skins more distinct, with outlines, shading, expressions, and wagging tails.
- Reused the same renderer for the floating pet and settings previews, so the preview now matches the on-screen character.
- Added more idle actions (stretching, yawning, grooming, spinning, and waving), more dance poses, and a much larger set of Russian dialogue lines.
- Rebuilt music reactions around Android active media sessions. PixPet can react to playback from different media apps, not only Telegram. It needs Notification Listener access to read media-session state; it does not record or analyze audio.
- Improved the settings screen’s permission guidance and updated the app label to **PixPet - Пиксельный питомец**.
- Kept object-eating and UI-element hiding out of the app. Pooping remains available on demand, and each poop can be cleaned with a tap.

## Compatibility and installation

- Package: `com.frizovdanya.pixpet`
- Version code: `2` · version name: `1.1.0`
- Minimum Android: 6.0 (API 23) · target Android: 15 (API 35)
- The downloadable APK is debug-signed for manual testing, not Google Play distribution. Because its debug key may differ from the earlier build, Android may ask you to uninstall the older PixPet build before installing this APK.

PixPet is created by **@frizovdanya**. The pet concept is inspired by the Clawd Telegram plugin 1.5 by **@itskotovski**; see [NOTICE.md](NOTICE.md) for attribution and scope.
