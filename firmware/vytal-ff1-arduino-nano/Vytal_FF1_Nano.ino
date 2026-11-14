/*
 * Vytal FF1 — Arduino Nano + HC-05
 * Sensors: analog pulse sensor on A0, DHT11 on D2.
 * HC-05: module TX -> Nano D10; module RX <- Nano D11 through a voltage divider.
 * HC-05 UART: 9600 baud.
 *
 * Output: DATA|FF1|bpm|temperatureC|humidityPercent
 * No MCU buttons; SOS / HELP / GATHER are controlled from the firefighter phone app.
 *
 * Prototype telemetry only. Not a certified medical or firefighter safety device.
 */
#include <SoftwareSerial.h>
#include <DHT.h>

#define DHT_PIN 2
#define DHT_TYPE DHT11
#define PULSE_PIN A0
#define BT_RX 10
#define BT_TX 11

SoftwareSerial hc05(BT_RX, BT_TX);
DHT dht(DHT_PIN, DHT_TYPE);

float pulseBaseline = 512.0f;
int bpm = 0;
bool pulseHigh = false;
unsigned long lastPulseSample = 0;
unsigned long lastBeatAt = 0;
unsigned long lastDhtRead = 0;
unsigned long lastFrameAt = 0;
float temperatureC = -1.0f;
float humidityPct = -1.0f;

void samplePulse() {
  const unsigned long now = millis();
  if (now - lastPulseSample < 10) return;
  lastPulseSample = now;
  const int sample = analogRead(PULSE_PIN);
  pulseBaseline += 0.01f * ((float)sample - pulseBaseline);
  const float amplitude = (float)sample - pulseBaseline;
  const float threshold = 28.0f;
  if (amplitude > threshold && !pulseHigh && (now - lastBeatAt) > 300) {
    pulseHigh = true;
    if (lastBeatAt != 0) {
      const unsigned long interval = now - lastBeatAt;
      if (interval >= 300 && interval <= 2000) {
        const int measured = (int)(60000UL / interval);
        if (measured >= 30 && measured <= 200) bpm = measured;
      }
    }
    lastBeatAt = now;
  }
  if (amplitude < threshold * 0.4f) pulseHigh = false;
  if (lastBeatAt == 0 || now - lastBeatAt > 2500) bpm = 0;
}

void updateDht() {
  if (millis() - lastDhtRead < 2000) return;
  lastDhtRead = millis();
  const float h = dht.readHumidity();
  const float t = dht.readTemperature();
  humidityPct = (!isnan(h) && h >= 0 && h <= 100) ? h : -1.0f;
  temperatureC = (!isnan(t) && t >= -20 && t <= 80) ? t : -1.0f;
}

void sendFrame() {
  if (millis() - lastFrameAt < 1000) return;
  lastFrameAt = millis();
  hc05.print("DATA|FF1|");
  hc05.print(bpm);
  hc05.print("|");
  hc05.print(temperatureC, 1);
  hc05.print("|");
  hc05.println(humidityPct, 1);
}

void setup() {
  Serial.begin(115200);
  hc05.begin(9600);
  dht.begin();
  pulseBaseline = analogRead(PULSE_PIN);
  Serial.println("Vytal FF1 Nano ready; HC-05 SPP 9600 baud");
}

void loop() {
  samplePulse();
  updateDht();
  sendFrame();
}
