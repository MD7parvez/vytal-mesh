# Vytal FF1 / FF2 MCU firmware

These sketches implement the serial format expected by the Vytal Android node app.

## Shared serial protocol (HC-05 at 9600 baud)

- `DATA|FF1|bpm|temperatureC|humidityPercent`
- `BUTTON|FF1|SOS`
- `BUTTON|FF1|HELP`
- `BUTTON|FF1|GATHER`

FF2 uses `FF2` in place of `FF1`. Each firefighter phone pairs with only its own HC-05. The phones forward telemetry over Wi-Fi to the separate STATION-role phone.

## Libraries

Install **DHT sensor library by Adafruit** and its dependency **Adafruit Unified Sensor** using Arduino IDE Library Manager. The pulse signal is sampled directly; no PulseSensor library is required.

## FF1 — Arduino Nano

| Part | Pin |
|---|---|
| Pulse sensor signal | A0 |
| DHT11 data | D2 |
| SOS button | D3 to GND |
| HELP button | D4 to GND |
| GATHER button | D5 to GND |
| HC-05 TXD | Nano D10 (SoftwareSerial RX) |
| HC-05 RXD | Nano D11 (SoftwareSerial TX, use a resistor divider) |
| HC-05 VCC / GND | Supply appropriate to your breakout; common GND |

All three buttons use internal pull-ups, so wire each button between its pin and GND. Use a resistor divider on Nano D11 -> HC-05 RXD because Nano logic is 5V and HC-05 RX input is 3.3V logic.

## FF2 — ESP8266 NodeMCU

| Part | Pin |
|---|---|
| Pulse sensor signal | A0 |
| DHT11 data | D5 |
| SOS button | D1 to GND |
| HELP button | D2 to GND |
| GATHER button | D0 to GND, with external 10k pull-up to 3.3V |
| HC-05 TXD | D7 (SoftwareSerial RX) |
| HC-05 RXD | D6 (SoftwareSerial TX) |
| HC-05 GND | NodeMCU GND |

NodeMCU GPIO is 3.3V logic. Confirm the exact development board's A0 input range before connecting an analog sensor; board variants differ. Do not apply 5V to ESP8266 GPIO.

## Important limitations

The pulse calculation here is a simple demonstration peak detector, not a validated heart-rate algorithm. DHT11 measures ambient temperature/humidity, not core body temperature or oxygen. A displayed value of 0 or -1 means no valid pulse / sensor sample. This prototype must not be used for operational firefighter decisions or medical monitoring.
