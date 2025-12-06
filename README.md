BitX – Command-Based Live Control System for Bitwig

BitX is a “ClyphX-style” command system for Bitwig Studio.
You control your project by naming clips and cue markers with special commands like:

()BPM 122:8 ()LIR Bass1:2 ()MPCXD 134 ()RSND


BitX reads those names, parses the commands, and manipulates Bitwig:
BPM, sends, instruments, FX racks, note filters, external synths via Program Change, OSC, etc.

Installation

Download the built BitX extension (the .bwextension or packaged folder).

Place it in your Bitwig Studio/Extensions folder.

Restart Bitwig and enable the extension in:
Settings → Controllers → Add controller → BitX (or however you named it).

(Optional) On macOS Silicon:
The external display app is signed, but if macOS blocks it, go to
System Settings → Security & Privacy and allow it.

You don’t have to build the project yourself to use it; just drop in the compiled extension.

How it works (concept)

BitX watches Clip Launcher clips and Arranger cue markers.

Any name that contains commands starting with () is parsed.

You can chain multiple commands in one clip name:

()BPM 120 ()SMW Hello World ()SPN Starting set! ()MPCXD 210


Spaces between commands are allowed.

Commands can have arguments (e.g. 120:8, Bass 1:3, C2:G5, etc).

Commands are grouped like this:

🎚 Transport & timing (BPM, time signature)

🎛 Track utilities (sends reset, channel/note utilities)

🎹 Bitwig devices (Drum Machine, Instrument Selector, FX Selector)

🎼 External synths (MIDI Program Change, Minilogue XD helper)

💬 UI & OSC (messages, popups, OSC out)

🎯 Navigation (jump by track/clip name)



Preferences / Settings

In the BitX controller script preferences you’ll find:

Number of tracks (default 32)

Number of scenes (default 128)

Number of layers (default 32)

Number of sends (default 4)


Display Window: enable/disable the JavaFX visual window

OSC Send IP / Port (default 127.0.0.1 : 8000)

Support BitX on Patreon button

Command Reference
1. Transport & Timing
   ()BPM

Set or transition BPM

Instant change

()BPM 124


Sets Bitwig’s tempo directly to 124 BPM.

Smooth transition over N bars

()BPM 124:8


Gradually moves from current BPM to 124 BPM over 8 bars
(taking time signature into account).

Format:

()BPM <targetBPM>[:<bars>]

()STS

Set time signature

()STS 3:4
()STS 7:8


Format:

()STS <numerator>:<denominator>


Numerator: 1–32

Denominator: 1, 2, 4, 8, 16, 32

2. Bitwig Device Utilities (Per Track)

These run on the track where the clip is playing (for clip commands),
or on the track index provided by cue markers (you’re already wiring that up).

()SCF

Set Channel Filter device’s allowed MIDI channels

()SCF 1:5:9


Uses the first Channel Filter on the track.

Disables all channels, then enables 1, 5, 9.

Format:

()SCF <ch1>:<ch2>:<ch3>...


Channels are 1–16.

()SNF

Set Note Filter key range

()SNF D1:E5
()SNF C-2:G8


Uses the first Note Filter on the track.

Converts note names (C0, D#3, etc.) to MIDI notes and sets MIN/ MAX.

Format:

()SNF <minNote>:<maxNote>

()SNT

Set Note Transpose

()SNT 2
()SNT -1:12
()SNT 1:7:-25


Uses the first Note Transpose on the track.

Values are validated and mapped to the device’s normalized ranges.

Format:

()SNT <octave>[:<coarse>[:<fine>]]


octave: -3 … +3

coarse (semitones): -48 … +48

fine (% range): -100 … +100 (mapped to device’s -200%..+200%)

3. Instrument & FX Selector (Layer-Based)

These work by naming layers inside Bitwig’s Instrument Selector or FX Selector.

()LIR

Select Instrument layer by name & optional Remote Controls page

()LIR Bass 1
()LIR Keyscape Pad:2


Uses the first Instrument Selector device that matches the UUID on the track.

Finds the layer by its name (e.g. “Bass 1”, “Keyscape Pad”).

Activates that layer via its ChainSelector.

Selects the device inside that layer and optionally switches its Remote Controls page.

Format:

()LIR <layerName>[:<remotePageIndex>]


Pages are 1-based in the command: :2 selects page index 1 internally.

()LFR

Select FX layer by name & optional Remote Controls page

()LFR Reverb Long
()LFR CrunchDelay:3


Uses the first FX Selector device (FX rack) that matches your FX UUID.

Same behavior as LIR but for FX chains:

Selects FX chain by layer name

Switches Remote Controls page if provided.

Format:

()LFR <fxLayerName>[:<remotePageIndex>]

()LDR

Load Drum Rack preset into track

()LDR 909_Kicks
()LDR IDM_Drums_01


Loads a drum rack preset from:

~/Documents/Bitwig Studio/Library/Presets/Drum Machine/<presetname>.bwpreset


Uses a fixed device slot on the track (second device in the device bank in your code) and replaces it via replaceDeviceInsertionPoint().

Format:

()LDR <presetName>

4. Sends & Mix
   ()RSND

Reset all sends on all visible tracks

()RSND


Iterates through all tracks in the TrackBank.

For each Send in each track’s SendBank, calls:

send.setImmediately(0.0);


Effectively mutes all sends (0.0).

Format:

()RSND

5. External Synths (MIDI Program Change)

BitX assumes a custom MIDI Program Change device with parameters:

PROGRAM

BANK_MSB

BANK_LSB

CHANNEL

stored per track in mpcParameters.

()MPC

Generic MIDI Program Change helper

()MPC 10:1:5:2
()MPC *:*:3:*
()MPC 42:::15


PROGRAM, BANK_MSB, BANK_LSB expected as 1–128, internally converted to 0–127 (by subtracting 1).

CHANNEL expected as 1–16, internally converted to 0–15.

Arguments can be * or empty to leave unchanged.

Format:

()MPC <program>[:<MSB>[:<LSB>[:<channel>]]]


Examples:

()MPC 10 → just set program 10, don’t touch banks or channel

()MPC *:*:3:12 → only set LSB=3 and channel 12

()MPC 64:1:2:* → program 64, bank MSB 1, bank LSB 2, keep existing channel

()MPCXD

Korg Minilogue XD memory slot selector

()MPCXD 1
()MPCXD 137
()MPCXD 500


The XD has 500 slots (001–500) but MIDI PC is only 0–127.
KORG uses Bank 1 + Sub Bank + PC to address them.

BitX does the math for you:

Slot 1–100 → SubBank 1, PC 0–99

Slot 101–200 → SubBank 2, PC 0–99

Slot 201–300 → SubBank 3, PC 0–99

Slot 301–400 → SubBank 4, PC 0–99

Slot 401–500 → SubBank 5, PC 0–99

Internally we compute:

zeroBased = slot - 1;        // 0..499
subBank   = zeroBased / 100 + 1; // 1..5
pc        = zeroBased % 100;     // 0..99
program   = pc + 1;              // 1..100 (for MPC setter)
bankMsb   = 1;
bankLsb   = subBank;


Then it calls:

MPC <program>:<bankMsb>:<bankLsb>:*


so it preserves the existing MIDI channel.

Format:

()MPCXD <slot>


slot: 1–500

6. UI & Feedback
   ()SMW

Show message in external Display Window (JavaFX)

()SMW Hello World
()SMW Section A – Breakdown


Sends the text to the BitX display app via a local TCP socket.

Useful for on-screen cues, lyrics, performance notes, etc.

Format:

()SMW <message>

()SPN

Show Bitwig popup notification

()SPN Drop coming up!
()SPN Switching to second act


Displays a small popup inside Bitwig that auto-hides.

Format:

()SPN <message>

()OSC

Send an OSC message

()OSC /bitx/section 1 intro
()OSC /lights/scene 3
()OSC /fx/reverb 0.5


Uses Bitwig’s OSC module to send a UDP OSC message to the configured IP/port.

Arguments are auto-parsed as int, then float, then string.

Format:

()OSC <address> <arg1> <arg2> ...


Example internal mapping:

"42" → int

"0.5" → float

"hello" → string

7. Navigation & Jumping
   ()JUMPTO

Move the Clip Launcher focus rectangle by track name & clip name

()JUMPTO Bassline:Verse A
()JUMPTO Drums:Drop 2


Searches cachedTrackNames for the given track name (case-insensitive).

On that track, scans Launcher clips for a clip with that name.

Scrolls the SceneBank so the clip is in view.

Selects the track and clip in mixer/editor and shows the clip in the editor.

Shows a popup like:

Jumped to "Bassline" / "Verse A"


Format:

()JUMPTO <trackName>:<clipName>

Chaining Commands – Examples

All of these go in clip names or cue marker names:

()BPM 124:8 ()SMW Warming up ()SPN Transitioning tempo

()LIR Bass 1:2 ()SNF D1:E5 ()SNT 1:7

()MPCXD 210 ()SCF 1:3:5 ()SPN XD slot 210

()LFR Big Reverb:1 ()RSND ()OSC /lights/scene 5


You can basically script your performance with clip names.