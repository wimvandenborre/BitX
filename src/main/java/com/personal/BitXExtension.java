package com.personal;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private DeviceBank[] deviceBanks;
    private DeviceLayerBank[] layerBanks;
    private ChainSelector[] chainSelectors;
    private DeviceBank[][] layerDeviceBanks;

    private Preferences prefs;
    private SettableRangedValue widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting, layerNumberSetting;

    private Map<String, CommandExecutor> commands = new HashMap<>();

    // Map to store track layer names for each track
    private Map<Integer, Map<String, Integer>> trackLayerNames = new HashMap<>();

    private BitXGraphics bitXGraphics;
    // private Process displayProcess;  // Removed: now handled by BitXGraphics

    protected BitXExtension(final BitXExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {

        final ControllerHost host = getHost();
        transport = host.createTransport();
        transport.tempo().value().addValueObserver(value -> {
            host.println("Initial tempo value (normalized): " + value);
        });

        drumPresetsPath = Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "Library", "Presets", "Drum Machine").toString();

        // Initialize preferences, including the new layer preference
        prefs = host.getPreferences();
        widthSetting = prefs.getNumberSetting("Bitmap Width", "Display", 40, 5000, 1, "pixels", 3024);
        heightSetting = prefs.getNumberSetting("Bitmap Height", "Display", 40, 1200, 1, "pixels", 120);
        tracknNumberSetting = prefs.getNumberSetting("Number of tracks", "Display", 1, 128, 1, "tracks", 32);
        sceneNumberSetting = prefs.getNumberSetting("Number of scenes", "Display", 1, 1024, 1, "scenes", 128);
        layerNumberSetting = prefs.getNumberSetting("Number of layers", "Display", 1, 64, 1, "layers", 32);

        int bitmapWidth = (int) widthSetting.getRaw();
        if (bitmapWidth == 0) bitmapWidth = 400;
        int bitmapHeight = (int) heightSetting.getRaw();
        if (bitmapHeight == 0) bitmapHeight = 120;
        MAX_TRACKS = (int) tracknNumberSetting.getRaw();
        if (MAX_TRACKS == 0) MAX_TRACKS = 32;
        MAX_SCENES = (int) sceneNumberSetting.getRaw();
        if (MAX_SCENES == 0) MAX_SCENES = 128;
        MAX_LAYERS = (int) layerNumberSetting.getRaw();

        // Initialize dynamic arrays based on preferences
        deviceBanks = new DeviceBank[MAX_TRACKS];
        layerBanks = new DeviceLayerBank[MAX_TRACKS];
        chainSelectors = new ChainSelector[MAX_TRACKS];
        layerDeviceBanks = new DeviceBank[MAX_TRACKS][MAX_LAYERS];

        //textWindowBitmap = host.createBitmap(bitmapWidth, bitmapHeight, BitmapFormat.ARGB32);
        trackBank = host.createTrackBank(MAX_TRACKS, 0, MAX_SCENES, true);
        // Initialize BitXGraphics instance for JavaFX communication
        bitXGraphics = new BitXGraphics(host);

        // 🔥 Mark volume values as "interested"
        for (int i = 0; i < 8; i++) {  // Only the first 8 tracks for faders
            Track track = trackBank.getItemAt(i);
            track.volume().markInterested();

            track.color().markInterested(); // Track color observation

            int finalJ = i;
            track.color().addValueObserver((r, g, b) -> {
                bitXGraphics.sendTrackColorData(finalJ, r, g, b);
            });

            // VU Meter Observer: Use 128 range and the sum of both channels
            int finalI = i;
            track.addVuMeterObserver(128, -1, false, newValue -> {
                bitXGraphics.sendVuMeterData(finalI, newValue);
            });

        }

        initializeLayersAndDevices(MAX_LAYERS);

        // The command functions have been moved to the CommandFunctions class.
        BitXFunctions bitXFunctions = new BitXFunctions(
                host,
                transport,
                drumPresetsPath,
                deviceBanks,
                layerBanks,
                chainSelectors,
                layerDeviceBanks,
                trackLayerNames
        );
        commands.put("SMW", (arg, trackIndex) -> bitXFunctions.displayTextInWindow(arg));
        commands.put("LDR", (arg, trackIndex) -> bitXFunctions.executeLDRCommand(arg, trackIndex));
        commands.put("BPM", (arg, trackIndex) -> bitXFunctions.setBpm(arg));
        commands.put("LIR", (arg, trackIndex) -> bitXFunctions.selectInstrumentInLayer(arg, trackIndex));
        commands.put("SPN", (arg, trackIndex) -> bitXFunctions.showPopupNotification(arg));

        initializeTrackAndClipObservers(host);

        try {
            bitXGraphics.startDisplayProcess();
        } catch (Exception e) {
            host.println("Error starting JavaFX app: " + e.getMessage());
        }

        host.showPopupNotification("BitX Initialized");
    }

    private void initializeLayersAndDevices(int maxLayers) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;
            trackLayerNames.put(trackIndex, new HashMap<>());
            Track track = trackBank.getItemAt(trackIndex);
            deviceBanks[trackIndex] = track.createDeviceBank(2);
            Device device = deviceBanks[trackIndex].getDevice(1);
            layerBanks[trackIndex] = device.createLayerBank(maxLayers);
            chainSelectors[trackIndex] = device.createChainSelector();

            for (int j = 0; j < maxLayers; j++) {
                final int layerIndex = j;
                DeviceLayer layer = layerBanks[trackIndex].getItemAt(layerIndex);
                layerDeviceBanks[trackIndex][layerIndex] = layer.createDeviceBank(1);
                layer.name().addValueObserver(layerName -> {
                    trackLayerNames.get(trackIndex).put(layerName, layerIndex);
                });
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
                                CommandExecutor executor = commands.get(cmd.command);
                                if (executor != null) {
                                    executor.execute(cmd.argument, trackIndex);
                                } else {
                                    host.println("Unknown command: " + cmd.command);
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
//        // Remove trailing comma & send to JavaFX
//        if (data.length() > 0) {
//            sendDataToJavaFX(data.substring(0, data.length() - 1)); // Remove last comma
//        }

        // TODO: Send any updates here
    }
}
