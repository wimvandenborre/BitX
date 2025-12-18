package com.personal;

import java.nio.file.Paths;
import java.util.*;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;


public class BitXExtension extends ControllerExtension {
    private static int MAX_TRACKS = 32;
    private static int MAX_SCENES = 128;
    private static int MAX_LAYERS = 32;
    private static int MAX_SENDS = 16;

    private TrackBank trackBank;
    private SceneBank sceneBank;

    // private Bitmap textWindowBitmap;
    private String drumPresetsPath;
    private Transport transport;

    private DocumentState documentState;

    private DeviceBank[] instrumentSelectordeviceBanks;
    private DeviceLayerBank[] layerBanks;
    private ChainSelector[] chainSelectors;
    private DeviceBank[][] layerDeviceBanks;

    // FX Selector
    private DeviceBank[] fxSelectordeviceBanks;
    private DeviceLayerBank[] fxLayerBanks;
    private ChainSelector[] fxChainSelectors;
    private DeviceBank[][] fxLayerDeviceBanks;

    private DeviceBank[] channelFilterDeviceBanks;
    private DeviceBank[] noteFilterDeviceBanks;
    private DeviceBank[] noteTransposeDeviceBanks;
    private DeviceBank[] mpcDeviceBanks;

    private CursorRemoteControlsPage cursorRemoteControlsPage;

    private CursorRemoteControlsPage[][] cursorRemoteControlsPages;
    private CursorRemoteControlsPage[][] fxCursorRemoteControlsPages;

    private Clip cursorClipArranger;
    private Clip cursorClipLauncher;

    private PinnableCursorClip launcherCursorClip;

    private CursorTrack followCursorTrack;

    // --- Selected device + direct-parameter randomizer ---
    private PinnableCursorDevice cursorDevice;      // <- make this a field
    private final List<String> directParameterIds = new ArrayList<>();
    private final Random random = new Random();


    // We store the "Clip Type" setting during initialization.
    private SettableEnumValue clipTypeSetting;

    // Global map for storing note data.
    // Outer key: x-coordinate (step); inner map: key (y) → note status.
    private final Map<Integer, Map<Integer, Integer>> currentNotesInClip = new HashMap<>();

    //Preference settings in control Panel
    private Preferences prefs;
    private SettableStringValue oscSendIpSetting;
    private SettableRangedValue instrumentSelectorPosition, fxSelectorPosition, widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting, layerNumberSetting, oscSendPortSetting, sendNumberSetting;
    private SettableBooleanValue displayWindowShowSetting;
    private Signal button_openPatreon, button_randomizeDevice;
    private Signal button_groovy;



    private Map<String, CommandEntry> commands = new HashMap<>();

    // Map to store track layer names for each track (Instrument Selector)
    private Map<Integer, Map<String, Integer>> trackLayerNames = new HashMap<>();
    // Map to store FX layer names for each track (FX Selector)
    private Map<Integer, Map<String, Integer>> fxTrackLayerNames = new HashMap<>();

    private BitXGraphics bitXGraphics;

    //Instrument Selector
    private final UUID instrumentSelectorUUID = UUID.fromString("9588fbcf-721a-438b-8555-97e4231f7d2c");
    private final Map<Integer, Device> instrumentSelectorDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificInstrumentSelectorDevices = new HashMap<>();
    private DeviceMatcher instrumentSelectorDeviceMatcher;

    // FX Selector (FX Rack) – replace with correct UUID yourself
    private final UUID fxSelectorUUID = UUID.fromString("956e396b-07c5-4430-a58d-8dcfc316522a");
    private final Map<Integer, Device> fxSelectorDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificFxSelectorDevices = new HashMap<>();
    private DeviceMatcher fxSelectorDeviceMatcher;

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

    private final Map<Integer, String> cachedTrackNames = new HashMap<>();

    //Midi Program Change
    private final UUID mpcUUID = UUID.fromString("429c7dcb-6863-48bc-becc-508463841e3b");
    private final Map<Integer, Device> mpcDevices = new HashMap<>();
    private final Map<Integer, SpecificBitwigDevice> specificmpcDevices = new HashMap<>();
    private DeviceMatcher mpcDeviceMatcher;
    private final Map<Integer, List<Parameter>> mpcParameters = new HashMap<>();

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
        transport.timeSignature().markInterested();

        Arranger arranger = host.createArranger();
        CueMarkerBank cueMarkerBank = arranger.createCueMarkerBank(64);

        for (int i = 0; i < cueMarkerBank.getSizeOfBank(); i++) {
            CueMarker cueMarker = cueMarkerBank.getItemAt(i);
            if (cueMarker != null) {
                cueMarker.position().markInterested();
                cueMarker.name().markInterested();
            }
        }

        transport.getPosition().addValueObserver(beatTime -> {
            for (int i = 0; i < cueMarkerBank.getSizeOfBank(); i++) {
                CueMarker cueMarker = cueMarkerBank.getItemAt(i);

                if (cueMarker != null) {
                    double markerPosition = cueMarker.position().get();
                    double currentPosition = beatTime;

                    if (Math.abs(currentPosition - markerPosition) < 0.1) {
                        String markerName = cueMarker.name().get();

                        if (markerName.startsWith("()")) {
                            List<CommandWithArgument> commandsToExecute = parseCommands(markerName);

                            for (CommandWithArgument cmd : commandsToExecute) {
                                CommandEntry entry = commands.get(cmd.command);
                                if (entry != null) {
                                    entry.executor.execute(cmd.argument, 0); // Track index is not relevant here
                                } else {
                                    getHost().println("Unknown cue marker command: " + cmd.command);
                                }
                            }
                        }
                    }
                }
            }
        });

        drumPresetsPath = Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "Library", "Presets", "Drum Machine").toString();

        // Initialize preferences.
        prefs = host.getPreferences();
        tracknNumberSetting = prefs.getNumberSetting("Number of tracks", "Display", 1, 128, 1, "tracks", 32);
        sceneNumberSetting = prefs.getNumberSetting("Number of scenes", "Display", 1, 1024, 1, "scenes", 128);
        layerNumberSetting = prefs.getNumberSetting("Number of layers", "Display", 1, 64, 1, "layers", 32);
        sendNumberSetting = prefs.getNumberSetting("Number of sends", "Display", 1, 16, 1, "sends", 4);
        displayWindowShowSetting = prefs.getBooleanSetting("Display Window", "Display", false);
        oscSendIpSetting = prefs.getStringSetting("Osc Send IP", "OSC", 15, "127.0.0.1");
        oscSendPortSetting = prefs.getNumberSetting("Osc Send Port", "OSC", 1024, 65535, 1, "", 8000);
        button_openPatreon = prefs.getSignalSetting("Support BitX on Patreon!", "Support", "Go to Patreon.com/CreatingSpaces");
        button_openPatreon.addSignalObserver(() -> openPatreonPage(host));


        followCursorTrack = host.createCursorTrack("FollowTrack", "Jump Follow", 0, 0, true);

        MAX_TRACKS = (int) tracknNumberSetting.getRaw();
        if (MAX_TRACKS == 0) MAX_TRACKS = 32;
        MAX_SCENES = (int) sceneNumberSetting.getRaw();
        if (MAX_SCENES == 0) MAX_SCENES = 128;
        MAX_LAYERS = (int) layerNumberSetting.getRaw();
        MAX_SENDS = (int) sendNumberSetting.getRaw();
        if (MAX_SENDS == 0) MAX_SENDS = 4;

        // Initialize dynamic arrays based on preferences
        instrumentSelectordeviceBanks = new DeviceBank[MAX_TRACKS];
        fxSelectordeviceBanks = new DeviceBank[MAX_TRACKS];

        channelFilterDeviceBanks = new DeviceBank[MAX_TRACKS];
        noteFilterDeviceBanks = new DeviceBank[MAX_TRACKS];
        noteTransposeDeviceBanks = new DeviceBank[MAX_TRACKS];
        mpcDeviceBanks = new DeviceBank[MAX_TRACKS];

        layerBanks = new DeviceLayerBank[MAX_TRACKS];
        chainSelectors = new ChainSelector[MAX_TRACKS];
        layerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];

        fxLayerBanks = new DeviceLayerBank[MAX_TRACKS];
        fxChainSelectors = new ChainSelector[MAX_TRACKS];
        fxLayerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];

        trackBank = host.createTrackBank(MAX_TRACKS, MAX_SENDS, MAX_SCENES, true);
        sceneBank = host.createSceneBank(MAX_SCENES);

        // Initialize BitXGraphics instance for JavaFX communication
        bitXGraphics = new BitXGraphics(host);

        if (displayWindowShowSetting.get()) {
            try {
                bitXGraphics.startDisplayProcess();
            } catch (Exception e) {
                host.errorln("Failed to start display process: " + e.getMessage());
            }
        }

        //Initialize the master track
        MasterTrack masterTrack = getHost().createMasterTrack(0);

        masterTrack.volume().markInterested();
        masterTrack.color().markInterested();

        masterTrack.addVuMeterObserver(128, -1, false, newValue -> {
            bitXGraphics.sendDataToJavaFX("MASTER_VU:" + newValue);
        });

        masterTrack.color().addValueObserver((r, g, b) -> {
            bitXGraphics.sendDataToJavaFX("MASTER_COLOR:" + (int) (r * 255) + ":" + (int) (g * 255) + ":" + (int) (b * 255));
        });

        //  Mark volume values as "interested"
        for (int i = 0; i < 8 && i < MAX_TRACKS; i++) {
            Track track = trackBank.getItemAt(i);

            track.color().markInterested();
            track.volume().markInterested();
            int trackIndex = i;
            track.color().addValueObserver((r, g, b) -> {
                bitXGraphics.sendTrackColorData(trackIndex, r, g, b);
            });

            int finalI = i;
            track.addVuMeterObserver(128, -1, false, newValue -> {
                bitXGraphics.sendVuMeterData(finalI, newValue);
            });
        }

        CursorTrack cursorTrack = host.createCursorTrack("RemoteControlsTrack", "Selected Track", 0, 0, true);
        cursorDevice = cursorTrack.createCursorDevice(
                "RemoteControlsDevice",
                "Selected Device",
                0,
                CursorDeviceFollowMode.FOLLOW_SELECTION
        );

// 👇 collect ALL direct parameter IDs of the currently selected device
        cursorDevice.addDirectParameterIdObserver(ids -> {
            directParameterIds.clear();
            Collections.addAll(directParameterIds, ids);
        });

        launcherCursorClip = cursorTrack.createLauncherCursorClip(16 * 8, 128);
        cursorRemoteControlsPage = cursorDevice.createCursorRemoteControlsPage(8);

        cursorRemoteControlsPages = new CursorRemoteControlsPage[MAX_TRACKS][MAX_LAYERS];
        fxCursorRemoteControlsPages = new CursorRemoteControlsPage[MAX_TRACKS][MAX_LAYERS];

        if (cursorRemoteControlsPage != null) {
            cursorRemoteControlsPage.getName().addValueObserver(name -> {
                bitXGraphics.sendDataToJavaFX("PAGE:" + name);
            });

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
        mpcDevices.clear();
        specificmpcDevices.clear();
        fxSelectorDevices.clear();
        specificFxSelectorDevices.clear();

        for (int i = 0; i < MAX_TRACKS; i++) {

            Track track = trackBank.getItemAt(i);

            // Sends: mark interested
            SendBank sendBank = track.sendBank();
            int numSends = sendBank.getSizeOfBank();
            for (int s = 0; s < numSends; s++) {
                Send send = sendBank.getItemAt(s);
                send.value().markInterested();
                send.isEnabled().markInterested();
            }

            //channelFilter
            DeviceBank channelFilterDeviceBank = track.createDeviceBank(16);
            channelFilterDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(channelFilterUUID));
            Device channelFilterdevice = channelFilterDeviceBank.getDevice(0);
            SpecificBitwigDevice specificChannelFilterDevice = channelFilterdevice.createSpecificBitwigDevice(channelFilterUUID);
            specificChannelFilterDevices.put(i, specificChannelFilterDevice);

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
            Device noteFilterdevice = noteFilterDeviceBank.getDevice(0);
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
            Device noteTransposedevice = noteTransposeDeviceBank.getDevice(0);
            SpecificBitwigDevice specificNoteTransposeDevice = noteTransposedevice.createSpecificBitwigDevice(noteTransposeUUID);
            specificNoteTransposeDevices.put(i, specificNoteTransposeDevice);
            Parameter noteTransposeOctaves = specificNoteTransposeDevice.createParameter("OCTAVES");
            Parameter noteTransposeCoarse = specificNoteTransposeDevice.createParameter("COARSE");
            Parameter noteTransposeFine = specificNoteTransposeDevice.createParameter("FINE");

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

            //MPC
            DeviceBank mpcDeviceBank = track.createDeviceBank(16);
            mpcDeviceBank.setDeviceMatcher(host.createBitwigDeviceMatcher(mpcUUID));
            Device mpcdevice = mpcDeviceBank.getDevice(0);
            SpecificBitwigDevice specificmpcDevice = mpcdevice.createSpecificBitwigDevice(mpcUUID);
            specificmpcDevices.put(i, specificmpcDevice);

            Parameter mpcParamPROGRAM = specificmpcDevice.createParameter("PROGRAM");
            Parameter mpcParamBANK_MSB = specificmpcDevice.createParameter("BANK_MSB");
            Parameter mpcParamBANK_LSB = specificmpcDevice.createParameter("BANK_LSB");
            Parameter mpcParamCHANNEL = specificmpcDevice.createParameter("CHANNEL");

            mpcParamPROGRAM.name().markInterested();
            mpcParamPROGRAM.value().markInterested();
            mpcParamBANK_MSB.name().markInterested();
            mpcParamBANK_MSB.value().markInterested();
            mpcParamBANK_LSB.name().markInterested();
            mpcParamBANK_LSB.value().markInterested();
            mpcParamCHANNEL.name().markInterested();
            mpcParamCHANNEL.value().markInterested();

            List<Parameter> mpcParams = new ArrayList<>();
            mpcParams.add(mpcParamPROGRAM);
            mpcParams.add(mpcParamBANK_MSB);
            mpcParams.add(mpcParamBANK_LSB);
            mpcParams.add(mpcParamCHANNEL);
            mpcParameters.put(i, mpcParams);
        }

        //Nudging the groove
        cursorClipArranger = host.createArrangerCursorClip(16 * 8, 128);
        cursorClipLauncher = host.createLauncherCursorClip(16 * 8, 128);

        cursorClipArranger.setStepSize(1.0 / 16.0);
        cursorClipLauncher.setStepSize(1.0 / 32.0);

        cursorClipArranger.addStepDataObserver(this::observingNotes);
        cursorClipLauncher.addStepDataObserver(this::observingNotes);

        cursorClipArranger.scrollToKey(0);
        cursorClipLauncher.scrollToKey(0);

        initializeLayersAndDevices(MAX_LAYERS);

        String oscIp = oscSendIpSetting.get();
        int oscPort = (int) oscSendPortSetting.getRaw();
        if (oscPort == 0) {
            oscPort = 8000;
        }

        BitXFunctions bitXFunctions = new BitXFunctions(
                host,
                transport,
                drumPresetsPath,
                instrumentSelectordeviceBanks,
                channelFilterDeviceBanks,
                layerBanks,
                chainSelectors,
                layerDeviceBanks,
                fxLayerBanks,
                fxChainSelectors,
                fxLayerDeviceBanks,
                trackLayerNames,
                fxTrackLayerNames,
                cursorRemoteControlsPages,
                fxCursorRemoteControlsPages,
                trackChannelFilterParameters,
                trackNoteFilterParameters,
                trackNoteTransposeParameters,
                mpcParameters,
                oscIp,
                oscPort,
                trackBank,
                sceneBank,
                MAX_TRACKS,
                MAX_SCENES,
                cachedTrackNames,
                followCursorTrack,
                launcherCursorClip
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
                "LIR: Select instrument in the first Instrument Selector device on the track. Usage: ()LIR <layername>:<optionalremotecontrolspage>."
        ));

        commands.put("LFR", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.selectFxInLayer(arg, trackIndex),
                "LFR: Select FX in the first FX Selector device on the track. Usage: ()LFR <fxname>:<optionalremotecontrolspage>."
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

        commands.put("OSC", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.sendOSCMessage(arg),
                "OSC: Sends an OSC message. Usage: ()OSC /address arg1 arg2 ..."
        ));

        commands.put("STS", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setTimeSignature(arg),
                "STS: Sets the time signature. Usage: ()STS <numerator>:<denominator>."
        ));

        commands.put("JUMPTO", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.jumpToClipLauncherRectangleByName(arg),
                "JUMPTO: Move focus rectangle by track name and clip name. Usage: ()JUMPTO <trackName>:<clipName>."
        ));

        commands.put("RSND", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.resetAllSendsOnAllTracks(),
                "RSND: Reset all sends on all tracks. Usage: ()RSND"
        ));

        commands.put("MPC", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setMPC(arg, trackIndex),
                "MPC: Sets Midi Program Change. Usage: ()MPC <program>:<MSB>:<LSB>:<channel> (Last 1 optional)."
        ));

        commands.put("MPCXD", new CommandEntry(
                (arg, trackIndex) -> bitXFunctions.setMPCXD(arg, trackIndex),
                "MPCXD: Set Korg Minilogue XD slot by number. Usage: ()MPCXD <1-500>"
        ));

        SettableStringValue showCommandDocumentation = documentState.getStringSetting("Documentation", "Documentation", 200, "SNF: Sets note filter. Usage: SNF <E2>:<D5>.");
        String[] options = commands.keySet().toArray(new String[0]);
        Arrays.sort(options);

        SettableEnumValue commandDropDown = documentState.getEnumSetting(
                "Command", "Commands", options, "SNF"
        );

        commandDropDown.markInterested();
        commandDropDown.addValueObserver(selectedValue -> {
            CommandEntry entry = commands.get(selectedValue);
            if (entry != null) {
                showCommandDocumentation.set(entry.documentation);
            }
        });

        button_randomizeDevice = documentState.getSignalSetting(
                "Randomize selected device",  // label in BitX UI
                "BitX",                       // section/group name
                "Randomize"                   // button text
        );

// When the button is clicked in Bitwig, this gets called:
        button_randomizeDevice.addSignalObserver(this::randomizeSelectedDevice);


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

    private Clip getCursorClip() {
        String type = clipTypeSetting != null ? clipTypeSetting.get() : "Arranger";
        if ("Arranger".equals(type)) {
            return cursorClipArranger;
        } else {
            return cursorClipLauncher;
        }
    }

    private void shiftNotesLeft(Clip clip) {
        int movedCount = 0;
        for (Integer x : new ArrayList<>(currentNotesInClip.keySet())) {
            if (x > 0) {
                Map<Integer, Integer> stepNotes = currentNotesInClip.get(x);
                if (stepNotes != null) {
                    for (Integer y : new ArrayList<>(stepNotes.keySet())) {
                        clip.moveStep(x, y, -1, 0);
                        movedCount++;
                    }
                }
            }
        }
    }

    private void initializeLayersAndDevices(int maxLayers) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;
            trackLayerNames.put(trackIndex, new HashMap<>());
            fxTrackLayerNames.put(trackIndex, new HashMap<>());

            Track track = trackBank.getItemAt(trackIndex);

            // --- INSTRUMENT SELECTOR (first matching device on track) ---
            instrumentSelectordeviceBanks[trackIndex] = track.createDeviceBank(16);
            instrumentSelectordeviceBanks[trackIndex].setDeviceMatcher(
                    getHost().createBitwigDeviceMatcher(instrumentSelectorUUID)
            );

            // First matching Instrument Selector device (index 0 in the matched bank)
            Device instrDevice = instrumentSelectordeviceBanks[trackIndex].getDevice(0);
            layerBanks[trackIndex] = instrDevice.createLayerBank(maxLayers);
            chainSelectors[trackIndex] = instrDevice.createChainSelector();

            for (int j = 0; j < maxLayers; j++) {
                final int layerIndex = j;
                DeviceLayer layer = layerBanks[trackIndex].getItemAt(layerIndex);
                layerDeviceBanks[trackIndex][layerIndex] = layer.createDeviceBank(1);

                // Map layer name -> index for LIR
                layer.name().addValueObserver(layerName -> {
                    trackLayerNames.get(trackIndex).put(layerName, layerIndex);
                });

                if (cursorRemoteControlsPages != null) {
                    Device layerDevice = layerDeviceBanks[trackIndex][layerIndex].getDevice(0);
                    if (layerDevice != null) {
                        cursorRemoteControlsPages[trackIndex][layerIndex] =
                                layerDevice.createCursorRemoteControlsPage(8);
                        cursorRemoteControlsPages[trackIndex][layerIndex].pageCount().markInterested();
                    }
                }
            }

            // --- FX SELECTOR (first matching FX rack on track) ---
            fxSelectordeviceBanks[trackIndex] = track.createDeviceBank(16);
            fxSelectordeviceBanks[trackIndex].setDeviceMatcher(
                    getHost().createBitwigDeviceMatcher(fxSelectorUUID)
            );

            // First matching FX Selector device (index 0 in the matched bank)
            Device fxDevice = fxSelectordeviceBanks[trackIndex].getDevice(0);
            fxLayerBanks[trackIndex] = fxDevice.createLayerBank(maxLayers);
            fxChainSelectors[trackIndex] = fxDevice.createChainSelector();

            for (int j = 0; j < maxLayers; j++) {
                final int layerIndex = j;
                DeviceLayer fxLayer = fxLayerBanks[trackIndex].getItemAt(layerIndex);
                fxLayerDeviceBanks[trackIndex][layerIndex] = fxLayer.createDeviceBank(1);

                // Map FX layer name -> index for LFR
                fxLayer.name().addValueObserver(layerName -> {
                    fxTrackLayerNames.get(trackIndex).put(layerName, layerIndex);
                });

                if (fxCursorRemoteControlsPages != null) {
                    Device fxLayerDevice = fxLayerDeviceBanks[trackIndex][layerIndex].getDevice(0);
                    if (fxLayerDevice != null) {
                        fxCursorRemoteControlsPages[trackIndex][layerIndex] =
                                fxLayerDevice.createCursorRemoteControlsPage(8);
                        fxCursorRemoteControlsPages[trackIndex][layerIndex].pageCount().markInterested();
                    }
                }
            }
        }
    }


    private void initializeTrackAndClipObservers(final ControllerHost host) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;

            Track track = trackBank.getItemAt(trackIndex);

            track.name().markInterested();
            track.name().addValueObserver(name -> cachedTrackNames.put(trackIndex, name));

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
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private List<CommandWithArgument> parseCommands(String clipName) {
        List<CommandWithArgument> commandsList = new ArrayList<>();
        String[] parts = clipName.split("\\(\\)");
        for (String part : parts) {
            String commandSegment = part.trim();
            if (!commandSegment.isEmpty()) {
                commandsList.add(parseCommandWithArgument(commandSegment));
            }
        }
        return commandsList;
    }

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
        String patreonUrl = "https://patreon.com/CreatingSpaces";
        try {
            String[] command;
            if (host.platformIsWindows()) {
                command = new String[] { "cmd", "/c", "start", patreonUrl };
            } else if (host.platformIsMac()) {
                command = new String[] { "open", patreonUrl };
            } else {
                command = new String[] { "xdg-open", patreonUrl };
            }

            Runtime.getRuntime().exec(command);
        } catch (Exception e) {
            host.errorln("Failed to open Patreon page: " + e.getMessage());
            host.showPopupNotification("Please visit " + patreonUrl + " in your browser.");
        }
    }

    private void randomizeSelectedDevice() {
        if (cursorDevice == null || directParameterIds.isEmpty()) {
            getHost().println("BitX: No device or no parameters to randomize.");
            return;
        }

        final int resolution = 16384; // enough steps for smooth values

        for (String id : directParameterIds) {
            int step = random.nextInt(resolution); // [0, resolution-1]
            cursorDevice.setDirectParameterValueNormalized(id, step, resolution);
        }

        getHost().showPopupNotification("BitX: Randomized " + directParameterIds.size() + " parameters");
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
        // TODO: Send any updates here maybe
    }
}
