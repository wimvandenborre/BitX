# 🎛️ BitX – Command-Based Live Control System for Bitwig

**BitX** is a “ClyphX-style” command system for **Bitwig Studio**.  
You control your project by naming **clips** and **cue markers** with special commands like:

```text
()BPM 122:8 ()LIR Bass1:2 ()MPCXD 134 ()RSND
```

BitX reads those names, parses the commands, and manipulates Bitwig:
BPM, sends, instruments, FX racks, note filters, external synths via Program Change, OSC, and more.
Launching or quantized-retriggering an already-playing command clip executes its commands again.

BitX requires **Bitwig Controller API 25**.

---


## 🚀 Installation

1. **Download** https://personalaudio.lemonsqueezy.com/
2. **Create** a 'PerSonal' folder inside the Bitwig Studio Extensions folder and places the scripts inside of it
3. **Restart Bitwig**, then go to  
   **Settings → Controllers → Add Controller → Per-Sonal → BitX**  

> 💡 **Optional DisplayApp for macOS and Windows:**
> Place the packaged display beside `BitX.bwextension` in the `PerSonal`
> extension folder. On Windows, copy the complete `BitXDisplayApp` directory;
> on macOS, copy `BitXDisplayApp.app`. The Windows package includes its own Java
> runtime. macOS might block the signed app the first time; go to
> **System Settings → Security & Privacy** and click **Allow**.

You don’t have to compile the project — just drop in the compiled extension.

---

## 🧠 How it works

BitX watches **Clip Launcher clips** and **Arranger cue markers**.

Any name that contains commands starting with `()` is parsed.

You can chain multiple commands in one clip name:

```text
()BPM 120 ()SMW Hello World ()SPN Starting set! ()MPCXD 210
```

✅ Spaces between commands are allowed  
✅ Commands can have arguments (e.g. `120:8`, `Bass 1:3`, `C2:G5`)

**Command groups:**

| Group | Description |
|-------|--------------|
| 🎚 Transport & timing | BPM, time signature |
| 🎛 Track utilities | Sends, channel/note filters |
| 🎹 Bitwig devices | Drum Machine, Instrument Selector, FX Selector |
| 🎼 External synths | MIDI Program Change, Korg XD helper |
| 💬 UI & OSC | Popups, display window, OSC out |
| 🎯 Navigation | Jump by track and clip name |

---

## ⚙️ Preferences

In Bitwig’s **Controller Preferences**, you’ll find:

| Setting | Description | Default |
|----------|--------------|----------|
| Number of tracks | Visible tracks in the TrackBank | 32 |
| Number of scenes | Scenes per TrackBank | 128 |
| Number of layers | Layers per selector | 32 |
| Number of sends | Sends per track | 4 |
| Display Window | Show/hide external JavaFX window | Off |
| TouchDesigner Host / Port | Dedicated TouchDesigner OSC target | 127.0.0.1 : 8000 |
| Launchpad Host / Port | Dedicated Launchpad OSC target | 127.0.0.1 : 8001 |
| Custom 1 Name / Host / Port | First user-defined OSC target | Custom 1 / 127.0.0.1 : 8002 |
| Custom 2 Name / Host / Port | Second user-defined OSC target | Custom 2 / 127.0.0.1 : 8003 |
| OSC Cursor Remotes Output | Which OSC output receives cursor remotes | TouchDesigner |
| Support me | Opens support link ❤️ | – |

---

## 🎛 OSC outputs and Cursor Remotes

BitX can emit the currently active **Remote Controls** page (selected device / track remote / project remote) so external apps can bind controls in sync with Bitwig.

### Setup

1. In **Preferences**, configure and enable the dedicated **OSC - TouchDesigner** and **OSC - Launchpad** targets as needed.
2. Configure **OSC - Custom 1** and **OSC - Custom 2** for any additional applications. Their names, hosts, and ports are user-editable.
3. Under **OSC - Routing**, pick **Bitx Cursor Remotes Output** to choose which of the four targets receives cursor-remotes data. **OSC Default Output** controls OSC commands that omit a target.
4. Restart the controller after changing output settings.

Every port is selectable from 1 through 65535. OSC commands can address outputs by their configured name or by the aliases `touchdesigner`, `launchpad`, `custom1`, and `custom2`.

### Incoming (optional, from Torso T-1)

BitX listens for:

```
/torsot1script/cc <channel> <cc> <value> <targetType> <targetTrack> <targetPage> <label>
```

This message updates the display and can trigger a cursor‑remotes snapshot, but the output also updates when the active Remote Controls page or values change.

### Outgoing (from BitX)

BitX sends:

```
/bitx/cursorremotes/page <pageName>
/bitx/cursorremotes/knob1 <value> <name>
/bitx/cursorremotes/knob2 <value> <name>
...
/bitx/cursorremotes/knob8 <value> <name>
```

Notes:
- `value` is normalized 0.0..1.0.
- If a slot has no remote, `name` is `"0"` and `value` is `0.0`.

---

## 🧩 Command Reference

### 1. 🎚 Transport & Timing

#### `()BPM`
Set or transition BPM.

```text
()BPM 124        → sets tempo instantly to 124 BPM
()BPM 124:8      → transitions to 124 BPM over 8 bars
```

**Format:**
```text
()BPM <targetBPM>[:<bars>]
```

#### `()STS`
Set time signature.

```text
()STS 3:4
()STS 7:8
```

**Format:**
```text
()STS <numerator>:<denominator>
```

| Numerator | 1–32 |
|------------|------|
| Denominator | 1, 2, 4, 8, 16, 32 |

---

### 2. 🎛 Bitwig Device Utilities (Per Track)

#### `()SCF`
**Set Channel Filter** allowed MIDI channels.

```text
()SCF 1:5:9
```

- Uses the first *Channel Filter* device on the track.
- Disables all channels, then enables 1, 5, 9.

**Format:**
```text
()SCF <ch1>:<ch2>:<ch3>...
```

Channels: 1–16

#### `()SNF`
**Set Note Filter** key range.

```text
()SNF D1:E5
()SNF C-2:G8
```

**Format:**
```text
()SNF <minNote>:<maxNote>
```

#### `()SNT`
**Set Note Transpose.**

```text
()SNT 2
()SNT -1:12
()SNT 1:7:-25
```

**Format:**
```text
()SNT <octave>[:<coarse>[:<fine>]]
```

| Parameter | Range | Notes |
|------------|--------|-------|
| Octave | -3 … +3 | |
| Coarse | -48 … +48 | semitones |
| Fine | -100 … +100 | mapped to -200%..+200% |

---

### 3. 🎹 Instrument & FX Selector (Layer-Based)

#### `()LIR`
**Select Instrument layer by name.**

```text
()LIR Bass 1
()LIR Keyscape Pad:2
```

**Format:**
```text
()LIR <layerName>[:<remotePageIndex>]
```

#### `()LFR`
**Select FX layer by name.**

```text
()LFR Reverb Long
()LFR CrunchDelay:3
```

**Format:**
```text
()LFR <fxLayerName>[:<remotePageIndex>]
```

#### `()LDR`
**Load Drum Rack preset.**

```text
()LDR 909_Kicks
()LDR IDM_Drums_01
```

**Format:**
```text
()LDR <presetName>
```

---

### 4. 🎚 Sends & Mix

#### `()RSND`
**Reset all sends** on all visible tracks.

```text
()RSND
```

**Format:**
```text
()RSND
```

---

### 5. 🎼 External Synths (MIDI Program Change)

#### `()MPC`
**Generic MIDI Program Change.**

```text
()MPC 10:1:5:2
()MPC *:*:3:*
()MPC 42:::15
```

| Arg | Range | Description |
|------|--------|-------------|
| Program | 1–128 | Converted to 0–127 |
| Bank MSB | 1–128 | Converted to 0–127 |
| Bank LSB | 1–128 | Converted to 0–127 |
| Channel | 1–16 | Converted to 0–15 |

✅ Use `*` or leave empty to skip that parameter.

#### `()MPCXD`
**Korg Minilogue XD slot selector (1–500).**

```text
()MPCXD 1
()MPCXD 137
()MPCXD 500
```

**Format:**
```text
()MPCXD <slot>
```

---

### 6. 💬 UI & Feedback

#### `()SMW`
**Show message** in the external Display Window.

```text
()SMW Hello World
```

#### `()SPN`
**Show Bitwig popup notification.**

```text
()SPN Drop coming up!
```

#### `()OSC`
**Send OSC message.**

```text
()OSC /bitx/section 1 intro
()OSC /lights/scene 3
()OSC /fx/reverb 0.5
```

---

### 7. 🎯 Navigation & Jumping

#### `()JUMPTO`
**Jump to track & clip by name.**

```text
()JUMPTO Bassline:Verse A
```

---

## 🔗 Chaining Commands – Examples

```text
()BPM 124:8 ()SMW Warming up ()SPN Transitioning tempo
()LIR Bass 1:2 ()SNF D1:E5 ()SNT 1:7
()MPCXD 210 ()SCF 1:3:5 ()SPN XD slot 210
()LFR Big Reverb:1 ()RSND ()OSC /lights/scene 5
```

---

## 🧠 Device UUID Reference

| Device | UUID | Description |
|---------|------|-------------|
| Instrument Selector | `9588fbcf-721a-438b-8555-97e4231f7d2c` | Controls instrument layers |
| FX Selector | `956e396b-07c5-4430-a58d-8dcfc316522a` | Controls FX layers |
| Channel Filter | `c5a1bb2d-a589-4fda-b3cf-911cfd6297be` | Enables/disables MIDI channels |
| Note Filter | `ef7559c8-49ae-4657-95be-11abb896c969` | Sets note range |
| Note Transpose | `0815cd9e-3a31-4429-a268-dabd952a3b68` | Shifts notes |
| MIDI Program Change (MPC) | `429c7dcb-6863-48bc-becc-508463841e3b` | External synth control |

---

## 💡 Credits

Developed by **Creating Spaces**  
🎶 Musician • 🧠 Developer • 💫 Space Creator

Support at https://personalaudio.lemonsqueezy.com/ 💖
