# Chromecast Remote Helper

A small Android TV accessibility service with two functions:

- The Chromecast remote's Netflix button opens the stock Jellyfin TV app.
- A configurable night schedule blocks the remote's arrows after playback has
  finished. Bringing the remote close to the Chromecast unlocks navigation
  until the next video finishes.

Other keys (including Back, Home, centre/select, volume and playback controls)
pass through. Navigation also remains available in the helper's own settings
and from other input devices. This is a personal habit aid, not a parental
control or tamper-resistant lock.

## Schedule

Each day labels an **evening**. Friday's default 01:30 means Saturday morning
at 01:30; Saturday's means Sunday morning. Other evenings default to midnight.
The morning reset defaults to 04:00. All seven cutoffs, the morning reset,
and the holiday cutoff are editable in the TV app. Times use the Chromecast's
local timezone and account for daylight-saving changes.

“Tomorrow is a holiday” applies the holiday cutoff (default 01:30) to this night
only. It expires for the following night. Selecting it after midnight applies
to the same night, rather than the next evening.

While Android media sessions report playback, pause, buffering, connecting, or seeking,
arrows remain usable. A three-second grace period bridges brief player/session
handoffs. At idle after the cutoff, arrows are consumed. A proximity unlock
allows unlimited browsing until playback starts and then finishes; it expires
at that end or at the morning reset. Autoplay that continues playing stays
exempt, just like any other active playback. The helper depends on apps reporting
accurate media-session state. Missing media access leaves navigation enabled.
For legacy players with no useful media session (including the tested Jellyfin 0.19.10 video player),
active media audio is an additional playing signal. A pause made using the
physical remote is remembered until audio resumes, Home/Stop is pressed, or
Back is pressed twice. This fallback cannot perfectly distinguish every pause,
long buffering interruption, or player exit initiated from another device.
A service/process restart resets any temporary browsing unlock.

## Permissions and calibration

- Accessibility: filters hardware key events. Screen-content access and gesture
  injection are not requested.
- Nearby devices (`BLUETOOTH_CONNECT`): shares the already-paired remote's BLE
  connection and reads its RSSI. It does not unpair or modify the remote.
- Notification-listener access: Android's supported access path for observing
  other apps' media sessions. This is a broad system permission; our listener
  does not read notification contents, and the app has no network permission.

The night restriction defaults off and cannot block until both Bluetooth
positions have been calibrated. Use the app's two ten-second measurements:
normal seating position, then remote about 10–20 cm from the Chromecast.
At least 8 samples and a 12 dB difference between medians are required.
The midpoint becomes the close threshold. Filtered signal must remain above
it for two seconds, and must drop below the hysteresis threshold before another
approach can unlock again. RSSI is a proximity heuristic, not measured distance.

## Build and install

Requirements: JDK 17 or newer, Android SDK platform 35, build-tools 36.0.0,
and `zip`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) if the SDK is outside
`~/Android/Sdk`. `ANDROID_PLATFORM` and `ANDROID_BUILD_TOOLS` override the
platform/build-tools versions.

Clone and build on your computer:

```sh
git clone https://github.com/floitsch/chromecast-remote-helper.git
cd chromecast-remote-helper
bash build.sh
adb -s DEVICE install -r build/remote-helper.apk
adb -s DEVICE shell am start -n org.floitsch.netflixjellyfin/.MainActivity
```

The build creates a local debug signing key at `build/debug.keystore`.
Keep that key to install future updates over your own build; another signing
key requires uninstalling the previous app first. The key and all generated
artifacts are ignored by Git.

Enable the helper under Android's Accessibility settings and approve Nearby
devices and playback/notification access. Configuration is stored on the
Chromecast. The helper runs without ADB or a computer after setup.
The Netflix mapping uses `KEYCODE_BUTTON_4` (191), verified on the tested remote.
The paired device must be uniquely named `Chromecast Remote`.

For operator setup, with the owner's permission:

```sh
adb -s DEVICE shell pm grant org.floitsch.netflixjellyfin android.permission.BLUETOOTH_CONNECT
adb -s DEVICE shell cmd notification allow_listener org.floitsch.netflixjellyfin/.MediaAccessService
```

The settings app includes a manual test mode that applies the night window
without changing the system clock. It remains active until **Stop test** is
selected. Production scheduling resumes when the test is stopped.

## Diagnostics and tests

Pure Java tests cover the weekly schedule, holiday expiration, midnight,
morning reset, daylight-saving repeats, playback exemption, unlock lifecycle,
signal spikes, hysteresis and stale samples. They do not require Android:

```sh
bash test.sh
```

A receiver protected by Android's system `DUMP` permission supports local ADB
diagnostics. Commands: `status`, `far`, `near`, `enable` (boolean extra `enabled`),
`holiday` (boolean extra `enabled`), `test`, `cancel-test`.

```sh
adb -s DEVICE shell am broadcast -n org.floitsch.netflixjellyfin/.DiagnosticReceiver --es command status
```

Disable the restriction in the helper, disable its Accessibility service, or
uninstall `org.floitsch.netflixjellyfin` to remove it. Disabling/uninstalling
the helper also restores the original Netflix button behavior.

## Verified setup

Tested on Chromecast with Google TV (Android 14) and its Bluetooth voice remote.
The physical Netflix mapping and seat-lock/close-unlock behavior were confirmed.
Live YouTube tests covered playing, pausing longer than the end grace, resuming,
and locking after completion. The policy suite contains 51 checks.
Bluetooth signal strength is affected by position and obstructions; recalibrate
for your own room. Other remotes/device models have not been verified.
