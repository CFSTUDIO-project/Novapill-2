# Changelog

## 2.0.0 (2026-11-27)

Version code 2. Settings schema 2; settings saved by 1.0 migrate automatically.

### Fixed
- Flashlight: the tile and the compact pill no longer claim "On" without a confirmed torch state. Cause: a fixed "On" in the compact tools card and a stored boolean instead of the camera service's state.
- Flashlight: no stale state from an earlier run; the torch callback is registered only while the island service runs.
- Build: the GitHub Actions workflow no longer asks the SDK action to install the retired `tools` package (the first run failed there).
- Build: `PreviewModel.volume` and `PreviewModel.toolsPage` clashed on the JVM with the `setVolume` and `setToolsPage` overrides; renamed to `volumeFraction` and `toolsPageIndex` (the second run failed there). The third run is green.

### Added
- Idle panel with real clock, date, weather (Open-Meteo, with loading, offline and stale states) and battery; three idle modes; the pill widens around a camera cutout.
- Quick panel with two pages and twelve tiles, each hideable; tiles report unavailable actions honestly.
- Per-camera torch tracking with pending, busy and "not confirmed" handling; strength slider on Android 13+.
- Notification peek with app name, sender and one-line preview; three privacy levels; de-duplication and removal handling; reply from the peek when the notification offers a reply action.
- Screen recorder on MediaProjection with consent on every start, a foreground service, MP4 output to `Movies/NOVA-PILL` through MediaStore, states Idle, Preparing, Recording, Stopping, Saved, Error, optional microphone, countdown, 720p/1080p, 30/60 fps.
- Freeform launch for app shortcuts through the public `ActivityOptions` API, with a clear message when the device does not support it.
- Internal floating calculator window: drag, resize, close.
- Settings sections *Quick panel*, *Screen recorder*, *Floating windows* and new options in existing sections.
- "V2" badge on the intro screen and a one-time What's new card.
- Unit tests for the torch tracker, recorder state machine, calculator engine, floating geometry, storage formatting, notification text and de-duplication, settings schema 2 and migration (188 in total).

### Not supported
- Recording device (internal) audio.

## 1.0.0

First release: Dynamic-Island-style overlay pill with music (synced lyrics, visualizer), timer, stopwatch, call and notification peeks, landscape layout, app picker and a full settings area. Debug APK built on GitHub Actions.
