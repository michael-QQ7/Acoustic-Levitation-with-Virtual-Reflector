# Reflector-Assisted SonicSurface — AcousticField3D and DPCAT

This project adapts the open-source [SonicSurface](https://github.com/upnalab/SonicSurface) phased-array platform and [Ultraino / AcousticField3D](https://github.com/asiermarzo/Ultraino) software for a single active ultrasonic array facing a passive reflector.

The software includes a reflected-field model and an experimental **DPCAT** optimizer based on ideas from the published DS-PAT approach. The system has previously been used to levitate, move, and merge liquid droplets.
## Requirements

- Java 11 or later (JDK 11+ to build from source)
- Windows for the provided `.bat` launchers; NetBeans/Ant for source development
- A copy of the project with `dist/` and `jars/` kept in their original relative locations
- For hardware control: an Arduino Mega with compatible `MegaCommandSender` firmware and a SonicSurface-style FPGA controller
- An array model (`.xml.gz`) with correct transducer addresses and phase calibration

## 1. Start AcousticField3D

1. Open the `AcousticFieldSim` folder.
2. Run `START_DPCAT.bat`. `START_Reflector.bat` also starts the modified simulator.
3. Select **File → Load** to open a saved `.xml.gz` array model.
4. To use the optimizer, select **DPCAT → Open DPCAT optimizer**.

To build and run the included software tests from `AcousticFieldSim/`:

```bash
python build_reflector.py --test
```

The project can also be opened in NetBeans by selecting the directory containing `build.xml` and `nbproject/`.

**Check the array before optimizing.** In the **Trans** panel, set a positive amplitude for each physically installed transducer. Some older saved models load with amplitude `0`; DPCAT will then report no active transducers. Keep unused addresses disabled. The example 192-transducer array uses addresses **64–255**; the other 64 addresses are OFF.

## 2. Set up the reflector

1. Open **Reflector** and check **Enable reflection**.
2. Enter a reflector distance in **Gap from array (mm)**; the supplied DPCAT example uses `75` mm.
3. Click **Place below (-Y)** if the plate is below the array. For an array centered at `Y = 0`, a 75 mm gap places the reflector at `Y = -75 mm`.
4. Check **Pressure |R|** and **Reflection phase (deg)** for the model you intend to use. `|R| = 1` and phase `0°` are ideal rigid-reflector assumptions, not measured properties of a physical plate.
5. Click **Refresh Reflector Status** in DPCAT to check the effective plane location and reflection settings.

The reflector uses a single image-source reflection from an idealized infinite plane. Changing the plate position updates the field model but does not automatically re-optimize the transducer phases. The displayed plate dimensions do not set its acoustic extent.

## 3. Connect a controller (Devs → TransControl → Protocol)

Open **Devs → TransControl**, select the protocol for the hardware and firmware actually connected, then click **Connect** and select its serial port. The **Protocol** menu selects a Java communication implementation; it does not automatically detect or reprogram the controller. The baud rate is set when the PC opens the serial port, so disconnect and reconnect after changing protocols or the Java baud-rate constant.

### Protocol comparison

Values below were checked against `src/acousticfield3d/protocols/` and `TransControlPanel.java` in this release. **PC baud** refers to the connection opened by AcousticField3D, not to every UART within the hardware.

| Protocol (GUI) | Connected hardware | PC baud (bps) | Phase divisions | Channels | Data sent from the PC | Use / notes |
| --- | --- | ---: | ---: | --- | --- | --- |
| `MEGA` | Arduino Mega driver boards | 115200 | 10 | 64 per board; multiple boards supported | Packed waveform bitplanes, split into tagged nibbles; duration/commit commands | Original Mega 64-channel-per-board protocol; board number is encoded in the data. |
| `SimpleFPGA` | FPGA connected directly to PC serial | 230400 | 32 | 256 | Binary `254` + 256 phase bytes; optional `251` + 256 amplitude bytes; `253` to switch buffers | Direct FPGA phase control, optional amplitude/multiplex commands. |
| `Nano8` | Arduino Nano driver | 115200 | 24 | 8 | Packed waveform bitplanes, split into nibble commands; `0` switches buffers | Original 8-channel Nano driver. |
| `MEGA_Anim` | Arduino Mega with matching animation firmware | 115200 | 10 | 64 per board; multiple boards supported | Mega-style tagged waveform frames and separate duration commands | Original Mega animation variant; **not** DPCAT playback. |
| `Nano16` | Arduino Nano with matching 16-channel firmware | 115200 | 16 | 16 | Same packed-nibble scheme as `Nano8`, for 16 channels and 16 time slots | Original 16-channel Nano driver. |
| `SimpleFPGA 128` | FPGA with matching 128-step firmware | 1500000 | 128 | **72** in this Java implementation (`64 + 8`) | `SimpleFPGA` binary frame format, but 72 phase/amplitude entries | 128 **phase divisions**, not 128 output channels in the current code. |
| `Chained FPGA` | Multiple chained FPGA boards | 230400 | 32 | 256 per board; board count derived from loaded transducer count | Each board: selection byte `192 + board index` followed by 256 phase bytes; buffer switch is separate | Check board ordering and channel numbers. No fixed number of boards is guaranteed. |
| **`MegaFPGA`** | **PC → Arduino Mega → FPGA** | **250000** | **32** | **256 logical channels; 192 physically fitted in this setup** | **ASCII** `version\n` handshake; `phases=p0,...,p255\n` commands; waits for Mega reply | **Use this for the reflector-assisted lab system and DPCAT playback.** |

`MEGA`, `MEGA_Anim` and `MegaFPGA` are three **different protocols**. Matching their baud rates alone will not make their commands interchangeable. The mapping from a simulated transducer to a physical channel also differs by implementation: the original Mega protocols use driver-pin/board mappings, whereas `MegaFPGA` uses the transducer **Order number** (minus its device offset) as the raw FPGA channel address.

For the included 40 kHz example, one acoustic cycle is **25 µs**. Phase divisions describe the *resolution within that cycle*, not the rate at which new frames are transmitted:

| Phase divisions | Phase step | Equivalent time step at 40 kHz |
| ---: | ---: | ---: |
| 10 | 36° | 2.500 µs |
| 16 | 22.5° | 1.563 µs |
| 24 | 15° | 1.042 µs |
| 32 | 11.25° | 0.781 µs |
| 128 | 2.8125° | 0.195 µs |

These time steps are ideal phase intervals, **not measured switching accuracy**.

### Example：MegaFPGA

Use **`MegaFPGA`**, not `MEGA`, for the Arduino Mega 2560 forwarding commands to the SonicSurface-style FPGA. The Java encoder has **256 logical channels**: phase codes `0–31` represent 32 discrete phases; code **`32` means OFF**. The example apparatus has **192 installed transducers** (addresses `64–255`) and **64 unused addresses**. Check that your array model has the correct `Order number` and phase corrections; unused channels should remain disabled.

```text
AcousticField3D on PC
    |
    | USB–UART bridge; 250000 bps (Java PC_BAUD)
    | ASCII: version\n, phases=p0,...,p255\n
    v
Arduino Mega 2560 — Serial / UART0
    | parse command and forward a binary frame
    | Serial2 / UART2; target 250000 bps (firmware setting)
    v
FPGA UART receiver — must match Serial2 speed
    |
    v
256 logical outputs (192 populated in this apparatus)
```

The Arduino Mega 2560 uses a **USB–UART bridge** for this PC connection; it is not a baud-independent native USB CDC endpoint. Arduino `Serial` (UART0) and `Serial2` (UART2) have **separate settings**. For the documented 250000/250000 configuration, the compatible firmware needs:

```cpp
Serial.begin(250000);    // PC ↔ Mega (USB–UART bridge, UART0)
Serial2.begin(250000);   // Mega TX2 (pin 16) → FPGA UART
```

The Java file `MegaFPGA.java` defines `PC_BAUD = 250000` and opens **only the PC-to-Mega serial port** using that value. It also defines `FPGA_BAUD = 250000` and displays `Mega ready (250000 / 250000)`, but neither declaration configures the physical FPGA receiver or executes `Serial2.begin(...)` on the Arduino. **The supplied project does not include `MegaCommandSender.ino` or the running FPGA receiver configuration, so their current on-device baud rates have not been verified from source.** Check the uploaded Mega sketch and FPGA build before connecting. Also confirm the Mega-to-FPGA voltage-level conversion (typically 5 V to 3.3 V), common ground and TX2-to-FPGA-RX wiring. This link is documented as using `Serial2`, not `Serial1`; verifying any other firmware UART use also requires the actual sketch.

For example, if you change the Java value to `PC_BAUD = 500000` and rebuild, while setting the corresponding Arduino firmware to:

```cpp
Serial.begin(500000);
Serial2.begin(250000);
```

only **PC ↔ Mega** changes to 500000 bps. **Mega ↔ FPGA** stays at 250000 bps. Changing a Java Protocol or constant does not change either Arduino UART setting automatically, and the FPGA UART timing must match the `Serial2` speed.

#### Commands, acknowledgment and playback

The PC sends **text**, one line per command. Example (abbreviated; the real phase command has exactly 256 comma-separated values):

```text
version
phases=0,16,32,...,31
```

The Java implementation waits for a numeric version reply, then sends phase frames and expects the exact Mega response **`phases ok`**. The accompanying `README_MegaFPGA.md` describes the Mega's downstream **binary** FPGA frame as `254, 192, <256 phase bytes>, 253` (start, board select, codes, buffer commit). **That downstream layout is documented but has not been checked against `MegaCommandSender.ino` in this release because the sketch is absent.** The FPGA does not receive the PC's ASCII `phases=` line.

To send one frame:

1. Load the `.xml.gz` array model and check active channels, order numbers, reflector position and calibration.
2. Open **Devs → TransControl**, select **`MegaFPGA`**, click **Connect**, and choose the Mega's COM port.
3. Wait for **`Mega ready (250000 / 250000)`**. This means the Java/Mega version handshake succeeded; it does **not** verify the downstream FPGA link.
4. Click **Send** to transmit the current phases, or use **Send Current Frame** in DPCAT.
5. Look for **`Mega: phases ok`**. This is the Mega's software acknowledgment, **not** an FPGA acknowledgment, verified electrical output, or evidence of a stable acoustic trap.

For DPCAT sequences, use **Play Sequence To Mega / FPGA** in the DPCAT window. It sends one complete frame, waits for the Mega acknowledgment, and only then schedules the next frame. The TransControl **Switch**, **Seq**, **Mult**, **Durations** and push/pull controls are disabled for `MegaFPGA`; `MEGA_Anim` is a separate original protocol and must not be selected as a substitute.

**Three separate quantities:**

- **Baud rate:** the symbol rate of one serial link (250000 bps here for binary UART signalling). It is not the number of phase frames sent per second.
- **Phase divisions:** 32 means 32 possible phase positions within a 40 kHz acoustic cycle, not 32 Hz.
- **Phase update rate:** completed 256-channel patterns per second, limited by frame length, UART framing, parsing, the Mega's acknowledgment, scheduling and FPGA behavior. It must be measured across the complete link.

At 250000 bps using standard **8N1** serial framing, even the *shortest possible* 256-value `phases=` line is **519 bytes**, requiring about **20.8 ms** just to transmit PC → Mega. Typical two-digit codes require more bytes. Thus the current full ASCII-frame format cannot deliver **500 complete patterns/s** at 250000 bps; a 2 ms dwell setting or a 250000-bps baud rate does not establish a 500 Hz update rate. For actual playback timing use **Export Playback Timing CSV** and, when needed, measure the FPGA outputs directly.

**Safe shutdown:** Disconnect and Stop do not guarantee that the last FPGA pattern switches off. Set all transducer amplitudes to `0`, click **Send**, wait for `phases ok`, then disconnect. Use an independent hardware power cutoff for emergencies.

Further details: [MegaFPGA communication](AcousticFieldSim/README_MegaFPGA.md).

## 4. Optimize a field or generate a sequence

Open **DPCAT → Open DPCAT optimizer** after loading the array and enabling the reflector.

For the supplied two-target example:

1. Enter the target center `X`, `Y`, and `Z` in **mm**, or use **Use selected point**.
2. Select **A: Two Point Traps** and **Merge axis: X**.
3. Set **Initial separation** to `14` mm. This places two pressure targets at `X = xc ± 7 mm`.
4. Select the objective function. `C1` matches a complex-pressure reference; `C2` favors high pressure with a uniformity penalty; `C2N` uses a relative uniformity term.
5. Click **Optimize Current** for one frame, or enter the separation list and click **Generate Sequence** for multiple frames.
6. Use **Frame**, **Preview Field**, or **Preview Discrete Sequence** to inspect the result before transmission.

The default separation list is `14,12,10,9.6,9.4,9.2,9.0,8.8` mm. **Generate Sequence reads the separation list**, not just the Initial/Final/Steps boxes. After changing those boxes, click **Build linear separation list** to update the list.

Available modes:

| Mode | Use |
| --- | --- |
| `A: Two Point Traps` | Move two pressure targets toward each other |
| `B: Two Wide Traps` | Move two sampled wide regions toward each other |
| `Single Wide Trap` | Optimize one sampled central region |
| `C: Point -> Wide -> Single` | Approach, widen the sampled regions, and contract to one central target |

These are **acoustic pressure targets**, not confirmed stable droplet positions. Lower objective values and successful software playback do not demonstrate stable trapping or droplet coalescence.

## 5. Play and export

- **Add Sequence To Animation**: store generated frames in the simulator's animation data. Save the full model as `.xml.gz` to retain the sequence.
- **Send Current Frame**: transmit the selected frame over a connected `MegaFPGA` interface.
- **Play Sequence To Mega / FPGA**: send all frames, waiting for `phases ok` after each one.
- **Stop / Cancel**: stop sending subsequent frames; the FPGA may continue outputting the last pattern.
- **Export DPCAT Phase CSV**: export the calibrated 256-channel phase codes and the corresponding optimization data.
- **Export DPCAT Sequence CSV**: export positions, objective values, timing settings, reflection settings, and phase codes for every frame. A `.model.xml.gz` snapshot is written alongside the CSV.
- **Export Playback Timing CSV**: export recorded send/ACK/dwell timing after a successful playback.

**Frame dwell after ACK** is the minimum hold time *after* the Mega reply, not the exact frame period. Serial transmission and controller processing add delay. A 5 ms dwell setting does not imply 200 Hz complete-frame updates.

See [DPCAT user guide](AcousticFieldSim/DPCAT_README.md) for objective functions, phase encoding, mode C parameters, file formats, and example commands.

## Example files

The DPCAT example set is under `AcousticFieldSim/examples/dpcat/`:

- `example_192_DPCAT_75mm.xml.gz` — 192 active addresses (64–255), 75 mm reflector, and stored animations
- `example_A_C1_sequence.csv` — 8 frames, separation 14 → 8.8 mm
- `example_TWO_WIDE_C2_sequence.csv` — 8 frames with two sampled wide regions
- `example_POINT_WIDE_SINGLE_C2_sequence.csv` — 17 frames for approach, widening, and contraction

These examples were computed offline. They are **not** hardware-verified droplet trajectories. Always check array order, geometry, calibration, and reflector height against your own apparatus.

## Known limitations

- The reflected-field model excludes finite-plate diffraction, multiple reflections, finite-droplet scattering, streaming, and droplet deformation.
- Pressure optimization does not by itself enforce gravitational force balance or three-dimensional trap stability.
- The `MegaFPGA` protocol has ON/OFF amplitude control, not independent analog amplitude control per transducer.
- Phase calibration is applied to exported/transmitted hardware codes; do not apply the same correction again in FPGA firmware.
- The bundled JARs (including JOGL, GlueGen, and jSerialComm) have their own distribution terms. Check and include the applicable notices before public release.

## Credits and references

- [UPNA Lab — SonicSurface](https://github.com/upnalab/SonicSurface) (MIT license)
- [Asier Marzo and contributors — Ultraino / AcousticField3D](https://github.com/asiermarzo/Ultraino) (MIT license)
- [Li et al., *Coalescence of multiple pairs of levitated droplets using dual-side phased arrays* (2025)](https://doi.org/10.1016/j.ultsonch.2025.107327) — DS-PAT research reference; DPCAT is an adaptation, not an official implementation.

Keep the original copyright and license notices when redistributing upstream code. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and the project release checklist for additional licensing and publication checks.
