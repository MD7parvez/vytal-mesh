# Vytal Mesh

Offline-first firefighter team communication and indoor-tracking research prototype for Android.

## Current milestone (0.1.0)

- Role selection: Commander / Firefighter.
- Unit ID.
- Nearby Connections advertising and discovery with the P2P Cluster strategy.
- Direct peer connection and JSON text packets.
- Preset and custom test messages.
- GitHub Actions workflow that builds a debug APK and runs local unit tests.

## Build

The GitHub Actions workflow runs on pushes and pull requests to `main`. Open **Actions → Android CI** and download the `vytal-mesh-debug-apk` artifact from a successful run.

To build locally, install JDK 17 and Android SDK platform 35, then run:

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

## Two-phone smoke test

1. Install the debug APK on two Android phones that have Google Play services.
2. Enable Bluetooth and Wi-Fi on both devices; grant the nearby-device permissions.
3. Open the app on both phones. Choose distinct unit IDs.
4. Tap **START OFFLINE MESH** on each phone.
5. When the peer count indicates a connection, send a test message from either phone.

Nearby Connections may use Bluetooth and Wi-Fi radios to discover and connect devices. This initial milestone does not need an internet connection for peer-to-peer transport, but Google Play services must be available on the phone.

## Roadmap

- Reliable packet routing: duplicate suppression, TTL forwarding, acknowledgements, retries, and SOS repeat/acknowledgement.
- Foreground service and reconnection lifecycle.
- GPS base-point setup and entrance alignment.
- Sensor-based step detection, heading estimation, indoor dead reckoning, floor estimation, and breadcrumb storage.
- Team status, uncertainty display, man-down workflow, calls, and push-to-talk audio.
- Commander dashboard and floor-aware team view.
- More unit tests and physical-device testing.

## Safety and limitations

**This is an experimental prototype, not a certified life-safety system. Do not use it as a substitute for approved firefighter communications, PASS alarms, SCBA equipment, incident-command procedures, or emergency protocols.** Nearby connections can fail; the current version has no delivery acknowledgements, multi-hop forwarding, background service, or guaranteed emergency alert delivery. Indoor dead reckoning will require calibration and extensive testing, and position uncertainty can grow significantly.
