# Nearby Phones (Android)

An Android app that scans for phones near you using Bluetooth and shows a rough distance to each one.

## How it works

Phones don't broadcast "I am a phone", so the app combines several signals:

| Signal | What it tells us | Confidence |
| --- | --- | --- |
| Our own BLE service UUID | Another phone running this app | Confirmed |
| Classic Bluetooth device class = *Phone* | A discoverable phone | Likely |
| Apple Continuity "Nearby Info" message | An iPhone (or other Apple device) | Likely |
| Google Nearby / Quick Share service data | An Android phone | Likely |
| Device name (iPhone, Galaxy, Pixel, …) | Probably a phone | Likely |
| Samsung manufacturer data, Apple Handoff | Maybe a phone | Possible |

AirPods, watches, Fast Pair accessories, iBeacons and similar are recognised and hidden when **Phones only** is on.

Distance is estimated from signal strength (RSSI) with a log-distance path-loss model. Bluetooth signal strength is noisy and depends on walls, pockets and bodies, so read it as "near vs. far", not as an exact measurement.

## Limitations

- Phones are only seen if their Bluetooth is on. Most phones advertise over BLE intermittently, and many use randomised addresses that change every ~15 minutes, so the same phone may appear twice over time.
- Classic Bluetooth discovery only finds phones that are *discoverable* (usually only while their Bluetooth settings screen is open).
- Two phones that both run this app always find each other.
- On Android 11 and lower, location services must be on for Bluetooth scanning to return results.

## Build

Requires the Android SDK (API 35) and JDK 17.

```sh
cd nearby-phone-detector
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the `nearby-phone-detector` folder in Android Studio and press Run.

Every push builds a debug APK on GitHub Actions; download it from the **nearby-phones-debug-apk** artifact on the workflow run.

## Permissions

- Android 12+: Nearby devices (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`) and location.
- Android 8–11: location (required by Android for Bluetooth scanning).

The app scans only while it is open and stops when you leave it.
