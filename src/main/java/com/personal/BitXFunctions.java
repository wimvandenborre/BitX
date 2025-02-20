package com.personal;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.api.ChainSelector;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.InsertionPoint;
import com.bitwig.extension.controller.api.Transport;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BitXFunctions {
    private final ControllerHost host;
    private final Transport transport;
    private final String drumPresetsPath;
    private final DeviceBank[] deviceBanks;
    private final DeviceLayerBank[] layerBanks;
    private final ChainSelector[] chainSelectors;
    private final DeviceBank[][] layerDeviceBanks;
    private final Map<Integer, Map<String, Integer>> trackLayerNames;
    private final CursorRemoteControlsPage[][] cursorRemoteControlsPages;
    private final Map<Integer, List<Parameter>> trackChannelFilterParameters; // ✅ Now accessible
    private final Map<Integer, List<Parameter>> trackNoteFilterParameters;

    public BitXFunctions(ControllerHost host,
                         Transport transport,
                         String drumPresetsPath,
                         DeviceBank[] deviceBanks,
                         DeviceBank[] channelFilterDeviceBanks,
                         DeviceLayerBank[] layerBanks,
                         ChainSelector[] chainSelectors,
                         DeviceBank[][] layerDeviceBanks,
                         Map<Integer, Map<String, Integer>> trackLayerNames,
                         CursorRemoteControlsPage[][] cursorRemoteControlsPages,
                         Map<Integer, List<Parameter>> trackChannelFilterParameters, Map<Integer, List<Parameter>> trackNoteFilterParameters) { // ✅ Add this
        this.host = host;
        this.transport = transport;
        this.drumPresetsPath = drumPresetsPath;
        this.deviceBanks = deviceBanks;
        this.layerBanks = layerBanks;
        this.chainSelectors = chainSelectors;
        this.layerDeviceBanks = layerDeviceBanks;
        this.trackLayerNames = trackLayerNames;
        this.cursorRemoteControlsPages = cursorRemoteControlsPages;
        this.trackChannelFilterParameters = trackChannelFilterParameters;
        this.trackNoteFilterParameters = trackNoteFilterParameters;// ✅ Store it
    }




    private int midiNoteFromString(String note) {
        Map<String, Integer> noteMap = new HashMap<>();
        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

        for (int octave = -2; octave <= 8; octave++) {
            for (int i = 0; i < notes.length; i++) {
                String key = notes[i] + octave;
                int midiNote = (octave + 2) * 12 + i;
                noteMap.put(key, midiNote);
            }
        }

        return noteMap.getOrDefault(note, -1);
    }

    public void changeNoteFilter(String arg, int trackIndex) {
        host.println("Changing Note Filter on Track " + trackIndex + " with args: " + arg);

        String[] args = arg.split(":");
        if (args.length != 2) {
            host.println("Invalid CNF format. Use: CNF C-2:G8");
            return;
        }

        int minNote = midiNoteFromString(args[0].trim());
        int maxNote = midiNoteFromString(args[1].trim());

        if (minNote == -1 || maxNote == -1) {
            host.println("Invalid note values in CNF command.");
            return;
        }

        if (minNote > maxNote) {
            host.println("Min note cannot be greater than max note. Swapping...");
            int temp = minNote;
            minNote = maxNote;
            maxNote = temp;
        }

        List<Parameter> noteFilterParams = trackNoteFilterParameters.get(trackIndex);
        if (noteFilterParams == null || noteFilterParams.size() < 2) {
            host.println("No Note Filter parameters found on Track " + trackIndex);
            return;
        }

        Parameter minKeyParam = noteFilterParams.get(0);
        Parameter maxKeyParam = noteFilterParams.get(1);

        minKeyParam.set(minNote / 127.0);
        maxKeyParam.set(maxNote / 127.0);


        host.println("Set Note Filter: MIN_KEY=" + minNote + " MAX_KEY=" + maxNote + " on Track " + trackIndex);
    }


    public void setChannelFilter(String arg, int trackIndex) {
        host.println("Setting Channel Filter for Track " + trackIndex + " with args: " + arg);

        List<Parameter> parameters = trackChannelFilterParameters.get(trackIndex);
        if (parameters == null || parameters.isEmpty()) {
            host.println("No channel filter parameters found for Track " + trackIndex);
            return;
        }

        // ✅ Reset all parameters to 0
        for (Parameter param : parameters) {
            param.set(0.0);
        }
        host.println("All channels disabled on Track " + trackIndex);

        // ✅ Parse the argument and enable selected channels
        String[] selectedChannels = arg.split(":");
        for (String channelStr : selectedChannels) {
            try {
                int channelIndex = Integer.parseInt(channelStr.trim()) - 1; // Convert to zero-based index
                if (channelIndex >= 0 && channelIndex < parameters.size()) {
                    parameters.get(channelIndex).set(1.0);
                    host.println("Enabled SELECT_CHANNEL_" + (channelIndex + 1) + " on Track " + trackIndex);
                } else {
                    host.println("Invalid channel number: " + (channelIndex + 1));
                }
            } catch (NumberFormatException e) {
                host.println("Error parsing channel number: " + channelStr);
            }
        }

        host.println("Finished setting Channel Filter for Track " + trackIndex);
    }


    public void displayTextInWindow(String text) {
        sendDataToJavaFX("CLIP:" + text); // Send clip name

        // private void displayTextInWindow(ControllerHost host, Bitmap myBitmap, String text)

        //        final int width = myBitmap.getWidth();
        //        final int height = myBitmap.getHeight();
        //
        //        // 1) Render the bitmap's contents
        //        myBitmap.render(graphicsOutput -> {
        //            graphicsOutput.save();
        //            try {
        //                graphicsOutput.setOperator(GraphicsOutput.Operator.SOURCE);
        //                graphicsOutput.setColor(1.0, 0.0, 0.0, 1.0); // Bright red background
        //                graphicsOutput.rectangle(0, 0, width, height);
        //                graphicsOutput.fill();
        //                graphicsOutput.setFontSize(80.0);
        //
        //                // Set a color for the text (yellowish)
        //                graphicsOutput.setColor(0.9, 1.0, 0.0, 1.0);
        //                graphicsOutput.moveTo(10, 60);
        //                graphicsOutput.showText(text);
        //
        //
        //            } finally {
        //                graphicsOutput.restore();
        //            }
        //        });
        //
        //        myBitmap.setDisplayWindowTitle("Info");
        //        myBitmap.showDisplayWindow();
    }

    public void setBpm(String bpmString) {
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

            host.println("BPM set to: " + targetBpm + " (Normalized: " + targetNormalizedValue + ")");
        } catch (NumberFormatException e) {
            host.println("Invalid BPM value: " + bpmString);
        }
    }

    public void executeLDRCommand(String presetName, int trackIndex) {
        String drumFile = Paths.get(drumPresetsPath, presetName + ".bwpreset").toString();
        host.println("Loading preset file: " + drumFile);

        File file = new File(drumFile);
        if (!file.exists()) {
            host.println("Error: Preset file does not exist: " + drumFile);
            return;
        }

        DeviceBank trackDeviceBank = deviceBanks[trackIndex];
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

    public void selectInstrumentInLayer(String commandArgs, int trackIndex) {
        String[] parts = commandArgs.split(":");
        String instrumentName = parts[0].trim();

        // Validate and adjust the page number (User inputs 1-based, so we subtract 1)
        int remotePageIndex = (parts.length > 1) ? validatePageNumber(parts[1].trim()) - 1 : -1;

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

            // Select the device in the selected layer
            Device deviceForSelectedLayer = layerDeviceBanks[trackIndex][layerIndex].getDevice(0);
            if (deviceForSelectedLayer != null) {
                deviceForSelectedLayer.selectInEditor();
                host.println("Instrument layer selected: " + instrumentName);

                //Use pre-initialized CursorRemoteControlsPage
                CursorRemoteControlsPage cursorPage = cursorRemoteControlsPages[trackIndex][layerIndex];

                if (cursorPage != null && remotePageIndex >= 0) {
                    int totalPages = cursorPage.pageCount().get(); //Safe to access

                    if (remotePageIndex < totalPages) {
                        cursorPage.selectedPageIndex().set(remotePageIndex);
                        host.println("Remote Controls Page selected: " + (remotePageIndex + 1)); // Show 1-based page number
                    } else {
                        host.println("⚠Error: Remote Controls Page " + (remotePageIndex + 1) + " does not exist.");
                    }
                }
            } else {
                host.println("No device found in the selected layer for instrument: " + instrumentName);
            }
        } else {
            host.println("Error: Instrument layer not found: " + instrumentName);
        }
    }

    public void showPopupNotification(String text) {
        host.showPopupNotification(text);
    }

    // Helper method to send data to the JavaFX display application.
    private void sendDataToJavaFX(String message) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println(message);
        } catch (Exception e) {
            host.println("❌ Error sending data to JavaFX: " + e.getMessage());
        }
    }


    private int validatePageNumber(String input) {
        try {
            int pageNumber = Integer.parseInt(input.trim());
            return Math.max(pageNumber, 1); // Ensure it's at least 1
        } catch (NumberFormatException e) {
            host.println("⚠️ Invalid page number input: " + input + ". Defaulting to page 1.");
            return 1; // Default to first page
        }
    }


}
