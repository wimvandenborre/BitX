package com.personal;

import java.nio.file.Paths;
import java.util.*;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;


public class BitXExtension extends ControllerExtension {
    private static int MAX_TRACKS = 32;
    private static int MAX_SCENES = 128;
    private static int MAX_LAYERS = 32;

    private TrackBank trackBank;
    // private Bitmap textWindowBitmap;
    private String drumPresetsPath;
    private Transport transport;

    private DocumentState documentState;

    private DeviceBank[] instrumentSelectordeviceBanks;
    private DeviceLayerBank[] layerBanks;
    private ChainSelector[] chainSelectors;
    private DeviceBank[][] layerDeviceBanks;

    private DeviceBank[] channelFilterDeviceBanks;
    private DeviceBank[] noteFilterDeviceBanks;
    private DeviceBank[] noteTransposeDeviceBanks;

    private CursorRemoteControlsPage cursorRemoteControlsPage;

    private CursorRemoteControlsPage[][] cursorRemoteControlsPages;

    private Clip cursorClipArranger;
    private Clip cursorClipLauncher;

    // We store the "Clip Type" setting during initialization.
    private SettableEnumValue clipTypeSetting;

    // Global map for storing note data.
    // Outer key: x-coordinate (step); inner map: key (y) → note status.
    private final Map<Integer, Map<Integer, Integer>> currentNotesInClip = new HashMap<>();

    //Preference settings in control Panel
    private Preferences prefs;
    private SettableRangedValue instrumentSelectorPosition, widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting, layerNumberSetting;
    private SettableBooleanValue displayWindowShowSetting;
    private Signal button_openPatreon;
    private Signal button_groovy;


    //private Map<String, CommandExecutor> commands = new HashMap<>();

    private Map<String, CommandEntry> commands = new HashMap<>();

    // Map to store track layer names for each track
    private Map<Integer, Map<String, Integer>> trackLayerNames = new HashMap<>();

    private BitXGraphics bitXGraphics;
    // private Process displayProcess;  // Removed: now handled by BitXGraphics

    //Instrument Selector

    private final UUID instrumentSelectorUUID = UUID.fromString("9588fbcf-721a-438b-8555-97e4231f7d2c");
    private final Map<Integer, Device> instrumentSelectorDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificInstrumentSelectorDevices = new HashMap<>();
    private DeviceMatcher instrumentSelectorDeviceMatcher;

    //ChannelFilter
    private final UUID channelFilterUUID = UUID.fromString("c5a1bb2d-a589-4fda-b3cf-911cfd6297be");
    private final Map<Integer, Device> channelFilterDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificChannelFilterDevices = new HashMap<>();
    private DeviceMatcher channelFilterDeviceMatcher;
    private final Map<Integer, List<Parameter>> trackChannelFilterParameters = new HashMap<>();


    //noteFilter
    private final UUID noteFilterUUID = UUID.fromString("ef7559c8-49ae-4657-95be-11abb896c969");
    private final Map<Integer, Device> noteFilterDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificNoteFilterDevices = new HashMap<>();
    private DeviceMatcher noteFilterDeviceMatcher;
    private final Map<Integer, List<Parameter>> trackNoteFilterParameters = new HashMap<>();

    //noteTranspose
    private final UUID noteTransposeUUID = UUID.fromString("0815cd9e-3a31-4429-a268-dabd952a3b68");
    private final Map<Integer, Device> noteTransposeDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificNoteTransposeDevices = new HashMap<>();
    private DeviceMatcher noteTransposeDeviceMatcher;
    private final Map<Integer, List<Parameter>> trackNoteTransposeParameters = new HashMap<>();


    protected BitXExtension(final BitXExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);

    }

    @Override
    public void init() {

        final ControllerHost host = getHost();
        documentState = host.getDocumentState();

        transport = host.createTransport();
        transport.tempo().value().addValueObserver(value -> {
        });

        drumPresetsPath = Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "Library", "Presets", "Drum Machine").toString();

        // Initialize preferences.
        prefs = host.getPreferences();
        instrumentSelectorPosition = prefs.getNumberSetting("Instr Selector track position", "Tracks", 1, 64, 1, "Position", 6);
//        widthSetting = prefs.getNumberSetting("Bitmap Width", "Display", 40, 5000, 1, "pixels", 3024);
//        heightSetting = prefs.getNumberSetting("Bitmap Height", "Display", 40, 1200, 1, "pixels", 120);
        tracknNumberSetting = prefs.getNumberSetting("Number of tracks", "Display", 1, 128, 1, "tracks", 32);
        sceneNumberSetting = prefs.getNumberSetting("Number of scenes", "Display", 1, 1024, 1, "scenes", 128);
        layerNumberSetting = prefs.getNumberSetting("Number of layers", "Display", 1, 64, 1, "layers", 32);
        displayWindowShowSetting = prefs.getBooleanSetting("Display Window", "Display", true);

        button_openPatreon = prefs.getSignalSetting("Support BitX on Patreon!", "Support", "Go to Patreon.com/CreatingSpaces");
        button_openPatreon.addSignalObserver(() -> openPatreonPage(host)); // ✅ Properly defined observer

        Signal button_groovy = documentState.getSignalSetting(
                "Groovy",
                "NoteManipulation",
                "grooveNotes"
        );

        button_groovy.addSignalObserver(() -> {
            shiftNotesLeft(cursorClipLauncher);
        });


//        int bitmapWidth = (int) widthSetting.getRaw();
//        if (bitmapWidth == 0) bitmapWidth = 400;
//        int bitmapHeight = (int) heightSetting.getRaw();
//        if (bitmapHeight == 0) bitmapHeight = 120;

        MAX_TRACKS = (int) tracknNumberSetting.getRaw();
        if (MAX_TRACKS == 0) MAX_TRACKS = 32;
        MAX_SCENES = (int) sceneNumberSetting.getRaw();
        if (MAX_SCENES == 0) MAX_SCENES = 128;
        MAX_LAYERS = (int) layerNumberSetting.getRaw();

        // Initialize dynamic arrays based on preferences
        instrumentSelectordeviceBanks = new DeviceBank[MAX_TRACKS];
        channelFilterDeviceBanks = new DeviceBank[MAX_TRACKS];
        noteFilterDeviceBanks = new DeviceBank[MAX_TRACKS];
        noteTransposeDeviceBanks = new DeviceBank[MAX_TRACKS];
        layerBanks = new DeviceLayerBank[MAX_TRACKS];
        chainSelectors = new ChainSelector[MAX_TRACKS];
        layerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];
        //textWindowBitmap = host.createBitmap(bitmapWidth, bitmapHeight, BitmapFormat.ARGB32);
        trackBank = host.createTrackBank(MAX_TRACKS, 0, MAX_SCENES, true);
        // Initialize BitXGraphics instance for JavaFX communication

        // Display App init
        bitXGraphics = new BitXGraphics(host);

        if (displayWindowShowSetting.get()) {
            try {
                bitXGraphics.startDisplayProcess();
            } catch (Exception e) {
                host.errorln("Failed to start display process: " + e.getMessage());
            }
        }

        //Initialize the master track
        MasterTrack masterTrack = getHost().createMasterTrack(0); // '0' means no send slots

        // Ensure volume and VU meter are marked as interested
        masterTrack.volume().markInterested();
        masterTrack.color().markInterested(); // If we want to give it a separate color

    //  Add VU meter observer for the master track
        masterTrack.addVuMeterObserver(128, -1, false, newValue -> {
           // getHost().println("🎛 Master Track VU Meter: " + newValue);
            bitXGraphics.sendDataToJavaFX("MASTER_VU:" + newValue);
        });

        masterTrack.color().addValueObserver((r, g, b) -> {
            bitXGraphics.sendDataToJavaFX("MASTER_COLOR:" + (int) (r * 255) + ":" + (int) (g * 255) + ":" + (int) (b * 255));
        });

        //  Mark volume values as "interested"
        for (int i = 0; i < 8; i++) {  // Only the first 8 tracks for faders
            Track track = trackBank.getItemAt(i);

            track.color().markInterested(); // Track color observation
            track.volume().markInterested();
            int trackIndex = i;
            track.color().addValueObserver((r, g, b) -> {
                bitXGraphics.sendTrackColorData(trackIndex, r, g, b);
            });
            // VU Meter Observer: Use 128 range and the sum of both channels
            int finalI = i;
            track.addVuMeterObserver(128, -1, false, newValue -> {
                bitXGraphics.sendVuMeterData(finalI, newValue);
            });
        }

        CursorTrack cursorTrack = host.createCursorTrack("RemoteControlsTrack", "Selected Track", 0, 0, true);
        PinnableCursorDevice cursorDevice = cursorTrack.createCursorDevice("RemoteControlsDevice", "Selected Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
        // Create a RemoteControlsPage for the selected device
        cursorRemoteControlsPage = cursorDevice.createCursorRemoteControlsPage(8);

        cursorRemoteControlsPages = new CursorRemoteControlsPage[MAX_TRACKS][MAX_LAYERS];

        if (cursorRemoteControlsPage != null) {
            cursorRemoteControlsPage.getName().addValueObserver(name -> {
                bitXGraphics.sendDataToJavaFX("PAGE:" + name);
            });

            // Observe first 8 knobs
            for (int i = 0; i < 8; i++) {
                int index = i;
                Parameter knob = cursorRemoteControlsPage.getParameter(i);
                knob.name().addValueObserver(name -> {
                    bitXGraphics.sendDataToJavaFX("KNOB_NAME:" + index + ":" + name);
                });

                knob.value().addValueObserver(value -> {
                    bitXGraphics.sendDataToJavaFX("KNOB_VALUE:" + index + ":" + value);
                });
            }
        }

        //Look for specific devices init
        channelFilterDevices.clear();
        specificChannelFilterDevices.clear();
        trackChannelFilterParameters.clear();
        instrumentSelectorDevices.clear();

        for (int i = 0; i < MAX_TRACKS; i++) {

            Track track = trackBank.getItemAt(i);

            // Instrument Selector: create a device bank and set its matcher.

          //  DeviceBank instrumentSelectorDeviceBank = track.createDeviceBank(16);
           // instrumentSelectorDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(instrumentSelectorUUID));
        //    Device instrumentSelectorDevice = instrumentSelectorDeviceBank.getDevice(0); // The first matched device.
         //   SpecificBitwigDevice specificInstrumentSelectorDevice = instrumentSelectorDevice.createSpecificBitwigDevice(instrumentSelectorUUID);
           // specificInstrumentSelectorDevices.put(i, specificInstrumentSelectorDevice);
           // specificInstrumentSelectorDevices.put(i, specificInstrumentSelectorDevice);


            //channelFilter
            DeviceBank channelFilterDeviceBank = track.createDeviceBank(16);
            channelFilterDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(channelFilterUUID));
           Device channelFilterdevice = channelFilterDeviceBank.getDevice(0); // The first matched device
            SpecificBitwigDevice specificChannelFilterDevice = channelFilterdevice.createSpecificBitwigDevice(channelFilterUUID);
            specificChannelFilterDevices.put(i, specificChannelFilterDevice);
           // Create premade parameters
      List<Parameter> channelFilterparams = new ArrayList<>();
            for (int j = 1; j <= 16; j++) {
                Parameter noteChannelParam = specificChannelFilterDevice.createParameter("SELECT_CHANNEL_" + j);
                noteChannelParam.name().markInterested();
                noteChannelParam.value().markInterested();
                channelFilterparams.add(noteChannelParam);
            }
            trackChannelFilterParameters.put(i, channelFilterparams);

            //noteFilter
            DeviceBank noteFilterDeviceBank = track.createDeviceBank(16);
            noteFilterDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(noteFilterUUID));
            Device noteFilterdevice = noteFilterDeviceBank.getDevice(0); // The first matched device
            SpecificBitwigDevice specificNoteFilterDevice = noteFilterdevice.createSpecificBitwigDevice(noteFilterUUID);
            specificNoteFilterDevices.put(i, specificNoteFilterDevice);

            Parameter noteFilterParamMIN_KEY = specificNoteFilterDevice.createParameter("MIN_KEY");
            Parameter noteFilterParamMAX_KEY = specificNoteFilterDevice.createParameter("MAX_KEY");

            noteFilterParamMIN_KEY.name().markInterested();
            noteFilterParamMIN_KEY.value().markInterested();
            noteFilterParamMAX_KEY.name().markInterested();
            noteFilterParamMAX_KEY.value().markInterested();

            List<Parameter> noteFilterParams = new ArrayList<>();
            noteFilterParams.add(noteFilterParamMIN_KEY);
            noteFilterParams.add(noteFilterParamMAX_KEY);
            trackNoteFilterParameters.put(i, noteFilterParams);


            //noteTranspose
            DeviceBank noteTransposeDeviceBank = track.createDeviceBank(16);
            noteTransposeDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(noteTransposeUUID));
            Device noteTransposedevice = noteTransposeDeviceBank.getDevice(0); // The first matched device
            SpecificBitwigDevice specificNoteTransposeDevice = noteTransposedevice.createSpecificBitwigDevice(noteTransposeUUID);
            specificNoteTransposeDevices.put(i, specificNoteTransposeDevice);
            Parameter noteTransposeOctaves = specificNoteTransposeDevice.createParameter("OCTAVES"); //-6 to +6
            Parameter noteTransposeCoarse = specificNoteTransposeDevice.createParameter("COARSE"); // -96 to + 96
            Parameter noteTransposeFine = specificNoteTransposeDevice.createParameter("FINE"); // -200% to + 200%

            noteTransposeOctaves.name().markInterested();
            noteTransposeOctaves.value().markInterested();
            noteTransposeCoarse.name().markInterested();
            noteTransposeCoarse.value().markInterested();
            noteTransposeFine.name().markInterested();
            noteTransposeFine.value().markInterested();

            List<Parameter> noteTransposeParams = new ArrayList<>();
            noteTransposeParams.add(noteTransposeOctaves);
            noteTransposeParams.add(noteTransposeCoarse);
            noteTransposeParams.add(noteTransposeFine);

            trackNoteTransposeParameters.put(i, noteTransposeParams);

        }

        //Nudging the groove
        // Create the two cursor clips.
        cursorClipArranger = host.createArrangerCursorClip(16 * 8, 128);
        cursorClipLauncher = host.createLauncherCursorClip(16 * 8, 128);

        cursorClipArranger.setStepSize(1.0 / 16.0);
        cursorClipLauncher.setStepSize(1.0 / 32.0);

        // Attach a step data observer to both clips.
        cursorClipArranger.addStepDataObserver(this::observingNotes);
        cursorClipLauncher.addStepDataObserver(this::observingNotes);

        // Make sure the key range is visible.
        cursorClipArranger.scrollToKey(0);
        cursorClipLauncher.scrollToKey(0);

        // Request the "Clip Type" setting once during init and store it.
        clipTypeSetting = documentState.getEnumSetting("Clip Type", "Note Manipulation", new String[] { "Launcher", "Arranger" }, "Arranger");
        
        initializeLayersAndDevices(MAX_LAYERS);

        // The command functions have been moved to the CommandFunctions class.
        BitXFunctions bitXFunctions = new BitXFunctions(
                host,
                transport,
                drumPresetsPath,
                instrumentSelectordeviceBanks,
                channelFilterDeviceBanks,
                layerBanks,
                chainSelectors,
                layerDeviceBanks,
                trackLayerNames,
                cursorRemoteControlsPages,
                trackChannelFilterParameters,
                trackNoteFilterParameters,
                trackNoteTransposeParameters
        );

        commands.put("BPM", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setBpm(arg),
                "BPM: Sets the BPM. Usage: ()BPM <value>."
        ));


        commands.put("LDR", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.executeLDRCommand(arg, trackIndex),
                "LDR: Load Drum rack. Usage: ()LDR <presetname>."
        ));

        commands.put("LIR", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.selectInstrumentInLayer(arg, trackIndex),
                "LIR: Select instrument in Instrument Selector (Needs to in position 6 on track). Usage: ()LIR <presetname>:<optionalremotecontrolspage> ."
        ));

        commands.put("SCF", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setChannelFilter(arg, trackIndex),
                "SCF: Set Channel filter. Usage: ()SCF <1:3:5 ...>."
        ));


        commands.put("SMW", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.displayTextInWindow(arg),
                "SMW: Displays a message in the DisplayWindow. Usage: ()SMW <message>."
        ));


        commands.put("SNF", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setNoteFilter(arg, trackIndex),
                "SNF: Sets note filter. Usage: SNF <E2>:<D5>."
        ));

        commands.put("SNT", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setNoteTranspose(arg, trackIndex),
                "SNT: Sets note transposition. Usage: ()SNT <octave>:<coarse>:<fine> (Last 2 optional)."
        ));

        commands.put("SPN", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.showPopupNotification(arg),
                "SPN: Show popup notification. Usage: SPN <message>."
        ));

        SettableStringValue showCommandDocumentation = documentState.getStringSetting("Documentation", "Documentation", 200, "Command Documentation");
        String[] options = commands.keySet().toArray(new String[0]);
        Arrays.sort(options);  // Sorts alphabetically
        SettableEnumValue commandDropDown = documentState.getEnumSetting(
                "Command", "Commands", options, "BPM"
        );

        commandDropDown.markInterested();
        commandDropDown.addValueObserver(selectedValue -> {
            CommandEntry entry = commands.get(selectedValue);
            if (entry != null) {
                showCommandDocumentation.set(entry.documentation);
            }
        });

        initializeTrackAndClipObservers(host);

        host.showPopupNotification("BitX Initialized");
    }

    public class CommandEntry {
        public final CommandExecutor executor;
        public final String documentation;

        public CommandEntry(CommandExecutor executor, String documentation) {
            this.executor = executor;
            this.documentation = documentation;
        }
    }


    private void observingNotes(int x, int y, int stat) {

       // ControllerHost host = getHost();
        Map<Integer, Integer> stepNotes = currentNotesInClip.get(x);
        if (stepNotes == null) {
            stepNotes = new HashMap<>();
            currentNotesInClip.put(x, stepNotes);
        }
        if (stat == 0) {
            stepNotes.remove(y);
        } else {
            stepNotes.put(y, stat);
        }
    }

    /**
     * Returns the active clip based on the stored "Clip Type" setting.
     * This mimics the JavaScript getCursorClip() function.
     */
    private Clip getCursorClip() {
        // Use the stored clipTypeSetting rather than requesting a new setting.
        String type = clipTypeSetting.get();
       // getHost().println("getCursorClip: Clip Type is " + type);
        if ("Arranger".equals(type)) {
            return cursorClipArranger;
        } else {
            return cursorClipLauncher;
        }
    }

    /**
     * Shifts every note in the global note map one grid step to the left.
     * @param clip The currently active clip.
     */
    private void shiftNotesLeft(Clip clip) {
       // ControllerHost host = getHost();
        int movedCount = 0;
        for (Integer x : new ArrayList<>(currentNotesInClip.keySet())) {
            if (x > 0) { // do not shift notes at the leftmost position
                Map<Integer, Integer> stepNotes = currentNotesInClip.get(x);
                if (stepNotes != null) {
                    for (Integer y : new ArrayList<>(stepNotes.keySet())) {
                       // host.println("Moving note from step " + x + ", key " + y + " to step " + (x - 1));
                        clip.moveStep(x, y, -1, 0);
                        movedCount++;
                    }
                }
            }
        }
      //  host.println("shiftNotesLeft: Completed. Moved " + movedCount + " note(s).");
    }


    private void initializeLayersAndDevices(int maxLayers) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;
            trackLayerNames.put(trackIndex, new HashMap<>());
            Track track = trackBank.getItemAt(trackIndex);

            //channelFilter
            if (channelFilterDeviceBanks[trackIndex] == null) {
                channelFilterDeviceBanks[trackIndex] = track.createDeviceBank(16);
            }

            channelFilterDeviceBanks[trackIndex].setDeviceMatcher(getHost().createBitwigDeviceMatcher(channelFilterUUID));

            for (int j = 0; j < channelFilterDeviceBanks[trackIndex].getSizeOfBank(); j++) {
                Device device = channelFilterDeviceBanks[trackIndex].getDevice(j);
                SpecificBitwigDevice specificChannelFilterDevice = device.createSpecificBitwigDevice(channelFilterUUID);
                if (specificChannelFilterDevice == null) continue;
                device.exists().addValueObserver(exists -> {
                    if (exists) {
                        channelFilterDevices.put(trackIndex, device);
                    }
                });
            }


            //noteFilter
            if (noteFilterDeviceBanks[trackIndex] == null) {
                noteFilterDeviceBanks[trackIndex] = track.createDeviceBank(16);
            }
            noteFilterDeviceBanks[trackIndex].setDeviceMatcher(getHost().createBitwigDeviceMatcher(noteFilterUUID));

            for (int j = 0; j < noteFilterDeviceBanks[trackIndex].getSizeOfBank(); j++) {
                Device device = noteFilterDeviceBanks[trackIndex].getDevice(j);
                if (device == null) continue;

                device.exists().addValueObserver(exists -> {
                    if (exists) {
                        noteFilterDevices.put(trackIndex, device);
                    }
                });
            }

            //noteTranspose
            if (noteTransposeDeviceBanks[trackIndex] == null) {
                noteTransposeDeviceBanks[trackIndex] = track.createDeviceBank(16);
            }
            noteTransposeDeviceBanks[trackIndex].setDeviceMatcher(getHost().createBitwigDeviceMatcher(noteFilterUUID));

            for (int j = 0; j < noteTransposeDeviceBanks[trackIndex].getSizeOfBank(); j++) {
                Device device = noteTransposeDeviceBanks[trackIndex].getDevice(j);
                if (device == null) continue;

                device.exists().addValueObserver(exists -> {
                    if (exists) {
                        noteTransposeDevices.put(trackIndex, device);
                    }
                });
            }

// Instrument Selector and layers setup
            
            trackLayerNames.put(trackIndex, new HashMap<>());
            instrumentSelectordeviceBanks[trackIndex] = track.createDeviceBank(16);
            int position = (int) instrumentSelectorPosition.getRaw();

//            instrumentSelectordeviceBanks[trackIndex].setDeviceMatcher(getHost().createBitwigDeviceMatcher(instrumentSelectorUUID));
//
//            for (int j = 0; j < instrumentSelectordeviceBanks[trackIndex].getSizeOfBank(); j++) {
//                Device device = instrumentSelectordeviceBanks[trackIndex].getDevice(j);
//                if (device == null) continue;
//
//                device.exists().addValueObserver(exists -> {
//                    if (exists) {
//                        getHost().println("Found IS on track: " +trackIndex );
//                       instrumentSelectorDevices.put(trackIndex, device);
//                    }
//                });
//            }

            Device device = instrumentSelectordeviceBanks[trackIndex].getDevice(position);
            layerBanks[trackIndex] = device.createLayerBank(maxLayers);
            chainSelectors[trackIndex] = device.createChainSelector();

            for (int j = 0; j < maxLayers; j++) {
                final int layerIndex = j;
                DeviceLayer layer = layerBanks[trackIndex].getItemAt(layerIndex);
                layerDeviceBanks[trackIndex][layerIndex] = layer.createDeviceBank(1);
                layer.name().addValueObserver(layerName -> {
                    trackLayerNames.get(trackIndex).put(layerName, layerIndex);
                });

                // Ensure the array is initialized before using it
                if (cursorRemoteControlsPages != null) {
                    Device layerDevice = layerDeviceBanks[trackIndex][layerIndex].getDevice(0);
                    if (layerDevice != null) {
                        cursorRemoteControlsPages[trackIndex][layerIndex] = layerDevice.createCursorRemoteControlsPage(8);
                        cursorRemoteControlsPages[trackIndex][layerIndex].pageCount().markInterested();
                    }
                }
            }
        }
    }

    private void initializeTrackAndClipObservers(final ControllerHost host) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;
            Track track = trackBank.getItemAt(trackIndex);
            ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();

            for (int slotIndex = 0; slotIndex < MAX_SCENES; slotIndex++) {
                final int finalSlotIndex = slotIndex;
                ClipLauncherSlot clipSlot = clipLauncherSlotBank.getItemAt(finalSlotIndex);
                clipSlot.name().markInterested();

                clipLauncherSlotBank.addIsPlayingObserver((index, isPlaying) -> {
                    if (index == finalSlotIndex && isPlaying) {
                        String clipName = clipSlot.name().get();
                        if (clipName != null && clipName.startsWith("()")) {
                            List<CommandWithArgument> commandsToExecute = parseCommands(clipName);
                            for (CommandWithArgument cmd : commandsToExecute) {
                                CommandEntry entry = commands.get(cmd.command);
                                if (entry != null) {
                                    entry.executor.execute(cmd.argument, trackIndex);
                                } else {
                                    // host.println("Unknown command: " + cmd.command);
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Splits the clip name (which is assumed to start with "()") into its constituent command parts.
     * Each command part is separated by the marker "()".
     */
    private List<CommandWithArgument> parseCommands(String clipName) {
        List<CommandWithArgument> commandsList = new ArrayList<>();
        // Split on the marker "()", note that the first part will be empty if the clip name starts with "()"
        String[] parts = clipName.split("\\(\\)");
        for (String part : parts) {
            String commandSegment = part.trim();
            if (!commandSegment.isEmpty()) {
                commandsList.add(parseCommandWithArgument(commandSegment));
            }
        }
        return commandsList;
    }

    /**
     * Parses a single command with its argument.
     * It splits on the first space so that, for example, "BPM 120" returns command "BPM" and argument "120".
     */
    private CommandWithArgument parseCommandWithArgument(String commandString) {
        int spaceIndex = commandString.indexOf(" ");
        String command = (spaceIndex > 0) ? commandString.substring(0, spaceIndex).trim() : commandString;
        String argument = (spaceIndex > 0) ? commandString.substring(spaceIndex + 1).trim() : "";
        return new CommandWithArgument(command, argument);
    }

    private static class CommandWithArgument {
        final String command;
        final String argument;

        CommandWithArgument(String command, String argument) {
            this.command = command;
            this.argument = argument;
        }
    }

    private void openPatreonPage(final ControllerHost host) {
        String patreonUrl = "https://per-sonal.com";
        try {
            String[] command;
            if (host.platformIsWindows()) {
                command = new String[] { "cmd", "/c", "start", patreonUrl };
            } else if (host.platformIsMac()) {
                command = new String[] { "open", patreonUrl };
            } else { // Linux
                command = new String[] { "xdg-open", patreonUrl };
            }

            Runtime.getRuntime().exec(command);
        } catch (Exception e) {
            host.errorln("Failed to open Patreon page: " + e.getMessage());
            host.showPopupNotification("Please visit " + patreonUrl + " in your browser.");
        }
    }

    @FunctionalInterface
    private interface CommandExecutor {
        void execute(String argument, int trackIndex);
    }

    @Override
    public void exit() {
        getHost().showPopupNotification("BitX Exited");
    }

    @Override
    public void flush() {

//        // Collect volume levels from the first 8 tracks
//        int numTracks = Math.min(8, MAX_TRACKS); // Limit to 8 tracks
//        StringBuilder data = new StringBuilder();
//
//        for (int i = 0; i < numTracks; i++) {
//            Track track = trackBank.getItemAt(i);
//            float volume = (float) track.volume().get(); // Get volume level (0.0 to 1.0)
//            data.append(volume).append(",");
//        }
//

        // TODO: Send any updates here
    }
}
