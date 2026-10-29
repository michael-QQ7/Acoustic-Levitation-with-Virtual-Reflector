/*
 * MegaCommandSenderv2.ino - Arduino Mega 2560 (ATmega16U2 USB-to-serial bridge)
 *
 * SonicSurface single-board phase modulation controller
 * ============================================================
 * Hardware connections:
 *   Mega Serial2 (TX2/D16 -> FPGA RX, RX2/D17 <- FPGA TX)
 *   WARNING: Mega uses 5 V logic, while the Cyclone IV E FPGA uses 3.3 V logic.
 *            Proper level shifting is required (e.g., a suitable 5 V-to-3.3 V
 *            translator or a correctly designed resistor divider on Mega TX).
 *   Mega Serial (USB) <-> PC (virtual serial port via ATmega16U2)
 *
 * Baud rates:
 *   WARNING: At 16 MHz, the Mega cannot generate 230400 baud precisely
 *            (its actual rate would be approximately 250000, an 8.5% error).
 *            This sketch therefore uses 250000 baud (nominally 0% error).
 *   The FPGA UART receiver must be configured for the same rate before use.
 *   In the referenced design, g_CLKS_PER_BIT was proposed to change from
 *   45 to 41 in the UARTReader instance of QuadrupleBuffer.bdf, assuming
 *   an approximately 10.24 MHz UART clock (10.24 MHz / 41 ~= 249.8 kbaud).
 *   Verify the actual FPGA clock and UART timing; changing firmware parameters
 *   requires recompiling and reprogramming the FPGA separately.
 *
 * PC -> Mega commands (ASCII text, terminated by a newline):
 *   focus=x,y,z             Single focal point (meters, array-centered)
 *   focusMulti=x1,y1,z1,... Multiple focal points (up to MAX_POINTS=4)
 *   phases=0,5,2,10,...     Raw phase codes for 256 emitters (0-31; 32=OFF)
 *   on / off                Enable all emitters / disable all emitters
 *   switch                  Recommit the current buffer
 *   version                 Report firmware version
 *
 * Mega Serial2 -> FPGA frame format (unchanged from the original protocol):
 *   [254 start] [192 board 0] [256 phase-code bytes] [253 commit]
 * ============================================================
 */

#include "EmitterPos.h"
#include "FocusAlg.h"
#include "PointsMov.h"

#define MEGA_VERSION "1.1"

byte buff[N_EMITTERS];   // Phase buffer (256 bytes)

/* ---------- Frame transmission ---------- */
void sendBuffer() {
  Serial2.write( 254 );         // Start a new phase frame
  Serial2.write( 192 + 0 );     // Select board 0
  Serial2.write( buff, N_EMITTERS );
  Serial2.write( 253 );         // Commit the phase frame
  Serial2.flush();
}

void switchOnArray(boolean on) {
  for (int j = 0; j < N_EMITTERS; j += 1)
    buff[j] = on ? 1 : 32;      // Phase code 32 disables an emitter
  sendBuffer();
}

/* ---------- Parse a list of floating-point values: "x,y,z,x,y,z,..." ---------- */
int parseFloats(const String& s, int startIndex, float target[], int maxNumbers) {
  int index = 0;
  int i = startIndex;
  while (i < (int)s.length() && index < maxNumbers) {
    // Skip non-numeric characters
    while (i < (int)s.length() && !isdigit(s[i]) && s[i] != '-' && s[i] != '.')
      i++;
    if (i >= (int)s.length()) break;
    // Parse one number
    float number = 0;
    boolean neg = false;
    if (s[i] == '-') { neg = true; i++; }
    while (i < (int)s.length() && isdigit(s[i])) {
      number = number * 10 + (s[i] - '0');
      i++;
    }
    if (i < (int)s.length() && s[i] == '.') {
      i++;
      float frac = 0.1;
      while (i < (int)s.length() && isdigit(s[i])) {
        number += frac * (s[i] - '0');
        frac /= 10;
        i++;
      }
    }
    target[index++] = neg ? -number : number;
  }
  return index;
}

/* ---------- Command dispatch ---------- */
void processCommand(String command) {
  command.trim();

  if (command.startsWith("focus=")) {
    float p[3];
    const int n = parseFloats(command, 6, p, 3);
    if (n == 3) {
      simpleFocusAt(p, buff);
      sendBuffer();
      Serial.println("focus ok");
    }

  } else if (command.startsWith("focusMulti=")) {
    float pts[MAX_POINTS * 3];
    const int n = parseFloats(command, 11, pts, MAX_POINTS * 3);
    if (n >= 3 && n % 3 == 0) {
      multiFocusAt(n / 3, pts, buff);
      sendBuffer();
      Serial.println("focusMulti ok");
    }

  } else if (command.startsWith("phases=")) {
    // Parse 256 raw phase-code bytes
    int idx = 0;
    int i = 7;
    while (i < (int)command.length() && idx < N_EMITTERS) {
      int val = 0;
      while (i < (int)command.length() && isdigit(command[i])) {
        val = val * 10 + (command[i] - '0');
        i++;
      }
      if (val > 31) val = 32;   // Accept codes 0-31; values above 31 mean OFF
      buff[idx++] = (byte)val;
      if (i < (int)command.length() && command[i] == ',') i++;
      else break;
    }
    sendBuffer();
    Serial.println("phases ok");

  } else if (command == "on") {
    switchOnArray(true);
    Serial.println("on");

  } else if (command == "off") {
    switchOnArray(false);
    Serial.println("off");

  } else if (command == "switch") {
    Serial2.write( 253 );
    Serial.println("switch");

  } else if (command == "version") {
    Serial.println(MEGA_VERSION);

  } else if (command.length() > 0) {
    Serial.println("unknown cmd");
  }
}


/* ---------- Initialization ---------- */
void setup() {
  Serial.begin(250000);           // PC-facing serial port over USB (virtual COM port)
  Serial2.begin(250000);          // FPGA UART: 250000 baud (FPGA receiver must match; see header)

  // On startup, enable the FPGA phase clock and disable the array by default
  Serial2.write( 151 );           // Command 151 enables the FPGA phase clock
  delay(10);
  switchOnArray(false);
  Serial.println("MegaCommandSender ready");
}

void loop() {
  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    processCommand(command);
  }
}
