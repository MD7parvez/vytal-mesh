/*
 * Vytal FF1 — Arduino Nano + HC-05
 * Sensors: analog pulse sensor on A0, DHT11 on D2.
 * Buttons: SOS D3, HELP D4, GATHER D5 (button to GND).
 * HC-05: module TX -> Nano D10; module RX <- Nano D11 through a voltage divider.
 * HC-05 UART: 9600 baud.
 *
 * Output protocol:
 * DATA|FF1|bpm|temperatureC|humidityPercent
 * BUTTON|FF1|SOS / HELP / GATHER
 *
 * Prototype telemetry only. Not a certified medical or firefighter safety device.
 */
#include <SoftwareSerial.h>
#include <DHT.h>

#define DHT_PIN 2
#define DHT_TYPE DHT11
#define PULSE_PIN A0
#define BTN_SOS 3
#define BTN_HELP 4
#define BTN_GATHER 5
#define BT_RX 10
#define BT_TX 11

SoftwareSerial hc05(BT_RX, BT_TX);
DHT dht(DHT_PIN, DHT_TYPE);

struct ButtonState {
  uint8_t pin;
  const char *name;
  bool stable;
  bool lastRaw;
  unsigned long changedAt;
};

ButtonState buttons[] = {
  {BTN_SOS, "SOS", HIGH, HIGH, 0},
  {BTN_HELP, "HELP", HIGH, HIGH, 0},
  {BTN_GATHER, "GATHER", HIGH, HIGH, 0}
};

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

void updateButtons() {
  const unsigned long now = millis();
  for (unsigned int i = 0; i < sizeof(buttons) / sizeof(buttons[0]); i++) {
    ButtonState &b = buttons[i];
    const bool rawState = digitalRead(b.pin);
    if (rawState != b.lastRaw) {
      b.lastRaw = rawState;
      b.changedAt = now;
    }
    if ((now - b.changedAt) > 35 && rawState != b.stable) {
      b.stable = rawState;
      if (b.stable == LOW) {
        hc05.print("BUTTON|FF1|");
        hc05.println(b.name);
      }
    }
  }
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
  pinMode(BTN_SOS, INPUT_PULLUP);
  pinMode(BTN_HELP, INPUT_PULLUP);
  pinMode(BTN_GATHER, INPUT_PULLUP);
  Serial.begin(115200);
  hc05.begin(9600);
  dht.begin();
  pulseBaseline = analogRead(PULSE_PIN);
  Serial.println("Vytal FF1 Nano ready; HC-05 SPP 9600 baud");
}

void loop() {
  samplePulse();
  updateButtons();
  updateDht();
  sendFrame();
}
