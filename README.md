# Reflector-Assisted SonicSurface — AcousticField3D and DPCAT

This project adapts the open-source [SonicSurface](https://github.com/upnalab/SonicSurface) phased-array platform and [Ultraino / AcousticField3D](https://github.com/asiermarzo/Ultraino) software for a single active ultrasonic array facing a passive reflector.

The software includes a reflected-field model, a PC–Arduino Mega–FPGA communication interface, and an experimental **DPCAT** optimizer based on ideas from the published DS-PAT approach. The system has previously been used to levitate, move, and merge liquid droplets with earlier control settings. **The DPCAT-generated sequences have not yet been validated with physical droplets.**

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

## 3. Select the serial protocol

Open the **Devs / TransControl** panel and choose the protocol that matches your controller. The baud rates below come from the corresponding Java protocol classes in this version.

| Protocol | Baud rate (bps) | Source |
| --- | ---: | --- |
| `MEGA` | 115200 | `ArduinoMEGA64.java` |
| `SimpleFPGA` | 230400 | `SimpleFPGA.java` |
| `Nano8` | 115200 | `ArduinoNano.java` |
| `MEGA_Anim` | 115200 | `ArduinoMEGA64_Anim.java` |
| `Nano16` | 115200 | `ArduinoNano16.java` |
| `SimpleFPGA_128` | 1500000 | `SimpleFPGA_128.java` |
| `Chained FPGA` | 230400 | `ChainedFPGA.java` (extends `SimpleFPGA`) |
| **`MegaFPGA`** | **250000** | **`MegaFPGA.java`** |

**For the PC → Arduino Mega → FPGA setup described here, select `MegaFPGA`, not `MEGA`.** `MEGA` refers to a separate 115200-bps driver protocol. The DPCAT hardware playback controls require the `MegaFPGA` connection.

With the supplied Mega interface, the firmware should use:

```cpp
Serial.begin(250000);   // PC USB -> Arduino Mega
Serial2.begin(250000);  // Arduino Mega TX2 -> FPGA
```

The `MegaFPGA` Java class sets the **PC-to-Mega** link to 250000 bps. The **Mega-to-FPGA** rate is set in the Arduino firmware and must match the FPGA receiver; changing the GUI protocol does not reprogram either device. Check the existing level shifting and common-ground wiring before connecting.

To send a pattern:

1. Select **MegaFPGA**, click **Connect**, and choose the correct COM port.
2. Wait for **`Mega ready (250000 / 250000)`**.
3. Set or optimize the active transducer phases and click **Send**, or use **Send Current Frame** in DPCAT.
4. Check for **`Mega: phases ok`**. This confirms that the Arduino processed the command; it is **not** an FPGA acknowledgment or a measurement of the acoustic field.

The PC sends one ASCII line containing `phases=p0,p1,...,p255`. Each channel has a code from `0` to `31` (phase), or `32` (OFF). Missing and disabled channels are sent as OFF. The Arduino forwards the pattern to the FPGA using its existing firmware protocol. No new FPGA serial command format is introduced by DPCAT.

**Stopping playback or disconnecting does not switch off the last transmitted pattern.** To shut the array down through this interface, first set all amplitudes to `0`, click **Send**, wait for the acknowledgment, and then disconnect. Use a separate physical emergency shutdown arrangement for the experimental apparatus.

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

- DPCAT performs discrete phase optimization and sequence export; no hardware or droplet-coalescence verification of the DPCAT-generated sequences is claimed.
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
