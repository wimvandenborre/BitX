package com.personal;

import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.nio.file.Paths;
import com.bitwig.extension.api.graphics.Bitmap;
import com.bitwig.extension.api.graphics.BitmapFormat;
import com.bitwig.extension.api.graphics.GraphicsOutput;
import com.bitwig.extension.api.graphics.Renderer;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

public class BitXExtension extends ControllerExtension {
    private static final int MAX_TRACKS = 16;
    private static final int MAX_SCENES = 128;
    private static final int MAX_LAYERS = 16;

    private TrackBank trackBank;
    private Bitmap textWindowBitmap;
    private String drumPresetsPath;
    private Transport transport;

    private DeviceBank[] deviceBanks = new DeviceBank[MAX_TRACKS];
    private DeviceLayerBank[] layerBanks = new DeviceLayerBank[MAX_TRACKS];
    private ChainSelector[] chainSelectors = new ChainSelector[MAX_TRACKS];

    private Preferences prefs;
    private SettableRangedValue widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting, layerNumberSetting;

    private Map<String, CommandExecutor> commands = new HashMap<>();

    // Map to store track layer names for each track
    private Map<Integer, Map<String, Integer>> trackLayerNames = new HashMap<>();

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
        heightSetting = prefs.getNumberSetting("Bitmap Height", "Display", 40, 5000, 1, "pixels", 120);
        tracknNumberSetting = prefs.getNumberSetting("Number of tracks", "Display", 1, 128, 1, "tracks", 32);
        sceneNumberSetting = prefs.getNumberSetting("Number of scenes", "Display", 1, 1024, 1, "scenes", 128);
        layerNumberSetting = prefs.getNumberSetting("Number of layers", "Display", 1, 64, 1, "layers", MAX_LAYERS);

        int bitmapWidth = (int) widthSetting.getRaw();
        if (bitmapWidth == 0) bitmapWidth = 3024;
        int bitmapHeight = (int) heightSetting.getRaw();
        if (bitmapHeight == 0) bitmapHeight = 120;
        int maxTracks = (int) tracknNumberSetting.getRaw();
        if (maxTracks == 0) maxTracks = 32;
        int maxScenes = (int) sceneNumberSetting.getRaw();
        if (maxScenes == 0) maxScenes = 128;
        int maxLayers = (int) layerNumberSetting.getRaw();

        textWindowBitmap = host.createBitmap(bitmapWidth, bitmapHeight, BitmapFormat.RGB24_32);
        trackBank = host.createTrackBank(maxTracks, 0, maxScenes, true);

        // Initialize device and layer structures with dynamic layer count
        initializeLayersAndDevices(maxLayers);

        commands.put("SMW", (arg, trackIndex) -> displayTextInWindow(host, textWindowBitmap, arg));
        commands.put("LDR", (arg, trackIndex) -> executeLDRCommand(host, drumPresetsPath, arg, deviceBanks[trackIndex]));
        commands.put("BPM", (arg, trackIndex) -> setBpm(arg));
        commands.put("LIR", (arg, trackIndex) -> selectInstrumentInLayer(host, arg, trackIndex));

        initializeTrackAndClipObservers(host);
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

                // Observe layer name and store it in the map
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
                            String commandString = clipName.substring(2).trim();
                            if (!commandString.isEmpty()) {
                                CommandWithArgument commandWithArgument = parseCommandWithArgument(commandString);
                                CommandExecutor command = commands.get(commandWithArgument.command);

                                if (command != null) {
                                    command.execute(commandWithArgument.argument, trackIndex);
                                } else {
                                    host.println("Unknown command: " + commandWithArgument.command);
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private void selectInstrumentInLayer(ControllerHost host, String instrumentName, int trackIndex) {
        ChainSelector selector = chainSelectors[trackIndex];
        DeviceLayerBank layerBank = layerBanks[trackIndex];

        if (selector == null || layerBank == null) {
            host.println("Error: Selector or layer bank not found for track " + trackIndex);
            return;
        }

        Integer layerIndex = trackLayerNames.get(trackIndex).get(instrumentName);
        if (layerIndex != null) {
            selector.activeChainIndex().set(layerIndex);
            layerBank.getItemAt(layerIndex).selectInEditor();
            host.println("Instrument layer selected: " + instrumentName);
        } else {
            host.println("Error: Instrument layer not found: " + instrumentName);
        }
    }

    private void executeLDRCommand(ControllerHost host, String drumPresetsPath, String presetName, DeviceBank trackDeviceBank) {
        String drumFile = Paths.get(drumPresetsPath, presetName + ".bwpreset").toString();
        host.println("Loading preset file: " + drumFile);

        File file = new File(drumFile);
        if (!file.exists()) {
            host.println("Error: Preset file does not exist: " + drumFile);
            return;
        }

        Device replaceDrumRackDevice = trackDeviceBank.getDevice(1);
        if (replaceDrumRackDevice == null) {
            host.println("Error: replaceDrumRackDevice is null on this track");
            return;
        }

        InsertionPoint insertionPoint = replaceDrumRackDevice.replaceDeviceInsertionPoint();
        if (insertionPoint == null) {
            host.println("Error: insertionPoint is null");
            return;
        }

        insertionPoint.insertFile(drumFile);
        host.println("Inserted preset file: " + drumFile);
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

    @FunctionalInterface
    private interface CommandExecutor {
        void execute(String argument, int trackIndex);
    }

    private void displayTextInWindow(ControllerHost host, Bitmap myBitmap, String text) {
        int width = myBitmap.getWidth();
        int height = myBitmap.getHeight();

        myBitmap.render(new Renderer() {
            @Override
            public void render(GraphicsOutput graphicsOutput) {
                graphicsOutput.save();
                try {
                    graphicsOutput.setColor(0.0, 0.0, 0.0, 1.0);
                    graphicsOutput.rectangle(0, 0, width, height);
                    graphicsOutput.fill();

                    graphicsOutput.setColor(0.9, 1.0, 0.0, 1.0);
                    graphicsOutput.setFontSize(50);
                    graphicsOutput.moveTo(10, 40);
                    graphicsOutput.showText(text);
                } finally {
                    graphicsOutput.restore();
                }
            }
        });
        myBitmap.setDisplayWindowTitle("Info");
        myBitmap.showDisplayWindow();
    }

    private void setBpm(String bpmString) {
        try {
            double targetBpm = Double.parseDouble(bpmString); // Parse the target BPM from the argument

            // Define the actual min and max BPM range in Bitwig
            double minBpm = 20.0;
            double maxBpm = 666.0;

            // Ensure target BPM is within the allowed range
            targetBpm = Math.max(minBpm, Math.min(maxBpm, targetBpm));

            // Map target BPM to the normalized range [0.0, 1.0]
            double targetNormalizedValue = (targetBpm - minBpm) / (maxBpm - minBpm);

            // Apply the normalized tempo value explicitly to allow both increase and decrease
            transport.tempo().value().set(targetNormalizedValue);

            getHost().println("BPM set to: " + targetBpm + " (Normalized: " + targetNormalizedValue + ")");
        } catch (NumberFormatException e) {
            getHost().println("Invalid BPM value: " + bpmString);
        }
    }



    @Override
    public void exit() {
        getHost().showPopupNotification("BitX Exited");
    }

    @Override
    public void flush() {
        // TODO: Send any updates here
    }
}
