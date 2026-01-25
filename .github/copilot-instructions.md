# BitX Copilot Instructions

BitX is a **ClyphX-style command system for Bitwig Studio** – a Java-based Bitwig extension that interprets special commands embedded in clip names and cue markers to control DAW parameters and external devices.

## Architecture Overview

### Three-Layer Command Processing Pipeline

1. **Listener Layer** (`BitXExtension.java` ~881 lines)
   - Monitors clip launcher clips and arranger cue markers for commands starting with `()`
   - Parses command names and extracts arguments
   - Routes parsed commands to `BitXFunctions` for execution
   - Manages Bitwig API state (TrackBank, SceneBank, Transport, DeviceBank arrays)

2. **Execution Layer** (`BitXFunctions.java` ~786 lines)
   - Implements individual command handlers (BPM, sends, filters, devices, OSC, etc.)
   - Manages device selection and parameter manipulation via Bitwig's remote controls pages
   - Handles external communication: MIDI Program Change, OSC, drum rack presets
   - Caches track names and device parameters for performance

3. **UI Layer** (`BitXGraphics.java`)
   - Communicates with external JavaFX display window via TCP sockets (port 9876)
   - Sends track color data, VU meter data, and UI updates
   - Window is optional (controlled by `displayWindowShowSetting`)

### Key Data Flow

```
Clip Name "()BPM 120:8 ()SMW Message" 
  → Parsed as [BPM: 120:8, SMW: Message]
  → BitXFunctions.executeBPMCommand(120, 8)
  → Bitwig Transport.tempo().transitionTo(120)
  → Over 8 bars
```

### Device Management Pattern

BitX uses **DeviceMatcher + UUID pattern** for device discovery:

```java
// In BitXExtension.java
private final UUID channelFilterUUID = UUID.fromString("c5a1bb2d-a589-4fda-b3cf-911cfd6297be");
private DeviceMatcher channelFilterDeviceMatcher;
private final Map<Integer, Device> channelFilterDevices = new HashMap<>();
private final Map<Integer, List<Parameter>> trackChannelFilterParameters = new HashMap<>();
```

- Each device type (Channel Filter, Note Filter, MPC, etc.) has a **known UUID**
- `DeviceMatcher` finds instances of that device on each track
- Parameters are cached in maps keyed by track index for fast lookup

## Build & Deployment

**Build Command:**
```bash
mvn clean install
```

**Output:** Built automatically to `/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/BitX.bwextension` via copy-rename plugin in pom.xml

**Build Details:**
- Java 21 (release config in maven-compiler-plugin)
- Dependencies: Bitwig API v20, JavaFX 23.0.2
- Generated sources from `src/generated-sources/bitwigapi/` added via build-helper-plugin
- JAR packaged as `.bwextension` (Bitwig's extension format)

## Project Conventions

### Command Format

All commands start with `()` and can be chained in one clip name:
```text
()BPM 120 ()STS 4:4 ()SMW Welcome ()RSND
```

- Commands can span multiple clips (no special ordering)
- Arguments use colon for ranges: `()BPM 120:8` (target BPM, over 8 bars)
- Track references often use format `TrackName:index` or `TrackName:rangeStart:rangeEnd`

### Preferences System

Configuration is stored in Bitwig's controller preferences (accessible via `getHost().getPreferences()`):
- `instrumentSelectorPosition`, `fxSelectorPosition` – device chain positions
- `oscSendIpSetting`, `oscSendPortSetting` – OSC target for commands like `()SOSC`
- `displayWindowShowSetting` – toggle JavaFX window
- `randomAmountSetting` – controls device parameter randomization range

See `BitXExtension.java` constructor for all preference bindings.

### Bi-Directional MIDI/OSC Communication

- **Outbound OSC**: Via `BitXFunctions` → TCP socket to configured IP:port
- **MIDI Program Change**: Devices with UUID `429c7dcb-6863-48bc-becc-508463841e3b` respond to program selection
- **External Socket Listener**: Watches for incoming OSC data via `DataReceivedCallback`

## Extension Integration Points

### Bitwig API Resources

- `BitwigApiLookup.java`: Searches embedded `BitwigAPI25.txt` resource for API documentation
- Use `generate_bitwig_stubs.py` (in `tools/`) to regenerate stubs if API version changes
- Generated stubs in `src/generated-sources/bitwigapi/com/bitwig/` are compiled into final package

### Bitwig Extension Lifecycle

1. Extension Definition (`BitXExtensionDefinition.java`) declares extension to Bitwig
2. Extension Init (`BitXExtension.init()`) sets up TrackBanks, DeviceMatchers, listeners
3. Clip/Cue listeners parse command names
4. Commands execute via `BitXFunctions` methods
5. Optional UI updates sent to JavaFX display via `BitXGraphics` sockets

## Critical Patterns for New Code

**Adding a New Command:**

1. Add command handler method in `BitXFunctions.java` (e.g., `executeMyCommand(String arg)`)
2. In `BitXExtension.init()`, add parsing logic to extract and validate arguments
3. Call handler from clip listener callback
4. Document command in `README.md` with format and examples
5. Add preference settings if command has user-configurable options

**Working with Device Parameters:**

- Always cache Parameter references in `BitXFunctions` maps (e.g., `trackChannelFilterParameters`)
- Use `CursorRemoteControlsPage` to access device parameters safely
- Call `markInterested()` on parameters before reading/writing values
- Bitwig API is asynchronous – use observers/callbacks, not polling

**Communicating with Display Window:**

- Send serialized messages via `BitXGraphics.sendDataToJavaFX(String)`
- Format: `"COMMAND:arg1:arg2:..."` (colon-delimited, received on port 9876)
- Window may not exist if setting disabled – wrap socket calls in try-catch

## File Reference Map

| File | Purpose |
|------|---------|
| [src/main/java/com/personal/BitXExtension.java](src/main/java/com/personal/BitXExtension.java) | Main extension entry point, initialization, listeners |
| [src/main/java/com/personal/BitXFunctions.java](src/main/java/com/personal/BitXFunctions.java) | Command execution logic and Bitwig API calls |
| [src/main/java/com/personal/BitXGraphics.java](src/main/java/com/personal/BitXGraphics.java) | JavaFX display window communication |
| [src/main/java/com/personal/bitwig/BitwigApiLookup.java](src/main/java/com/personal/bitwig/BitwigApiLookup.java) | API documentation search utility |
| [README.md](README.md) | User-facing command reference and preferences |
| [pom.xml](pom.xml) | Maven build config, dependencies, output path |
