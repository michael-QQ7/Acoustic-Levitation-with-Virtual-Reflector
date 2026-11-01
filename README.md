# Acoustic Levitation with a Single Phased Array and Reflector

This is my modified version of [SonicSurface](https://github.com/upnalab/SonicSurface) and [Ultraino / AcousticField3D](https://github.com/asiermarzo/Ultraino). I used it to levitate, move, and merge ethanol droplets with a 40 kHz ultrasonic phased array and a passive reflector. I added a reflector model to AcousticField3D, a DPCAT interface for generating coalescence sequences.

You can watch the process in this video: https://youtube.com/shorts/17up5Glu6to?si=qhz9mWbb4V7As6MR
## 1. Purpose and Background

I started this work to assist with Dr. Aadil's laboratory research. The goal was to reproduce an acoustic levitation system and then use it to bring two suspended droplets together.

At first I considered the usual two-sided array. But having a powered PCB underneath the droplets seemed risky: if a droplet fell, it could land directly on the lower board. I ended up using one active array above the droplets and putting a reflector underneath.

I couldn't find an AcousticField3D setup that supported both an adjustable reflector and the kind of trap sequence I needed for coalescence, so I modified the software. For the coalescence optimizer, I referred to the DS-PAT approach in [Li et al. (2025)](https://doi.org/10.1016/j.ultsonch.2025.107327). Their paper reports coalescence of up to six droplet pairs using optimized acoustic traps. My DPCAT implementation draws on those ideas, but not their original code. I also used [Watanabe et al. (2018)](https://doi.org/10.1038/s41598-018-28451-5) as a reference for working with a single array and reflector.

## 2. Hardware and Communication

The PC runs the modified AcousticField3D. An Arduino Mega 2560 receives commands over USB serial and forwards phase data to the FPGA and then ICs, which drive the transducers.

```text
PC (AcousticField3D)
    |
    | USB serial / ASCII commands
    v
Arduino Mega 2560
    |
    | Serial2 / binary phase data
    v
FPGA
    |
    v
Shift registers and driver ICs
    |
    v
40 kHz ultrasonic phased array
```

The original controller has 256 logical channel addresses. For convenience, my array has only 192 transducers installed (addresses `64–255`). The phase codes run from `0` to `31` for 32 phase steps; `32` is the OFF code.

### UART settings

The baud rate between the PC and Mega is separate from the one between the Mega and FPGA. The configuration I used was:

```cpp
Serial.begin(250000);     // PC -> Mega
Serial2.begin(250000);    // Mega -> FPGA
```

I also tested `152000` and `500000` on `Serial2`, but neither was reliable in my setup. That doesn't mean those rates are generally unsupported; it is just what I observed with my hardware and firmware.

One detail worth checking when setting this up again: the `MegaCommandSender.ino` in the accompanying archive still has `Serial.begin(115200)` and `Serial2.begin(250000)`. If you're using the 250000/250000 configuration, change the first value and upload the sketch again. The Java `MegaFPGA` protocol must also use the same baud rate as `Serial` on the Mega.

Changing the protocol or baud-rate constant in Java does not change the Arduino or FPGA firmware. If you change the PC-side baud rate, rebuild the Java application and reconnect the serial port. For example, you could run `Serial.begin(500000)` while leaving `Serial2.begin(250000)`; in that case only the PC–Mega link changes, and the Java `PC_BAUD` setting must be updated to match.

The `MegaFPGA.java` file sets `PC_BAUD = 250000` for the PC port. Its separate `FPGA_BAUD` constant is not a command that changes the Mega's `Serial2` or the FPGA UART. The Mega's USB port also uses a USB–UART bridge, so the PC baud rate still matters.

The FPGA's UART receiver needs to match `Serial2`. For a UART clock of 10.24 MHz and a target baud rate of 250000, I used: g_CLKS_PER_BIT = 10,240,000 / 250,000 = 40.96 ≈ 41

Check the actual clock feeding your FPGA UART before copying the divider. I changed the clock-divider parameter in the existing FPGA project rather than writing a new UART design.

### Connecting from AcousticField3D

1. Open `Devs → TransControl`.
2. Choose `MegaFPGA`, not `MEGA` or `MEGA_Anim`.
3. Connect to the Arduino Mega's COM port.
4. After the version handshake, use `Send` or `Send Current Frame` in DPCAT.
5. The Mega should reply with `phases ok`.

On a successful connection, the Java interface shows `Mega ready (250000 / 250000)` after the version handshake. This confirms communication with the Mega, not with the FPGA. The PC first sends `version\n` and expects a numeric reply.

The PC sends a line of 256 phase values in the form `phases=p0,p1,...,p255\n`. The Mega converts this into a binary frame for the FPGA:

```text
254 | 192 | 256 phase bytes | 253
start  board  phase codes       commit
```

For sequence playback, use `Play Sequence To Mega / FPGA` in DPCAT. It sends a frame, waits for `phases ok`, and then schedules the next one. The original TransControl `Switch`, `Seq`, `Mult`, `Durations`, and push/pull options aren't used by this protocol.

`phases ok` comes from the Mega, not the FPGA, so if the array doesn't respond, I would check the FPGA output with an oscilloscope rather than rely on the serial reply alone.

The logic and driver power supplies are separate. Check the common ground, the FPGA input-voltage limits, and any 5 V-to-3.3 V level shifting before connecting the boards. Keep the powered electronics protected from falling droplets.

## 3. Phase Calibration

Getting the array calibrated made a big difference to the stability of the acoustic trap. Each transducer has its own phase offset, so using the same nominal phase command across channels is not enough.

Instead of storing phases to fpga as Marzo did, I saved my calibrated phases in an `.xml.gz` file. This stores the transducer layout, order numbers, and phase corrections.

There are two fields that shouldn't be mixed up:

- `phaseCorrection` stores the calibration offset for each transducer.
- `phase` stores the current phase, which changes when I focus the field or generate a new DPCAT frame.

When importing optimized phases, update `phase` without overwriting `phaseCorrection`. Also avoid applying the same correction again in the FPGA firmware.

For a saved model, I usually check the following before trying to levitate anything:

1. Open the `.xml.gz` file through `File → Load`.
2. Check the transducer positions and `Order numbers`. For this board, only channels `64–255` are populated.
3. In `Trans`, make sure installed transducers have nonzero amplitude. Some older files open with amplitude set to zero.
4. Open `Reflector`, enable reflection, and enter `Gap from array (mm)`.
5. Since my array is above the reflector, choose `Place upward (+Y)`. With the array at `Y = 0` and a 75 mm gap, the modeled reflector is at `Y = +75 mm`. The minimum value of compensation is 0.1 mm not 0.
6. Check `Pressure |R|` and `Reflection phase (deg)`, refresh the reflector status in DPCAT, and optimize again if the plate height or target position has changed.

The position and reflection parameters change the simulated acoustic field, but moving the reflector in the GUI does not recalculate the phases by itself. Run the optimizer again after making a change. The displayed plate dimensions are visual only; they don't limit the acoustic reflection surface in this model.

The reflector model uses one image-source reflection from an idealized infinite plane. `|R| = 1` and `0°` reflection phase are ideal reflector settings, not values I measured from the real plate. The model doesn't reproduce the edges of the physical reflector.

## 4. Droplet Levitation and Coalescence

### Running the software

For the supplied Windows build, keep `dist/` and `jars/` in their original locations and use Java 11 or newer.

Open `AcousticFieldSim/` and run either `START_DPCAT.bat` or `START_Reflector.bat`. If you want to work on the Java source, open the folder containing `build.xml` and `nbproject/` in NetBeans instead.

Load your calibrated `.xml.gz` file, set the reflector position, and open `DPCAT → Open DPCAT optimizer`.

There is also a build/test script:

```bash
cd AcousticFieldSim
python build_reflector.py --test
```

### Generating the merging sequence

My usual workflow in DPCAT is:

1. Set the center position (`X`, `Y`, `Z`, in mm) or use a selected point in the simulator.
2. Select a mode, merging axis, and objective function. For a basic two-droplet approach, start with `C: Point -> Wide -> Single` and `Merge axis: Z`.
3. Set the initial and final separation. Click `Build linear separation list`, or edit the list yourself.
4. Use `Optimize Current` to inspect a single frame, then `Generate Sequence` for the full set.
5. Check the frames with `Preview Field` or `Preview Discrete Sequence`.
6. Click `Add Sequence To Animation`, then save the model. You can send individual frames or play the whole sequence after connecting `MegaFPGA`.

For the two-point example, an initial separation of 14 mm puts the pressure targets at `X = xc - 7 mm` and `X = xc + 7 mm`. `C1` matches a complex-pressure reference; `C2` favors pressure magnitude with a uniformity penalty; and `C2N` uses a relative uniformity term. Those are pressure-field targets, not guaranteed droplet equilibrium points.

`Generate Sequence` reads the separation list itself. If you change the initial/final separation boxes without rebuilding the list, it will still use the old values.

The available modes are:

| Mode | What it does |
| --- | --- |
| `A: Two Point Traps` | Brings two pressure targets together. |
| `B: Two Wide Traps` | Optimizes two wider sampled regions. |
| `Single Wide Trap` | Optimizes one central region. |
| `C: Point -> Wide -> Single` | Moves from two points to wider regions, then one central region. |

The `C1`, `C2`, and `C2N` objectives use different pressure-matching and uniformity terms. A good numerical objective value does not necessarily mean that the droplets will remain stable in the real setup.

In my experiments, the final frames sometimes made the droplets less stable. If that happens, try removing the last one or two frames rather than forcing the traps all the way together. Adjusting the driver voltage can help with excessive droplet deformation, but lowering it too much can make the droplets fall. I found it more useful to change one thing at a time and watch what happened.

### Saving and exporting

- `Add Sequence To Animation` saves the generated frames into the model's animation data. Save the `.xml.gz` file afterward.
- `Export DPCAT Phase CSV` saves the selected frame's calibrated phase values.
- `Export DPCAT Sequence CSV` saves the full sequence, including phases and target positions, with a `.model.xml.gz` snapshot.
- `Export Playback Timing CSV` saves the recorded send, acknowledgment, and dwell times.
- `Play Sequence To Mega / FPGA` sends each frame and waits for `phases ok` before continuing.

For a playback run, `Export Playback Timing CSV` is useful for checking the timings that the Java side recorded. It still isn't a direct measurement of when the FPGA outputs actually changed.

`Frame dwell after ACK` starts after the Mega has acknowledged a frame; it isn't the total time between two FPGA patterns. Stopping playback may also leave the last phase pattern active on the FPGA, so turn the outputs off before disconnecting.

There are more details in the [DPCAT user guide](AcousticFieldSim/DPCAT_README.md).

## 5. 500 Hz Frame Switching Solution

Watanabe et al. (2018) used 500 Hz time-multiplexed focusing to switch quickly between two focal patterns rather than generate both traps at once.

The current serial format makes the limitation clear. At 250000 baud with 8N1 framing, the shortest complete ASCII `phases=` command is 519 bytes, or at least 20.8 ms from PC to Mega. Sending the 259-byte binary frame from Mega to FPGA adds about 10.4 ms. This is before the processing overhead, so setting a 2 ms dwell in the GUI won't give 500 Hz output switching.

My proposed solution is to calculate the phase frames in advance, store them on the FPGA, and use an internal timer to switch between patterns at 500 Hz. The main drawback is that changing the stored sequence may require modifying and reprogramming the FPGA firmware, which takes extra time.

Another thing to consider is the initial levitation stage. The first phase pattern should be held long enough for the droplets to become stably levitated before starting the rapid switching sequence. Once both droplets are stable, the FPGA can begin switching between the precomputed patterns for manipulation and coalescence.

## 6.Notes and Limitations
### Protocol notes

The protocols also differ in how simulated transducers map to physical channels. The original `MEGA` protocol uses a driver-board/pin mapping; `MegaFPGA` maps the transducer `Order number` (minus any device offset) to its FPGA channel address. Check those order numbers if a focus appears in the wrong place.


The modified control path uses `MegaFPGA`. The other options in `Devs → TransControl` use different packet formats, so changing the baud rate alone won't make them interchangeable.

| Protocol | PC baud | Notes |
| --- | ---: | --- |
| `MEGA` | 115200 | Original Mega driver-board waveform/bitplane protocol, 64 channels per board; 10 phase divisions. |
| `MEGA_Anim` | 115200 | Mega-style animation frames and duration commands; not DPCAT playback. |
| `SimpleFPGA` | 230400 | Direct PC–FPGA binary protocol; 256 channels, 32 phase divisions. Uses start byte `254`, phase bytes and commit `253`; optional amplitude frames start with `251`. |
| `MegaFPGA` | 250000 | PC–Mega ASCII commands, then Mega–FPGA binary frames; 256 logical channels, 32 phase divisions. |
| `Nano8` / `Nano16` | 115200 | Original packed-nibble Nano protocols for 8 / 16 channels, with 24 / 16 phase divisions respectively. |
| `SimpleFPGA 128` | 1500000 | 128 phase divisions, but only 72 channels (`64 + 8`) in this Java implementation—not 128 channels. |
| `Chained FPGA` | 230400 | Multiple boards, 256 channels per board; selects boards using `192 + board index`. |

At 40 kHz, the acoustic period is 25 µs. Here is how the phase divisions used by these protocols compare:

| Phase divisions | Phase step | Time step at 40 kHz |
| ---: | ---: | ---: |
| 10 | 36° | 2.500 µs |
| 16 | 22.5° | 1.563 µs |
| 24 | 15° | 1.042 µs |
| 32 | 11.25° | 0.781 µs |
| 128 | 2.8125° | 0.195 µs |

These are ideal phase increments within an acoustic cycle, not measured switching accuracy or the animation frame rate.

## Credits and References

This work depends heavily on the original open-source projects. Thanks to the authors for making their hardware and software available:

- [SonicSurface — UPNA Lab](https://github.com/upnalab/SonicSurface) ([build guide on Instructables](https://www.instructables.com/SonicSurface-Phased-array-for-Levitation-Mid-air-T/))
- [Ultraino / AcousticField3D — Asier Marzo and contributors](https://github.com/asiermarzo/Ultraino)

The original `SonicSurface` and `Ultraino / AcousticField3D` repositories are both MIT-licensed; the bundled Java libraries can have separate terms.

The coalescence and switching ideas came from these papers:

- Li, J., Goddard, N. J., Songsaeng, R., & Gupta, R. (2025). [*Coalescence of multiple pairs of levitated droplets using dual-side phased arrays.*](https://doi.org/10.1016/j.ultsonch.2025.107327) *Ultrasonics Sonochemistry, 116*, 107327.
- Watanabe, A., Hasegawa, K., & Abe, Y. (2018). [*Contactless Fluid Manipulation in Air: Droplet Coalescence and Active Mixing by Acoustic Levitation.*](https://doi.org/10.1038/s41598-018-28451-5) *Scientific Reports, 8*, 10221.

Keep the upstream copyright and license notices with any redistributed code. See `THIRD_PARTY_NOTICES.md` where included.
