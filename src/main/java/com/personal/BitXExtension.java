package com.personal;

import java.nio.file.Paths;
import java.util.*;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscMessage;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.api.opensoundcontrol.OscServer;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;


public class BitXExtension extends ControllerExtension {
    private static int MAX_TRACKS = 32;
    private static int MAX_SCENES = 128;
    private static int MAX_LAYERS = 32;
    private static int MAX_SENDS = 16;
    private static final String OSC_CATEGORY = "OSC";
    private static final int OSC_PORT_MIN = 1;
    private static final int OSC_PORT_MAX = 65535;
    private static final int DEFAULT_OSC_IN_PORT = 9000;
    private static final int DEFAULT_OSC_OUT_PORT = 8000;
    private static final String DEFAULT_OSC_OUT_HOST = "127.0.0.1";
    private static final boolean DEFAULT_OSC_IN_ENABLED = true;
    private static final boolean DEFAULT_OSC_OUT_ENABLED = true;
    private static final String OSC_TORSO_T1_ADDRESS = "/torsot1script/cc";
    private static final String OSC_TARGET_SELECTED_DEVICE = "selecteddevice";
    private static final String OSC_TARGET_TRACK_REMOTE = "trackremote";
    private static final String OSC_TARGET_PROJECT_REMOTE = "projectremote";
    private static final String DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR = "selected-device-cursor";
    private static final String OSC_TORSO_PAGE_ADDRESS = "/bitx/t1/page";
    private static final String OSC_TORSO_KNOB_ADDRESS = "/bitx/t1/knob";

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
    private DeviceBank[] drumRackDeviceBanks;

    private CursorRemoteControlsPage cursorRemoteControlsPage;
    private CursorDevice[] trackSelectedDeviceCursors;
    private CursorRemoteControlsPage[] trackSelectedDevicePages;
    private CursorRemoteControlsPage[] trackRemotePages;
    private CursorRemoteControlsPage projectRemotePage;

    private CursorRemoteControlsPage[][] cursorRemoteControlsPages;
    private CursorRemoteControlsPage[][] fxCursorRemoteControlsPages;

    private Clip cursorClipArranger;
    private Clip cursorClipLauncher;

    private PinnableCursorClip launcherCursorClip;

    private CursorTrack followCursorTrack;
    private CursorTrack selectedCursorTrack;

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
    private SettableStringValue oscOutHostSetting;
    private SettableStringValue oscOutNameSetting;
    private SettableStringValue oscOut2HostSetting;
    private SettableStringValue oscOut2NameSetting;
    private SettableStringValue oscOut3HostSetting;
    private SettableStringValue oscOut3NameSetting;
    private SettableRangedValue instrumentSelectorPosition, fxSelectorPosition, widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting, layerNumberSetting, oscOutPortSetting, oscOut2PortSetting, oscOut3PortSetting, oscInPortSetting, sendNumberSetting;
    private SettableBooleanValue displayWindowShowSetting;
    private SettableBooleanValue oscInEnabledSetting;
    private SettableBooleanValue oscOutEnabledSetting;
    private SettableBooleanValue oscOut2EnabledSetting;
    private SettableBooleanValue oscOut3EnabledSetting;
    private SettableEnumValue oscDefaultOutputSetting;
    private SettableEnumValue oscTorsoOutputSetting;
    private Signal button_openPatreon, button_randomizeDevice;
    private Signal button_groovy;
    private SettableEnumValue randomAmountSetting;
    private int randomAmount = 10; // default
    private final Map<String, Float> directParamValues = new HashMap<>();




    private Map<String, CommandEntry> commands = new HashMap<>();

    // Map to store track layer names for each track (Instrument Selector)
    private Map<Integer, Map<String, Integer>> trackLayerNames = new HashMap<>();
    // Map to store FX layer names for each track (FX Selector)
    private Map<Integer, Map<String, Integer>> fxTrackLayerNames = new HashMap<>();

    private BitXGraphics bitXGraphics;
    private BitXFunctions bitXFunctions;
    private String activeDisplaySource = DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR;

    private OscModule oscModule;
    private OscAddressSpace oscAddressSpace;
    private OscServer oscServer;
    private int oscInPort;
    private int oscOutPort;
    private int oscOut2Port;
    private int oscOut3Port;
    private String oscOutHost;
    private String oscOutName;
    private String oscOut2Host;
    private String oscOut2Name;
    private String oscOut3Host;
    private String oscOut3Name;
    private boolean oscInEnabled;
    private boolean oscOutEnabled;
    private boolean oscOut2Enabled;
    private boolean oscOut3Enabled;
    private String defaultOscOutputName;
    private String torsoOscOutputName;

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
        oscInEnabledSetting = prefs.getBooleanSetting("OSC In Enabled", OSC_CATEGORY, DEFAULT_OSC_IN_ENABLED);
        oscInPortSetting = prefs.getNumberSetting("OSC In Port", OSC_CATEGORY, OSC_PORT_MIN, OSC_PORT_MAX, 1, "", DEFAULT_OSC_IN_PORT);
        oscOutNameSetting = prefs.getStringSetting("OSC Out 1 Name", OSC_CATEGORY, 32, "TouchDesigner");
        oscOutHostSetting = prefs.getStringSetting("OSC Out 1 Host", OSC_CATEGORY, 64, DEFAULT_OSC_OUT_HOST);
        oscOutPortSetting = prefs.getNumberSetting("OSC Out 1 Port", OSC_CATEGORY, OSC_PORT_MIN, OSC_PORT_MAX, 1, "", DEFAULT_OSC_OUT_PORT);
        oscOutEnabledSetting = prefs.getBooleanSetting("OSC Out 1 Enabled", OSC_CATEGORY, DEFAULT_OSC_OUT_ENABLED);
        oscOut2NameSetting = prefs.getStringSetting("OSC Out 2 Name", OSC_CATEGORY, 32, "TouchOSC");
        oscOut2HostSetting = prefs.getStringSetting("OSC Out 2 Host", OSC_CATEGORY, 64, DEFAULT_OSC_OUT_HOST);
        oscOut2EnabledSetting = prefs.getBooleanSetting("OSC Out 2 Enabled", OSC_CATEGORY, false);
        oscOut3NameSetting = prefs.getStringSetting("OSC Out 3 Name", OSC_CATEGORY, 32, "Output3");
        oscOut3HostSetting = prefs.getStringSetting("OSC Out 3 Host", OSC_CATEGORY, 64, DEFAULT_OSC_OUT_HOST);
        oscOut3PortSetting = prefs.getNumberSetting("OSC Out 3 Port", OSC_CATEGORY, OSC_PORT_MIN, OSC_PORT_MAX, 1, "", DEFAULT_OSC_OUT_PORT + 2);
        oscOut3EnabledSetting = prefs.getBooleanSetting("OSC Out 3 Enabled", OSC_CATEGORY, false);
        oscOut2PortSetting = prefs.getNumberSetting("OSC Out 2 Port", OSC_CATEGORY, OSC_PORT_MIN, OSC_PORT_MAX, 1, "", DEFAULT_OSC_OUT_PORT + 1);
        oscTorsoOutputSetting = prefs.getEnumSetting(
                "BitxTorsoT-1 Output",
                OSC_CATEGORY,
                new String[]{"Output 1", "Output 2", "Output 3"},
                "Output 1"
        );
        oscDefaultOutputSetting = prefs.getEnumSetting(
                "OSC Default Output",
                OSC_CATEGORY,
                new String[]{"Output 1", "Output 2", "Output 3"},
                "Output 1"
        );
        button_openPatreon = prefs.getSignalSetting("Support BitX on Patreon!", "Support", "Go to Patreon.com/CreatingSpaces");
        button_openPatreon.addSignalObserver(() -> openPatreonPage(host));
        oscModule = host.getOscModule();
        oscAddressSpace = oscModule.createAddressSpace();
        oscAddressSpace.setName("BitX");
        oscAddressSpace.setShouldLogMessages(true);
        oscAddressSpace.registerDefaultMethod((source, message) ->
                host.println("BitX OSC recv: " + message.getAddressPattern()));
        registerOscHandlers();
        oscServer = oscModule.createUdpServer(oscAddressSpace);
        oscInPort = (int) Math.round(oscInPortSetting.getRaw());
        oscOutPort = (int) Math.round(oscOutPortSetting.getRaw());
        oscOut2Port = (int) Math.round(oscOut2PortSetting.getRaw());
        oscOut3Port = (int) Math.round(oscOut3PortSetting.getRaw());
        oscOutHost = oscOutHostSetting.get();
        oscOut2Host = oscOut2HostSetting.get();
        oscOut3Host = oscOut3HostSetting.get();
        oscOutName = oscOutNameSetting.get();
        oscOut2Name = oscOut2NameSetting.get();
        oscOut3Name = oscOut3NameSetting.get();
        oscInEnabled = oscInEnabledSetting.get();
        oscOutEnabled = oscOutEnabledSetting.get();
        oscOut2Enabled = oscOut2EnabledSetting.get();
        oscOut3Enabled = oscOut3EnabledSetting.get();
        defaultOscOutputName = oscDefaultOutputSetting.get();
        torsoOscOutputName = oscTorsoOutputSetting.get();
        if (oscInPort <= 0) {
            oscInPort = DEFAULT_OSC_IN_PORT;
        }
        if (oscOutPort <= 0) {
            oscOutPort = DEFAULT_OSC_OUT_PORT;
        }
        if (oscOut2Port <= 0) {
            oscOut2Port = DEFAULT_OSC_OUT_PORT + 1;
        }
        if (oscOut3Port <= 0) {
            oscOut3Port = DEFAULT_OSC_OUT_PORT + 2;
        }
        if (oscOutHost == null || oscOutHost.trim().isEmpty()) {
            oscOutHost = DEFAULT_OSC_OUT_HOST;
        }
        if (oscOut2Host == null || oscOut2Host.trim().isEmpty()) {
            oscOut2Host = DEFAULT_OSC_OUT_HOST;
        }
        if (oscOut3Host == null || oscOut3Host.trim().isEmpty()) {
            oscOut3Host = DEFAULT_OSC_OUT_HOST;
        }
        if (oscOutName == null || oscOutName.trim().isEmpty()) {
            oscOutName = "Output 1";
        }
        if (oscOut2Name == null || oscOut2Name.trim().isEmpty()) {
            oscOut2Name = "Output 2";
        }
        if (oscOut3Name == null || oscOut3Name.trim().isEmpty()) {
            oscOut3Name = "Output 3";
        }
        if (defaultOscOutputName == null || defaultOscOutputName.trim().isEmpty()) {
            defaultOscOutputName = "Output 1";
        }
        if (torsoOscOutputName == null || torsoOscOutputName.trim().isEmpty()) {
            torsoOscOutputName = "Output 1";
        }
        oscInPortSetting.addRawValueObserver(value -> {
            oscInPort = (int) Math.round(value);
            refreshOscServer();
        });
        oscOutNameSetting.addValueObserver(value -> {
            oscOutName = value;
            notifyOscRestartRequired();
        });
        oscOutPortSetting.addRawValueObserver(value -> {
            oscOutPort = (int) Math.round(value);
            notifyOscRestartRequired();
        });
        oscOutHostSetting.addValueObserver(value -> {
            oscOutHost = value;
            notifyOscRestartRequired();
        });
        oscOut2NameSetting.addValueObserver(value -> {
            oscOut2Name = value;
            notifyOscRestartRequired();
        });
        oscOut2PortSetting.addRawValueObserver(value -> {
            oscOut2Port = (int) Math.round(value);
            notifyOscRestartRequired();
        });
        oscOut2HostSetting.addValueObserver(value -> {
            oscOut2Host = value;
            notifyOscRestartRequired();
        });
        oscOut3NameSetting.addValueObserver(value -> {
            oscOut3Name = value;
            notifyOscRestartRequired();
        });
        oscOut3PortSetting.addRawValueObserver(value -> {
            oscOut3Port = (int) Math.round(value);
            notifyOscRestartRequired();
        });
        oscOut3HostSetting.addValueObserver(value -> {
            oscOut3Host = value;
            notifyOscRestartRequired();
        });
        oscInEnabledSetting.addValueObserver(value -> {
            oscInEnabled = value;
            refreshOscServer();
        });
        oscOutEnabledSetting.addValueObserver(value -> {
            oscOutEnabled = value;
            notifyOscRestartRequired();
        });
        oscOut2EnabledSetting.addValueObserver(value -> {
            oscOut2Enabled = value;
            notifyOscRestartRequired();
        });
        oscOut3EnabledSetting.addValueObserver(value -> {
            oscOut3Enabled = value;
            notifyOscRestartRequired();
        });
        oscDefaultOutputSetting.addValueObserver(value -> {
            defaultOscOutputName = value;
            notifyOscRestartRequired();
        });
        oscTorsoOutputSetting.addValueObserver(value -> {
            torsoOscOutputName = resolveOutputName(value);
        });
        refreshOscServer();

        bitXGraphics = new BitXGraphics(host);


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
        drumRackDeviceBanks = new DeviceBank[MAX_TRACKS];


        layerBanks = new DeviceLayerBank[MAX_TRACKS];
        chainSelectors = new ChainSelector[MAX_TRACKS];
        layerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];

        fxLayerBanks = new DeviceLayerBank[MAX_TRACKS];
        fxChainSelectors = new ChainSelector[MAX_TRACKS];
        fxLayerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];

        trackBank = host.createTrackBank(MAX_TRACKS, MAX_SENDS, MAX_SCENES, true);
        sceneBank = host.createSceneBank(MAX_SCENES);

        initializeOscDisplayPages();

        if (displayWindowShowSetting.get()) {
            try {
                bitXGraphics.startDisplayProcess();
            } catch (Exception e) {
                host.errorln("Failed to start display process: " + e.getMessage());
            }
        }
        pushDisplaySnapshot(cursorRemoteControlsPage, DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR);

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

        selectedCursorTrack = host.createCursorTrack("RemoteControlsTrack", "Selected Track", 0, 0, true);
        cursorDevice = selectedCursorTrack.createCursorDevice(
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

        cursorDevice.addDirectParameterNormalizedValueObserver((id, normalizedValue) -> {
            if (!Float.isNaN((float) normalizedValue)) {
                directParamValues.put(id, (float) normalizedValue); // normalizedValue is 0..1
            }
        });


        launcherCursorClip = selectedCursorTrack.createLauncherCursorClip(16 * 8, 128);
        cursorRemoteControlsPage = cursorDevice.createCursorRemoteControlsPage(8);

        cursorRemoteControlsPages = new CursorRemoteControlsPage[MAX_TRACKS][MAX_LAYERS];
        fxCursorRemoteControlsPages = new CursorRemoteControlsPage[MAX_TRACKS][MAX_LAYERS];

        registerDisplayPage(cursorRemoteControlsPage, DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR);
        selectedCursorTrack.position().addValueObserver(position -> {
            activeDisplaySource = DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR;
            pushDisplaySnapshot(cursorRemoteControlsPage, DISPLAY_SOURCE_SELECTED_DEVICE_CURSOR);
        });

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


            //DrumRacks

            final UUID drumRackUUID = UUID.fromString("8ea97e45-0255-40fd-bc7e-94419741e9d1");

            DeviceBank drumRackBank = track.createDeviceBank(16);
            drumRackBank.setDeviceMatcher(host.createBitwigDeviceMatcher(drumRackUUID));
            drumRackDeviceBanks[i] = drumRackBank;

            Device dr = drumRackBank.getDevice(0);
            dr.exists().markInterested();
//            dr.name().markInterested();
//            int finalI = i;
//            dr.name().addValueObserver(name ->
//                    host.println("Track " + finalI + " DrumRack match: " + name + " (exists=" + dr.exists().get() + ")")
//            );

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

        List<BitXFunctions.OscOutputConfig> oscOutputs = new ArrayList<>();
        oscOutputs.add(new BitXFunctions.OscOutputConfig(
                oscOutName,
                oscOutHost,
                oscOutPort,
                oscOutEnabled,
                Arrays.asList(oscOutName, "1", "out1", "output1", "output 1")
        ));
        oscOutputs.add(new BitXFunctions.OscOutputConfig(
                oscOut2Name,
                oscOut2Host,
                oscOut2Port,
                oscOut2Enabled,
                Arrays.asList(oscOut2Name, "2", "out2", "output2", "output 2")
        ));
        oscOutputs.add(new BitXFunctions.OscOutputConfig(
                oscOut3Name,
                oscOut3Host,
                oscOut3Port,
                oscOut3Enabled,
                Arrays.asList(oscOut3Name, "3", "out3", "output3", "output 3")
        ));
        String defaultOutputName = resolveOutputName(defaultOscOutputName);
        torsoOscOutputName = resolveOutputName(torsoOscOutputName);

        bitXFunctions = new BitXFunctions(
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
                drumRackDeviceBanks,
                trackLayerNames,
                fxTrackLayerNames,
                cursorRemoteControlsPages,
                fxCursorRemoteControlsPages,
                trackChannelFilterParameters,
                trackNoteFilterParameters,
                trackNoteTransposeParameters,
                mpcParameters,
                oscOutputs,
                defaultOutputName,
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
                "OSC: Sends an OSC message. Usage: ()OSC /address arg1 arg2 ... or ()OSC <outputName> /address arg1 arg2 ..."
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

        // Random amount dropdown: 10..100
        String[] amounts = new String[10];
        for (int i = 0; i < 10; i++) amounts[i] = String.valueOf((i + 1) * 10);

        randomAmountSetting = documentState.getEnumSetting(
                "Random amount",   // label
                "BitX",            // group/section
                amounts,
                "10"              // default
        );

        randomAmountSetting.addValueObserver(val -> {
            try {
                randomAmount = Integer.parseInt(val);
                getHost().println("BitX: Random amount = " + randomAmount);
            } catch (Exception ignored) {
                randomAmount = 10;
            }
        });


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

    private void registerOscHandlers() {
        oscAddressSpace.registerMethod(
                OSC_TORSO_T1_ADDRESS,
                "*",
                "Torso T-1 CC",
                (source, message) -> handleTorsoT1Osc(message)
        );
    }

    private String resolveOutputName(String selection) {
        if ("Output 2".equalsIgnoreCase(selection)) {
            return oscOut2Name;
        }
        if ("Output 3".equalsIgnoreCase(selection)) {
            return oscOut3Name;
        }
        return oscOutName;
    }

    private void refreshOscServer() {
        getHost().println("BitX: OSC in enabled=" + oscInEnabled + " port=" + oscInPort);
        if (oscServer == null) {
            getHost().println("BitX: OSC server not initialized.");
            return;
        }
        if (!oscInEnabled) {
            getHost().println("BitX: OSC server disabled.");
            return;
        }
        if (oscInPort <= 0) {
            getHost().println("BitX: OSC server port invalid.");
            return;
        }
        try {
            getHost().println("BitX: Starting OSC server on port " + oscInPort);
            oscServer.start(oscInPort);
        } catch (Exception e) {
            getHost().errorln("BitX: Failed to start OSC server on port " + oscInPort + ": " + e.getMessage());
        }
    }

    private void notifyOscRestartRequired() {
        getHost().println("BitX: OSC output settings updated; restart controller to apply.");
    }

    private void initializeOscDisplayPages() {
        trackSelectedDeviceCursors = new CursorDevice[MAX_TRACKS];
        trackSelectedDevicePages = new CursorRemoteControlsPage[MAX_TRACKS];
        trackRemotePages = new CursorRemoteControlsPage[MAX_TRACKS];

        for (int i = 0; i < MAX_TRACKS; i++) {
            Track track = trackBank.getItemAt(i);

            trackSelectedDeviceCursors[i] = track.createCursorDevice("BitX Track " + (i + 1) + " Selected Device");
            trackSelectedDevicePages[i] = trackSelectedDeviceCursors[i].createCursorRemoteControlsPage(8);
            registerDisplayPage(trackSelectedDevicePages[i], displaySourceSelectedDeviceTrack(i));

            trackRemotePages[i] = track.createCursorRemoteControlsPage(8);
            registerDisplayPage(trackRemotePages[i], displaySourceTrackRemote(i));
        }

        projectRemotePage = getHost().getProject()
                .getRootTrackGroup()
                .createCursorRemoteControlsPage(8);
        registerDisplayPage(projectRemotePage, displaySourceProjectRemote());
    }

    private void registerDisplayPage(CursorRemoteControlsPage page, String sourceKey) {
        if (page == null) {
            return;
        }
        page.getName().addValueObserver(name -> {
            if (!sourceKey.equals(activeDisplaySource) || bitXGraphics == null) {
                return;
            }
            bitXGraphics.sendDataToJavaFX("PAGE:" + name);
        });

        for (int i = 0; i < 8; i++) {
            int index = i;
            Parameter knob = page.getParameter(i);
            knob.exists().addValueObserver(exists -> {
                if (!sourceKey.equals(activeDisplaySource) || bitXGraphics == null) {
                    return;
                }
                if (!exists) {
                    bitXGraphics.sendDataToJavaFX("KNOB_NAME:" + index + ":0");
                    bitXGraphics.sendDataToJavaFX("KNOB_VALUE:" + index + ":0.0");
                }
            });
            knob.name().addValueObserver(name -> {
                if (!sourceKey.equals(activeDisplaySource) || bitXGraphics == null) {
                    return;
                }
                bitXGraphics.sendDataToJavaFX("KNOB_NAME:" + index + ":" + name);
            });
            knob.value().addValueObserver(value -> {
                if (!sourceKey.equals(activeDisplaySource) || bitXGraphics == null) {
                    return;
                }
                bitXGraphics.sendDataToJavaFX("KNOB_VALUE:" + index + ":" + value);
            });
        }
    }

    private void showDisplayPage(CursorRemoteControlsPage page, String sourceKey, int pageIndex) {
        if (page == null) {
            return;
        }
        activeDisplaySource = sourceKey;
        if (pageIndex >= 0) {
            page.selectedPageIndex().set(pageIndex);
        }
        pushDisplaySnapshot(page, sourceKey);
    }

    private void pushDisplaySnapshot(CursorRemoteControlsPage page, String sourceKey) {
        if (page == null || bitXGraphics == null || !sourceKey.equals(activeDisplaySource)) {
            return;
        }
        String pageName = page.getName().get();
        bitXGraphics.sendDataToJavaFX("PAGE:" + (pageName == null ? "" : pageName));

        for (int i = 0; i < 8; i++) {
            Parameter knob = page.getParameter(i);
            boolean exists = knob.exists().get();
            if (!exists) {
                bitXGraphics.sendDataToJavaFX("KNOB_NAME:" + i + ":0");
                bitXGraphics.sendDataToJavaFX("KNOB_VALUE:" + i + ":0.0");
                continue;
            }
            String name = knob.name().get();
            bitXGraphics.sendDataToJavaFX("KNOB_NAME:" + i + ":" + (name == null ? "" : name));
            bitXGraphics.sendDataToJavaFX("KNOB_VALUE:" + i + ":" + knob.value().get());
        }
    }

    private void handleTorsoT1Osc(OscMessage message) {
        if (!oscInEnabled) {
            return;
        }
        getHost().println("BitX OSC recv " + message.getAddressPattern() + " " + message.getArguments());
        if (bitXFunctions != null) {
            bitXFunctions.sendOscMessageToNamedOutput(
                    torsoOscOutputName,
                    message.getAddressPattern(),
                    message.getArguments()
            );
        }
        String targetType = readOscString(message, 3);
        int targetTrack = readOscInt(message, 4, 0);
        int targetPage = readOscInt(message, 5, 0);

        if (targetType == null || targetType.isEmpty()) {
            return;
        }

        switch (targetType.toLowerCase(Locale.ROOT)) {
            case OSC_TARGET_SELECTED_DEVICE:
                showSelectedDeviceForTrack(targetTrack);
                sendTorsoSnapshotForTrack(targetTrack);
                break;
            case OSC_TARGET_TRACK_REMOTE:
                showTrackRemoteForTrack(targetTrack, targetPage);
                sendTorsoSnapshotForTrackRemote(targetTrack, targetPage);
                break;
            case OSC_TARGET_PROJECT_REMOTE:
                showProjectRemote(targetPage);
                sendTorsoSnapshotForProjectRemote(targetPage);
                break;
            default:
                break;
        }
    }

    private void showSelectedDeviceForTrack(int targetTrackOneBased) {
        int trackIndex = targetTrackOneBased - 1;
        if (trackIndex < 0 || trackIndex >= MAX_TRACKS) {
            return;
        }
        showDisplayPage(
                trackSelectedDevicePages[trackIndex],
                displaySourceSelectedDeviceTrack(trackIndex),
                -1
        );
    }

    private void showTrackRemoteForTrack(int targetTrackOneBased, int targetPageOneBased) {
        int trackIndex = targetTrackOneBased - 1;
        if (trackIndex < 0 || trackIndex >= MAX_TRACKS) {
            return;
        }
        int pageIndex = targetPageOneBased - 1;
        showDisplayPage(
                trackRemotePages[trackIndex],
                displaySourceTrackRemote(trackIndex),
                pageIndex
        );
    }

    private void showProjectRemote(int targetPageOneBased) {
        int pageIndex = targetPageOneBased - 1;
        showDisplayPage(projectRemotePage, displaySourceProjectRemote(), pageIndex);
    }

    private void sendTorsoSnapshotForTrack(int targetTrackOneBased) {
        int trackIndex = targetTrackOneBased - 1;
        if (trackIndex < 0 || trackIndex >= MAX_TRACKS) {
            return;
        }
        sendTorsoOscSnapshot(trackSelectedDevicePages[trackIndex], -1);
    }

    private void sendTorsoSnapshotForTrackRemote(int targetTrackOneBased, int targetPageOneBased) {
        int trackIndex = targetTrackOneBased - 1;
        if (trackIndex < 0 || trackIndex >= MAX_TRACKS) {
            return;
        }
        int pageIndex = targetPageOneBased - 1;
        sendTorsoOscSnapshot(trackRemotePages[trackIndex], pageIndex);
    }

    private void sendTorsoSnapshotForProjectRemote(int targetPageOneBased) {
        int pageIndex = targetPageOneBased - 1;
        sendTorsoOscSnapshot(projectRemotePage, pageIndex);
    }

    private void sendTorsoOscSnapshot(CursorRemoteControlsPage page, int pageIndex) {
        if (page == null || bitXFunctions == null) {
            return;
        }
        if (pageIndex >= 0) {
            page.selectedPageIndex().set(pageIndex);
        }
        String pageName = page.getName().get();
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(pageName == null ? "" : pageName);
        bitXFunctions.sendOscMessageToNamedOutput(torsoOscOutputName, OSC_TORSO_PAGE_ADDRESS, pageArgs);

        for (int i = 0; i < 8; i++) {
            Parameter knob = page.getParameter(i);
            boolean exists = knob.exists().get();
            String name = exists ? knob.name().get() : "0";
            float value = exists ? (float) knob.value().get() : 0.0f;
            List<Object> args = new ArrayList<>();
            args.add(value);
            args.add(name == null ? "" : name);
            bitXFunctions.sendOscMessageToNamedOutput(
                    torsoOscOutputName,
                    OSC_TORSO_KNOB_ADDRESS + (i + 1),
                    args
            );
        }
    }

    private String displaySourceSelectedDeviceTrack(int trackIndex) {
        return "selected-device-track-" + trackIndex;
    }

    private String displaySourceTrackRemote(int trackIndex) {
        return "track-remote-" + trackIndex;
    }

    private String displaySourceProjectRemote() {
        return "project-remote";
    }

    private int readOscInt(OscMessage message, int index, int defaultValue) {
        try {
            Object value = message.getArguments().get(index);
            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }
            if (value instanceof Float) {
                return Math.round((Float) value);
            }
            if (value instanceof Double) {
                return (int) Math.round((Double) value);
            }
            if (value instanceof String) {
                return Integer.parseInt(((String) value).trim());
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private String readOscString(OscMessage message, int index) {
        try {
            Object value = message.getArguments().get(index);
            if (value instanceof String) {
                return (String) value;
            }
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void randomizeSelectedDevice() {
        if (cursorDevice == null || directParameterIds.isEmpty()) {
            getHost().println("BitX: No device or no parameters to randomize.");
            return;
        }

        final int resolution = 16384;

        // amount 10..100 -> 0.10 .. 1.00
        final double amt = Math.max(10, Math.min(100, randomAmount)) / 100.0;

        for (String id : directParameterIds) {
            // current value [0..1] (fallback 0.5 if unknown yet)
            float current = directParamValues.getOrDefault(id, 0.5f);

            // random target [0..1]
            double rnd = random.nextDouble();

            // blend: current -> random by amt
            double out = (1.0 - amt) * current + amt * rnd;

            int step = (int) Math.round(out * (resolution - 1));
            cursorDevice.setDirectParameterValueNormalized(id, step, resolution);
        }

        getHost().showPopupNotification(
                "BitX: Randomized (" + randomAmount + "%) " + directParameterIds.size() + " params"
        );
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
