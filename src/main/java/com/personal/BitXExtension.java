package com.personal;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.bitwig.extension.api.graphics.Bitmap;
import com.bitwig.extension.api.graphics.BitmapFormat;
import com.bitwig.extension.api.graphics.FontOptions;
import com.bitwig.extension.api.graphics.GraphicsOutput;
import com.bitwig.extension.api.graphics.Renderer;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.InsertionPoint;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

public class BitXExtension extends ControllerExtension {
    private static int MAX_TRACKS = 16;
    private static int MAX_SCENES = 128;
    private TrackBank trackBank;
    private Bitmap textWindowBitmap;
    private String drumPresetsPath; // Class-level variable for drum preset path
    private Transport transport;

    // Preferences
    private Preferences prefs;
    private SettableRangedValue widthSetting, heightSetting, tracknNumberSetting, sceneNumberSetting;
   
    private Map<String, CommandExecutor> commands = new HashMap<>();
    private DeviceBank[] deviceBanks = new DeviceBank[MAX_TRACKS];

    protected BitXExtension(final BitXExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();
        transport = host.createTransport();
        transport.tempo().value().addValueObserver(value -> {
            // Optionally, print the current tempo for debugging purposes
            host.println("Initial tempo value (normalized): " + value);
        });
        drumPresetsPath = Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "Library", "Presets", "Drum Machine").toString();

        // Initialize preferences
        prefs = host.getPreferences();
        widthSetting = prefs.getNumberSetting("Bitmap Width", "Display", 40, 5000, 1, "pixels", 3024);
        heightSetting = prefs.getNumberSetting("Bitmap Height", "Display", 40, 5000, 1, "pixels", 120);
        tracknNumberSetting = prefs.getNumberSetting("Number of tracks", "Display", 1, 128, 1, "tracks", 32);
        sceneNumberSetting = prefs.getNumberSetting("Number of scenes", "Display", 1, 1024, 1, "scenes", 128);

        // Retrieve settings with defaults
        int bitmapWidth = (int) widthSetting.getRaw();
        if (bitmapWidth == 0) bitmapWidth = 3024; // Default width

        int bitmapHeight = (int) heightSetting.getRaw();
        if (bitmapHeight == 0) bitmapHeight = 120; // Default height

        int maxTracks = (int) tracknNumberSetting.getRaw();
        if (maxTracks == 0) maxTracks = 32; // Default number of tracks

        int maxScenes = (int) sceneNumberSetting.getRaw();
        if (maxScenes == 0) maxScenes = 128; // Default number of scenes

        // Create bitmap with dynamic or default size
        textWindowBitmap = host.createBitmap(bitmapWidth, bitmapHeight, BitmapFormat.RGB24_32);

        // Initialize track bank with dynamic or default track and scene count
        trackBank = host.createTrackBank(maxTracks, 0, maxScenes, true);

        // Initialize commands with both argument and trackIndex
        commands.put("SMW", (arg, trackIndex) -> displayTextInWindow(host, textWindowBitmap, arg));
        commands.put("LDR", (arg, trackIndex) -> executeLDRCommand(host, drumPresetsPath, arg, deviceBanks[trackIndex]));
        commands.put("BPM", (arg, trackIndex) -> setBpm(arg));
        // Track and Clip Observers
        initializeTrackAndClipObservers(host);

        host.showPopupNotification("BitX Initialized");
    }


    private void initializeTrackAndClipObservers(final ControllerHost host) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            final int trackIndex = i;
            Track track = trackBank.getItemAt(trackIndex);
            ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
            deviceBanks[trackIndex] = track.createDeviceBank(2); // Create a device bank for each track

            for (int slotIndex = 0; slotIndex < MAX_SCENES; slotIndex++) {
                final int finalSlotIndex = slotIndex; // Use a final variable for lambda
                ClipLauncherSlot clipSlot = clipLauncherSlotBank.getItemAt(finalSlotIndex);
                clipSlot.name().markInterested(); // Mark clip name as interested

                clipLauncherSlotBank.addIsPlayingObserver((index, isPlaying) -> {
                    if (index == finalSlotIndex && isPlaying) {
                        String clipName = clipSlot.name().get(); // Safe to get after marking interested
                        if (clipName.startsWith("()")) {
                            CommandWithArgument commandWithArgument = parseCommandWithArgument(clipName.substring(2).trim());
                            CommandExecutor command = commands.get(commandWithArgument.command);

                            if (command != null) {
                                // Pass both the command argument and the track index
                                command.execute(commandWithArgument.argument, trackIndex);
                            } else {
                                host.println("Unknown command: " + commandWithArgument.command);
                            }
                        }
                    }
                });
            }
        }
    }

    // Parses command and argument from clip name
    private CommandWithArgument parseCommandWithArgument(String commandString) {
        int spaceIndex = commandString.indexOf(" ");
        String command = (spaceIndex > 0) ? commandString.substring(0, spaceIndex).trim() : commandString;
        String argument = (spaceIndex > 0) ? commandString.substring(spaceIndex + 1).trim() : "";
        return new CommandWithArgument(command, argument);
    }

    // Command and argument wrapper class
    private static class CommandWithArgument {
        final String command;
        final String argument;

        CommandWithArgument(String command, String argument) {
            this.command = command;
            this.argument = argument;
        }
    }

    // Functional interface for command execution with argument and trackIndex
    @FunctionalInterface
    private interface CommandExecutor {
        void execute(String argument, int trackIndex);
    }

    // LDR command implementation
    private void executeLDRCommand(ControllerHost host, String drumPresetsPath, String presetName, DeviceBank trackDeviceBank) {
        String drumFile = Paths.get(drumPresetsPath, presetName + ".bwpreset").toString();
        host.println(drumFile);

        // Check if the file exists
        File file = new File(drumFile);
        if (!file.exists()) {
            host.println("Error: Preset file does not exist: " + drumFile);
            return;
        }

        // Get the device to be replaced from the track-specific DeviceBank
        Device replaceDrumRackDevice = trackDeviceBank.getDevice(1);
        if (replaceDrumRackDevice == null) {
            host.println("Error: replaceDrumRackDevice is null on this track");
            return;
        }

        // Get the insertion point for replacement
        InsertionPoint insertionPoint = replaceDrumRackDevice.replaceDeviceInsertionPoint();
        if (insertionPoint == null) {
            host.println("Error: insertionPoint is null");
            return;
        }

        // Insert the file
        insertionPoint.insertFile(drumFile);
        host.println("Inserted preset file: " + drumFile);
    }

    // SMW command implementation
    private void displayTextInWindow(ControllerHost host, Bitmap myBitmap, String text) {
        int width = myBitmap.getWidth();  // Extracting the width from the bitmap
        int height = myBitmap.getHeight(); // Extracting the height from the bitmap

        myBitmap.render(new Renderer() {
            @Override
            public void render(GraphicsOutput graphicsOutput) {
                graphicsOutput.save();
                try {
                    // Set the color and properties to clear the background
                    graphicsOutput.setColor(0.0, 0.0, 0.0, 1.0);  // Black background
                    graphicsOutput.rectangle(0, 0, width, height); // Draw a rectangle covering the entire bitmap
                    graphicsOutput.fill();  // Fill the rectangle to clear the previous text

                    // Set color and font for the new text
                    graphicsOutput.setColor(0.9, 1.0, 0.0, 1.0);  // Yellow text
                    graphicsOutput.setFontSize(50);
                    graphicsOutput.moveTo(10, 40);  // Position the text
                    graphicsOutput.showText(text);  // Draw the new text
                } finally {
                    graphicsOutput.restore();
                }
            }
        });

        myBitmap.setDisplayWindowTitle("Info");
        myBitmap.showDisplayWindow();
    }


    //SetBPM
    
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
