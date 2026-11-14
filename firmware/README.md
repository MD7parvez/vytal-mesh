# Vytal FF1 / FF2 MCU firmware

Both sketches send sensor telemetry to their own firefighter phone over HC-05 Bluetooth Classic SPP. **There are no MCU buttons**: SOS, HELP and GATHER are operated from the phone app and sent over Wi-Fi to the command-station phone.

## Serial protocol (HC-05 at 9600 baud)

- FF1: `DATA|FF1|bpm|temperatureC|humidityPercent`
- FF2: `DATA|FF2|bpm|temperatureC|humidityPercent`

Each MCU sends one line per second. Each firefighter phone pairs with only its own HC-05 and forwards telemetry to the separate STATION-role phone.

## Libraries

Install **DHT sensor library by Adafruit** and its dependency **Adafruit Unified Sensor** using Arduino IDE Library Manager. Pulse sampling is implemented directly; no PulseSensor library is required.

## FF1 — Arduino Nano

| Part | Pin |
|---|---|
| Pulse sensor signal | A0 |
| DHT11 data | D2 |
| HC-05 TXD | Nano D10 (SoftwareSerial RX) |
| HC-05 RXD | Nano D11 (SoftwareSerial TX, use a resistor divider) |
| HC-05 VCC / GND | Supply appropriate to your breakout; common GND |

Use a resistor divider on Nano D11 -> HC-05 RXD because Nano logic is 5V and HC-05 RX input is 3.3V logic.

## FF2 — ESP8266 NodeMCU

| Part | Pin |
|---|---|
| Pulse sensor signal | A0 |
| DHT11 data | D5 |
| HC-05 TXD | D7 (SoftwareSerial RX) |
| HC-05 RXD | D6 (SoftwareSerial TX) |
| HC-05 GND | NodeMCU GND |

NodeMCU GPIO is 3.3V logic. Confirm the exact board's A0 input range before connecting an analog sensor; board variants differ. Do not apply 5V to ESP8266 GPIO.

## Important limitations

The pulse calculation is a simple demonstration peak detector, not a validated heart-rate algorithm. DHT11 measures ambient temperature/humidity, not core body temperature or oxygen. A displayed value of 0 or -1 means no valid pulse / sensor sample. This prototype must not be used for operational firefighter decisions or medical monitoring.
