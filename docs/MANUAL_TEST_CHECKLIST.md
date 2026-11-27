# Manual test checklist (2.0.0)

Run these on a real phone with the debug APK. Tick what passes; write down what does not (see the template at the bottom). The unit tests cover the logic only, so everything below is still open until you run it.

Device: ____________ Android version: ____ Build: ____ Date: ____

## 0. Install and first run
- [ ] APK installs over 1.0 without losing settings (settings migrate to schema 2).
- [ ] Intro screen shows the **V2** badge; the What's new card appears once and stays dismissed.
- [ ] Wizard asks for overlay permission, notification access and (Android 13+) post-notifications, one at a time.
- [ ] The pill appears at the top after the overlay permission is granted.

## 1. Flashlight (the 1.0 bug)
- [ ] Fresh start, flashlight untouched: the tile says **Off**; the compact pill never says "On".
- [ ] Press the tile: it shows "Turning on", then **On** only after the torch is really lit.
- [ ] Press again: "Turning off", then **Off**; the torch is really dark.
- [ ] Turn the torch on from the system quick settings: the tile follows within a moment. Turn it off there: the tile follows.
- [ ] Open the camera app and use the flash: the tile shows "In use" or the correct state, never a stale one.
- [ ] Close the island service and start it again: no stale "On" from the earlier run.
- [ ] Android 13+ with a multi-level flash: the strength slider appears and changes brightness.
- [ ] Device without a flash unit (or an emulator): the tile says "No flash".

## 2. Idle panel
- [ ] Idle mode *Clock*, *Clock and battery*, *Full*: each shows what the setting says.
- [ ] Clock follows the system time and the 12/24-hour setting; it changes at the minute.
- [ ] Battery percentage and icon match the system; plug in the charger and it updates.
- [ ] Weather: shows "Updating" first, then temperature; with airplane mode shows an offline or stale state, not invented data.
- [ ] Temperature unit: Auto, Celsius, Fahrenheit.
- [ ] Phone with a camera cutout: the pill widens around the cutout and nothing is hidden behind it.
- [ ] Location permission denied: weather shows an unavailable state; the rest works.

## 3. Quick panel
- [ ] Two pages (Quick, More); swipe or tabs switch pages; page dots follow.
- [ ] Header shows clock, date, weather and battery.
- [ ] Each tile does what it says: Stopwatch, Record, Alarms, Calendar, Phone, Internet, Volume, Camera, Calculator, Settings.
- [ ] Internet tile shows Wi-Fi, mobile data, Ethernet or Offline correctly.
- [ ] Hide a tile in Settings, Quick panel: it disappears; hide all tiles of a page: the page says so.
- [ ] Tools card opens and closes; it also shows while the flashlight is on.

## 4. Notifications
- [ ] Message from a chat app: the pill shows app name, sender and a one-line preview (Full level).
- [ ] Summary level: app and sender only. App-name-only level: no sender, no text.
- [ ] A secret notification, or any notification while the phone is locked: app name only.
- [ ] The same notification re-posted several times: one peek, not a stack.
- [ ] Dismiss the notification in the shade: the peek goes away.
- [ ] Reply button appears only for notifications that have a reply action; sending works; a failure shows a message.
- [ ] Incoming call peek still shows Answer and Decline.
- [ ] Per-app filter: a muted app never peeks.
- [ ] Nothing about message text appears in logcat from the app (check with `adb logcat`).

## 5. Screen recorder
- [ ] Start from the Record tile: the system consent dialog appears every time.
- [ ] Deny the dialog: the pill says "You did not allow screen capture" and nothing keeps running.
- [ ] Allow: optional countdown (0, 3, 5 s), then "Recording the screen" and a running timer.
- [ ] The system shows its own recording indicator (Android 14+).
- [ ] Stop: "Saving the video", then "Video saved"; **Open video** plays the file.
- [ ] The file is in `Movies/NOVA-PILL`, appears in the gallery and plays with the right orientation and length.
- [ ] Microphone on: your voice is in the recording. Microphone permission denied: recording continues without sound and the pill says so.
- [ ] Device audio is **not** recorded (not supported); the README says so.
- [ ] Rotate the phone during recording; leave the app; lock and unlock: recording continues.
- [ ] Stop immediately after start: no corrupt file is left behind ("Recording was empty" is acceptable).
- [ ] Hide-pill-while-recording option works.
- [ ] 720p and 1080p, 30 and 60 fps produce files of the right size and rate.
- [ ] Android 8 or 9 device (if you have one): file lands in the app's Movies folder and the pill says so.

## 6. Floating windows
- [ ] Calculator tile opens the floating calculator; calculations are right (12 × 8 = 96, 0.1 + 0.2 shows 0.3).
- [ ] Drag by the title bar; resize from the corner; it stays on screen; the X button closes it.
- [ ] Rotate the phone with the calculator open: it stays reachable.
- [ ] App shortcut with freeform enabled in developer options: the app opens in a freeform window.
- [ ] Same shortcut on a device without freeform: the app opens normally and the pill says freeform was not available.

## 7. Settings
- [ ] All 13 sections open: General, Appearance, Behavior, Notifications, Music, Quick panel, Screen recorder, Record indicator, Floating windows, Landscape, Utilities, Privacy & Permissions, About & Debug.
- [ ] Every new option changes behavior immediately and survives an app restart and a reboot.
- [ ] Reset in General restores defaults without crashing.

## 8. Regression from 1.0
- [ ] Music: play something; the pill shows title and artist; lyrics sync; the visualizer moves.
- [ ] Timer and stopwatch start, pause, finish; a finished timer takes priority over everything.
- [ ] Card priority holds: timer finished, media, timer, record, stopwatch, tools.
- [ ] Landscape position and size.
- [ ] Hiding rules (fullscreen apps, per-app).
- [ ] Start after reboot (if enabled).

## 9. Privacy
- [ ] *Privacy & Permissions* lists granted and missing permissions correctly.
- [ ] With the network off, nothing crashes; only weather and lyrics fail visibly.
- [ ] No files besides recordings and settings are created in the app's storage.

## Bug report template

```
What I did:
What I expected:
What happened instead:
Device and Android version:
App version (About & Debug):
Screenshot or screen recording (crop personal data):
```
