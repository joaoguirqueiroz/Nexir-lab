/* NEXIR LAB ESP32 Bluetooth Classic IR bridge
 * Board: original ESP32 DevKit / ESP32-WROOM (Classic SPP required).
 * Library: IRremoteESP8266 by crankyoldgit (IRrecv, IRsend, IRutils)
 * RX sensor 38kHz OUT -> GPIO 15; VCC -> 3V3; GND -> GND.
 * TX GPIO 4 -> appropriate resistor/transistor driver -> IR LED, not directly
 * driving a high-power LED from a GPIO pin.
 * Pair in Android Settings with name NEXIR-BRIDGE.
 */
#include <Arduino.h>
#include <BluetoothSerial.h>
#include <IRremoteESP8266.h>
#include <IRrecv.h>
#include <IRsend.h>
#include <IRutils.h>

constexpr uint16_t RX_PIN = 15;
constexpr uint16_t TX_PIN = 4;
constexpr uint16_t CAPTURE_BUFFER = 512;
constexpr uint16_t MAX_TIMINGS = 500;
BluetoothSerial SerialBT;
IRrecv irrecv(RX_PIN, CAPTURE_BUFFER, 50, true);
IRsend irsend(TX_PIN);
decode_results results;
String lineBuffer;
bool captureArmed = false;

void sendError(const char *msg) { SerialBT.print("ERR|"); SerialBT.println(msg); }

void handleTx(String line) {
  // Format TX|38000|9000,4500,560,560...
  int secondPipe = line.indexOf('|', 3);
  if (secondPipe <= 3) { sendError("FORMAT"); return; }
  int hz = line.substring(3, secondPipe).toInt();
  if (hz < 20000 || hz > 60000) { sendError("FREQUENCY"); return; }
  String csv = line.substring(secondPipe + 1);
  uint16_t timings[MAX_TIMINGS];
  uint16_t count = 0;
  unsigned long total = 0;
  int start = 0;
  while (start < csv.length()) {
    if (count >= MAX_TIMINGS) { sendError("TOO_MANY_PULSES"); return; }
    int next = csv.indexOf(',', start);
    if (next < 0) next = csv.length();
    String token = csv.substring(start, next);
    if (token.isEmpty() || token.length() > 6) { sendError("TIMING"); return; }
    for (int j = 0; j < token.length(); j++) {
      if (token[j] < '0' || token[j] > '9') { sendError("TIMING"); return; }
    }
    unsigned long duration = token.toInt();
    if (duration == 0 || duration > 65535) { sendError("DURATION"); return; }
    total += duration;
    if (total >= 2000000UL) { sendError("FRAME_TOO_LONG"); return; }
    timings[count++] = (uint16_t)duration;
    start = next + 1;
  }
  if (count == 0) { sendError("EMPTY"); return; }
  irsend.sendRaw(timings, count, hz / 1000);
  SerialBT.printf("OK|TX|%u|%lu\n", count, total);
}

void handleCommand(String line) {
  line.trim();
  if (line == "PING") SerialBT.println("OK|PONG");
  else if (line == "CAPTURE") { captureArmed = true; SerialBT.println("OK|CAPTURE_ARMED"); }
  else if (line == "CANCEL") { captureArmed = false; SerialBT.println("OK|CAPTURE_CANCELLED"); }
  else if (line.startsWith("TX|")) handleTx(line);
  else sendError("UNKNOWN_COMMAND");
}

void setup() {
  Serial.begin(115200);
  SerialBT.begin("NEXIR-BRIDGE");
  irrecv.enableIRIn();
  irsend.begin();
  lineBuffer.reserve(4000);
}

void loop() {
  while (SerialBT.available()) {
    char c = (char)SerialBT.read();
    if (c == '\n') { handleCommand(lineBuffer); lineBuffer = ""; }
    else if (c != '\r') {
      if (lineBuffer.length() >= 3900) { lineBuffer = ""; sendError("LINE_TOO_LONG"); }
      else lineBuffer += c;
    }
  }
  if (captureArmed && irrecv.decode(&results)) {
    captureArmed = false;
    // Raw tick is normally 2 us. Item 0 is leading gap; item 1 is first mark.
    // Demodulating receiver does not measure carrier; nominal 38 kHz is used.
    const uint16_t count = min((uint16_t)(results.rawlen - 1), (uint16_t)MAX_TIMINGS);
    if (results.rawlen < 3) { sendError("CAPTURE_EMPTY"); }
    else {
      SerialBT.print("{\"type\":\"ir\",\"protocol\":\"RAW\",\"frequency\":38000,\"order\":\"MSB\",\"pattern\":[");
      for (uint16_t i = 1; i <= count; i++) {
        if (i > 1) SerialBT.print(',');
        // Sensor/rawbuf clocks are tick counts, not nanoseconds.
        SerialBT.print((unsigned long)results.rawbuf[i] * kRawTick);
      }
      SerialBT.println("]}");
      SerialBT.println("OK|CAPTURE_DONE|CARRIER_ASSUMED_38KHZ");
    }
    irrecv.resume();
  }
  delay(2);
}
