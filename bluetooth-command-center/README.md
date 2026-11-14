# Vytal Command Center 1.0

Android hybrid dashboard for two independent HC-05 Bluetooth Classic SPP links. The phone receives newline-delimited serial data and performs basic phone-side parsing, freshness tracking, event detection, and presentation.

## Build
Open this directory in Android Studio, or use the GitHub Actions workflow at `.github/workflows/vytal-command-center.yml`. Download the `vytal-command-center-debug-apk` artifact after a successful run.

## Pairing and use
1. Pair both HC-05 modules from Android Settings → Bluetooth.
2. Install and open the app.
3. Grant Nearby/Bluetooth permission.
4. Load paired devices, then assign one module to FF1 and the other to FF2.
5. The app keeps each SPP socket open while its foreground service runs and retries dropped links with backoff.

## Serial data
The parser intentionally does not impose a definitive sensor schema. It recognizes JSON objects and simple delimited triples (HR, temperature, humidity), but the actual Nano message format must be confirmed before relying on field mapping. Each frame must end with a newline.

Outbound commands are intentionally not enabled until the Nano command protocol is specified. The app currently receives and monitors data.

## Reliability
Two simultaneous HC-05 connections depend on the phone's Bluetooth chipset and Android Bluetooth stack. Test both links on the target phone. Android may still stop services under aggressive battery management; exempt the app from battery optimization if the phone permits it.

## Safety
This is an experimental development prototype, not certified life-safety equipment. It is not suitable for operational firefighting or emergency decision-making.
