# KomugiAttacker

Android 8.0+ BLE link-pressure rig. Two engines, one Material 3 surface, no setup wizard.

The app pushes Bluetooth connection traffic at whatever is in radio range: a rotating
advertisement stream that pops accessory pairing dialogs, or a multi-worker connect flood
aimed at one picked device.

## What it does

**Broadcast mode** — cycles BLE advertisement payloads through the controller as fast as it
accepts them. Payload families:

| Family | Company ID | Result on a nearby receiver |
|---|---|---|
| Apple proximity | `0x004C` | AirPods / Beats / PowerBeats pairing popups |
| Apple TV setup | `0x004C` | Apple TV setup and account transfer prompts |
| Microsoft Swift Pair | `0x0006` | Windows "new device" toast with a spoofed name |
| Custom hex | any | Paste your own manufacturer payload |

"Cycle every family" interleaves all three Apple and Swift Pair sets in one shuffled stream.
Interval is tunable from 20 ms to 2000 ms.

**Connect flood** — up to 16 workers, each alternating between channel-level RFCOMM connects
and LE GATT connects against the selected target. Every link is aborted as soon as it lands or
times out, so the remote stack keeps receiving fresh inbound requests. With no target picked,
the flood follows anything the scanner discovers.

## Target selection

The scan sheet lists every LE device the phone hears, sorted by signal strength, with name,
MAC and RSSI. A picked target is stored and reused across launches. "Broadcast" clears the
selection so the flood follows the scanner instead.

## UI

- Material 3 dark theme, canvas `#222222`
- Google Sans throughout, bundled in `app/src/main/res/font`
- Radial pulse badge, live packet/failure/uptime counters
- Bounded live log with monospace rendering
- Foreground service with a persistent notification and a Stop action

## Requirements

- Android 8.0 (API 26) or newer
- Bluetooth LE advertising support (almost every phone since 2015)
- Runtime permissions: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
  (Android 12+), `ACCESS_FINE_LOCATION` (Android 8–11)

## Build

```bash
git clone https://github.com/<owner>/KomugiAttacker.git
cd KomugiAttacker
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Release builds read signing material from `keystore.properties` in the repo root:

```properties
storeFile=komugi-release.jks
storePassword=...
keyAlias=komugi
keyPassword=...
```

Without that file `assembleRelease` still produces an unsigned APK.

Toolchain: AGP 8.5.2, Gradle 8.9, Kotlin 2.0.20, compileSdk 34, JDK 17.

## Project layout

```
app/src/main/java/com/komugi/attacker/
├── MainActivity.kt          permission + adapter gate, Compose host
├── MainViewModel.kt         config, target list, service control
├── ble/
│   ├── AdvertiseEngine.kt   advertisement rotation loop
│   ├── ConnectFlooder.kt    RFCOMM + GATT worker pool
│   ├── Payloads.kt          Apple / Swift Pair / custom payloads
│   ├── SpamService.kt       foreground service, wake lock, notification
│   ├── SpamState.kt         process-wide engine state and log
│   └── TargetScanner.kt     LE scan and target cache
└── ui/
    ├── MainScreen.kt        Compose UI
    └── theme/               Material 3 palette and Google Sans typography
```

## Notes

- Advertisement length is capped by the legacy 31-byte budget: 3 bytes flags + 4 bytes
  manufacturer header + 24 bytes payload. Longer custom hex is rejected in the UI.
- Samsung and Fast Pair popups are reachable through the custom hex field; the built-in
  families cover Apple and Microsoft stacks.
- The controller throttles aggressive advertising. The engine backs off and rebinds the
  advertiser after 25 consecutive failures instead of spinning.

## License

MIT. Google Sans ships under the SIL Open Font License, see `fonts/OFL.txt`.
