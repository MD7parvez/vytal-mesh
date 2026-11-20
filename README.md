# Vytal Mesh

**Vytal** is an experimental firefighter safety, team-awareness, and indoor-navigation project. It combines a wearable sensor prototype with an Android interface and a planned offline team-location network for environments where GPS may be unavailable or unreliable.

The project is hardware-led: the first prototype demonstrates a phone-linked wearable sensor setup. The longer-term goal is to develop a more capable second prototype with improved sensing, indoor positioning, and firefighter-to-firefighter awareness.

> **Development status:** Prototype 1 has been assembled and tested at a basic level. Several advanced capabilities described below are design goals for Prototype 2 or later; they are not yet validated as a complete operational system.

## 1. Hardware — current prototype

The first prototype is a physical wearable setup built around a smartphone interface and a small sensor/electronics unit.

### Current prototype components

| Component | Role | Status |
|---|---|---|
| Arduino Nano | Reads the connected sensors and prepares readings for transmission | Assembled in Prototype 1 |
| Optical Pulse Sensor | Experimental heart-rate measurement | Connected; readings can be intermittent or stale |
| DHT11 | Measures local temperature and humidity | Connected and tested |
| HC-05 Bluetooth module | Sends sensor data from the microcontroller to the phone | Connected and tested |
| Android smartphone | Arm-mounted user interface for live readings, alerts, and team-related features | Used in Prototype 1 |
| Wearable/hand-unit assembly | Holds the sensing electronics as part of the firefighter-worn concept | Prototype assembly |

The current sensor data format used in the prototype is:

\`\`\`text
DATA|FF1|bpm|temperature|humidity
\`\`\`

\`FF1\` is an example firefighter unit identifier. The phone receives the readings over Bluetooth and presents them in the app.

### Prototype 1 test notes

- Bluetooth communication between the electronics and phone has been tested.
- Temperature and humidity readings have been received by the app (one recorded test showed 27.9 °C and 79% humidity).
- The optical pulse sensor is not yet dependable: it can return no signal, repeat old readings, or show 0 BPM. The app has been adjusted to show **NO SIGNAL** for invalid readings and to mark readings as stale after six seconds.
- These are early prototype results, not certified measurements or evidence of performance in real fireground conditions.

## 2. Hardware direction — Prototype 2

The next stage is intended to move beyond a basic sensor demonstration toward a more integrated firefighter-worn system. Vytal is seeking **pre-seed support to build and test Prototype 2**.

Planned hardware development areas include:

- **More reliable vital-sign sensing:** improve the heart-rate sensing arrangement and the quality of readings during movement and wearable use.
- **Environmental hazard sensing:** investigate suitable sensors for oxygen level and hazardous gases, alongside temperature monitoring. Sensor selection, placement, calibration, and protective packaging still need engineering validation.
- **Inertial sensing:** use motion and orientation data to help estimate movement and heading when GPS is unavailable.
- **Distance/ranging sensing:** measure nearby walls or obstacles to provide additional spatial information for indoor positioning.
- **Thermal imaging:** investigate a thermal sensor/camera to help identify heat patterns and support situational awareness in low-visibility environments.
- **Wearable integration:** develop a more robust arm/hand-worn arrangement with practical mounting, wiring, power management, and clear access to controls.
- **Team identification:** give each firefighter a distinct unit identity so their estimated positions, status, and movement trails can be represented separately.

These are development targets, not claims that all sensors or functions are already installed in the current hardware.

## 3. Indoor mapping and GPS-denied positioning

A major Vytal goal is to help a team understand where firefighters are inside a building when ordinary GPS positioning is unavailable.

The proposed approach combines:

- A known starting point or entrance reference when one is available.
- Inertial movement and heading estimates.
- Distance measurements to nearby walls or obstacles.
- A 3D representation of the surrounding indoor space.
- Separate firefighter identifiers and trails, so the system can distinguish team members and show their estimated movement over time.

**Current status:** indoor 3D mapping and GPS-denied localization are still research and development work. An early mapping experiment used a video source and AI-assisted reconstruction; it was not a physical, real-time scan performed by the wearable prototype. Offline, faster reconstruction and reliable on-device positioning remain goals for future development. Position estimates will need uncertainty handling and extensive real-world testing.

## 4. Software and Android app

This repository contains the Android software side of Vytal Mesh. The app is intended to provide the interface for firefighter units and team coordination.

### Current software milestone (0.1.0)

- Role selection: Commander / Firefighter.
- Unit ID.
- Nearby Connections advertising and discovery with the P2P Cluster strategy.
- Direct peer connection and JSON text packets.
- Preset and custom test messages.
- GitHub Actions workflow that builds a debug APK and runs local unit tests.

The broader prototype app work has also explored a live sensor dashboard, firefighter profiles, threshold alerts, audible alarms, test mode, and GPS/location viewing. Availability of those features may differ from the current code in this repository; the repository milestone below describes what is included in this codebase.

### Mesh and team-awareness direction

The intended software capabilities include reliable message delivery, firefighter status sharing, SOS alerts, team tracking, and a commander view. The current Nearby Connections milestone is a starting point, not a complete resilient mesh network.

## 5. Build the Android app

The GitHub Actions workflow runs on pushes and pull requests to \`main\`. Open **Actions → Android CI** and download the \`vytal-mesh-debug-apk\` artifact from a successful run.

To build locally, install JDK 17 and Android SDK platform 35, then run:

\`\`\`bash
gradle assembleDebug
gradle testDebugUnitTest
\`\`\`

## 6. Two-phone software smoke test

1. Install the debug APK on two Android phones that have Google Play services.
2. Enable Bluetooth and Wi-Fi on both devices; grant the nearby-device permissions.
3. Open the app on both phones and choose distinct unit IDs.
4. Tap **START OFFLINE MESH** on each phone.
5. When the peer count indicates a connection, send a test message from either phone.

Nearby Connections may use Bluetooth and Wi-Fi radios to discover and connect devices. This initial milestone does not need an internet connection for peer-to-peer transport, but Google Play services must be available on the phone.

## 7. Development roadmap

### Hardware
- Improve the wearable enclosure, mounting, wiring, and power system.
- Improve the reliability of heart-rate and environmental measurements.
- Evaluate oxygen and hazardous-gas sensing.
- Integrate inertial and ranging sensors for indoor spatial estimation.
- Prototype thermal imaging integration.
- Conduct controlled bench tests before any field-oriented evaluation.

### Software and system integration
- Connect the wearable sensor stream cleanly to the Android app.
- Reliable packet routing: duplicate suppression, TTL forwarding, acknowledgements, retries, and SOS repeat/acknowledgement.
- Foreground service and reconnection lifecycle.
- GPS base-point setup and entrance alignment.
- Sensor-based step detection, heading estimation, indoor dead reckoning, floor estimation, and breadcrumb storage.
- Team status, uncertainty display, man-down workflow, calls, and push-to-talk audio.
- Commander dashboard and floor-aware team view.
- Offline mapping and faster 3D reconstruction.
- More unit tests and physical-device testing.

## 8. Prototype 2 funding objective

Vytal is seeking pre-seed support to turn the first proof-of-concept wearable into a more integrated second prototype. Funding would support hardware iteration, improved sensors, indoor-positioning experiments, wearable integration, and testing. The exact bill of materials and performance targets will be refined during engineering.

## 9. Safety and limitations

**Vytal is an experimental research prototype, not a certified life-safety system. It must not be used as a substitute for approved firefighter communications, PASS alarms, SCBA equipment, gas detectors, thermal-imaging equipment, incident-command procedures, or emergency protocols.**

The current software milestone does not provide guaranteed message delivery, complete multi-hop forwarding, or guaranteed emergency alert delivery. The current wearable sensors are prototype components and are not certified for firefighter protection. Indoor dead reckoning and 3D-based positioning can accumulate significant error and require calibration, uncertainty reporting, and extensive controlled testing before any operational use.
