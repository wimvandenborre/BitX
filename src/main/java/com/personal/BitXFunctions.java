package com.personal;

import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.api.ChainSelector;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.*;

public class BitXFunctions {
    private final ControllerHost host;
    private OscConnection oscSender;
    private String oscIp;
    private int oscPort;
    private final Transport transport;
    private final String drumPresetsPath;
    private final DeviceBank[] deviceBanks;
    private final DeviceLayerBank[] layerBanks;
    private final ChainSelector[] chainSelectors;
    private final DeviceBank[][] layerDeviceBanks;
    private final Map<Integer, Map<String, Integer>> trackLayerNames;
    private final CursorRemoteControlsPage[][] cursorRemoteControlsPages;
    private final Map<Integer, List<Parameter>> trackChannelFilterParameters;
    private final Map<Integer, List<Parameter>> trackNoteFilterParameters;
    private final Map<Integer, List<Parameter>> trackNoteTransposeParameters;
    private final TrackBank trackBank;
    private final SceneBank sceneBank;
    private final int maxTracks;
    private final int maxScenes;
    private final Map<Integer, String> cachedTrackNames;
    private final CursorTrack followCursorTrack;
    private Timer bpmTransitionTimer;
    private final PinnableCursorClip launcherCursorClip;

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
                         Map<Integer, List<Parameter>> trackChannelFilterParameters,
                         Map<Integer, List<Parameter>> trackNoteFilterParameters,
                         Map<Integer, List<Parameter>> trackNoteTransposeParameters,
                         String oscIp,
                         int oscPort,
                         TrackBank trackBank,
                         SceneBank sceneBank,
                         int maxTracks,
                         int maxScenes,
                         Map<Integer, String> cachedTrackNames,
                         CursorTrack followCursorTrack, PinnableCursorClip launcherCursorClip) {
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
        this.trackNoteFilterParameters = trackNoteFilterParameters;
        this.trackNoteTransposeParameters = trackNoteTransposeParameters;
        this.oscIp = oscIp;
        this.oscPort = oscPort;
        this.trackBank = trackBank;
        this.sceneBank = sceneBank;
        this.maxTracks = maxTracks;
        this.maxScenes = maxScenes;
        this.cachedTrackNames = cachedTrackNames;
        this.followCursorTrack = followCursorTrack;
        this.launcherCursorClip = launcherCursorClip;
        reconnectOscSender();
    }

    private void reconnectOscSender() {
        host.println("Reconnecting OSC sender to " + oscIp + ":" + oscPort);
        OscModule oscModule = host.getOscModule();
        oscSender = oscModule.connectToUdpServer(oscIp, oscPort, null);
    }

    public void sendOSCMessage(String arg) {
        if (arg.isEmpty()) {
            host.println("OSC command requires an address and arguments.");
            return;
        }

        String[] parts = arg.split(" ");
        if (parts.length < 1) {
            host.println("Invalid OSC command format.");
            return;
        }

        String address = parts[0];
        Object[] arguments = new Object[parts.length - 1];

        for (int i = 1; i < parts.length; i++) {
            try {
                arguments[i - 1] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e1) {
                try {
                    arguments[i - 1] = Float.parseFloat(parts[i]);
                } catch (NumberFormatException e2) {
                    arguments[i - 1] = parts[i];
                }
            }
        }

        host.println("Sending OSC to " + oscIp + ":" + oscPort + " → " + address + " " + Arrays.toString(arguments));
        try {
            oscSender.sendMessage(address, arguments);
        } catch (IOException e) {
            host.println("Error sending OSC message: " + e.getMessage());
        }
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

    public void setNoteFilter(String arg, int trackIndex) {
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

        for (Parameter param : parameters) {
            param.set(0.0);
        }
        host.println("All channels disabled on Track " + trackIndex);

        String[] selectedChannels = arg.split(":");
        for (String channelStr : selectedChannels) {
            try {
                int channelIndex = Integer.parseInt(channelStr.trim()) - 1;
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

    public void setNoteTranspose(String arg, int trackIndex) {
        host.println("Changing Note Transpose on Track " + trackIndex + " with args: " + arg);

        String[] parts = arg.split(":");
        double octave = 0.0, coarse = 0.0, fine = 0.0;

        try {
            if (parts.length == 3) {
                octave = Double.parseDouble(parts[0].trim());
                coarse = Double.parseDouble(parts[1].trim());
                fine = Double.parseDouble(parts[2].trim());
            } else if (parts.length == 2) {
                octave = Double.parseDouble(parts[0].trim());
                coarse = Double.parseDouble(parts[1].trim());
            } else if (parts.length == 1) {
                octave = Double.parseDouble(parts[0].trim());
            } else {
                host.println("Invalid transpose format. Use: CNF <octave>:<coarse>:<fine>");
                return;
            }
        } catch (NumberFormatException e) {
            host.println("Invalid number format in transpose command: " + e.getMessage());
            return;
        }

        if (octave < -3 || octave > 3) {
            host.println("Octave value must be between -3 and +3.");
            return;
        }
        if (coarse < -48 || coarse > 48) {
            host.println("Coarse value must be between -96 and +96.");
            return;
        }
        if (fine < -100 || fine > 100) {
            host.println("Fine value must be between -200 and +200.");
            return;
        }

        double normalizedOctave = (3 - octave) / 6.0;
        double normalizedCoarse = (coarse + 48) / 96.0;
        double normalizedFine = (fine + 100) / 200.0;

        List<Parameter> transposeParams = trackNoteTransposeParameters.get(trackIndex);
        if (transposeParams == null || transposeParams.isEmpty()) {
            host.println("No Note Transpose parameters found on Track " + trackIndex);
            return;
        }

        if (transposeParams.size() >= 3) {
            transposeParams.get(0).set(normalizedOctave);
            transposeParams.get(1).set(normalizedCoarse);
            transposeParams.get(2).set(normalizedFine);
        } else if (transposeParams.size() == 2) {
            transposeParams.get(0).set(normalizedOctave);
            transposeParams.get(1).set(normalizedCoarse);
        } else {
            transposeParams.get(0).set(normalizedOctave);
        }

        host.println("Set Note Transpose on Track " + trackIndex +
                ": Octave=" + octave + " (norm: " + normalizedOctave + "), " +
                "Coarse=" + coarse + " (norm: " + normalizedCoarse + "), " +
                "Fine=" + fine + " (norm: " + normalizedFine + ").");
    }

    public void displayTextInWindow(String text) {
        sendDataToJavaFX("CLIP:" + text);
    }

    public void setBpm(String bpmCommand) {
        String[] parts = bpmCommand.split(":");
        if (parts.length == 0 || parts.length > 2) {
            host.println("Invalid BPM command. Usage: ()BPM <targetBPM>:<bars>");
            return;
        }

        try {
            double targetBpm = Double.parseDouble(parts[0].trim());
            double transitionBars = (parts.length == 2) ? Double.parseDouble(parts[1].trim()) : 0;

            double minBpm = 20.0;
            double maxBpm = 666.0;
            targetBpm = Math.max(minBpm, Math.min(maxBpm, targetBpm));

            double currentBpm = transport.tempo().value().get() * (maxBpm - minBpm) + minBpm;

            if (transitionBars <= 0) {
                transport.tempo().value().set((targetBpm - minBpm) / (maxBpm - minBpm));
                host.println("🎵 BPM instantly set to: " + targetBpm);
                return;
            }

            int beatsPerBar = transport.timeSignature().numerator().get();
            int denominator = transport.timeSignature().denominator().get();

            double beatsPerWholeNote = 4.0 / denominator;
            double adjustedBeatsPerBar = beatsPerBar * beatsPerWholeNote;

            smoothBpmTransition(currentBpm, targetBpm, transitionBars, adjustedBeatsPerBar, minBpm, maxBpm);
        } catch (NumberFormatException e) {
            host.println("Invalid BPM or bars value. Usage: ()BPM <targetBPM>:<bars>");
        }
    }

    private void smoothBpmTransition(double startBpm, double endBpm, double bars, double beatsPerBar, double minBpm, double maxBpm) {
        if (bpmTransitionTimer != null) {
            bpmTransitionTimer.cancel();
        }

        bpmTransitionTimer = new Timer();
        double secondsPerBar = (60.0 / startBpm) * beatsPerBar;
        double totalDurationSeconds = bars * secondsPerBar;

        int steps = 100;
        double interval = Math.max(totalDurationSeconds / steps * 1000, 10);
        double bpmStep = Math.max((endBpm - startBpm) / steps, 0.01);

        host.println("🔄 Gradually changing BPM from " + startBpm + " to " + endBpm +
                " over " + bars + " bars (" + beatsPerBar + " adjusted beats/bar).");

        TimerTask bpmTask = new TimerTask() {
            int currentStep = 0;
            double currentBpm = startBpm;

            @Override
            public void run() {
                if (currentStep >= steps) {
                    transport.tempo().value().set((endBpm - minBpm) / (maxBpm - minBpm));
                    host.println("✅ BPM transition complete at " + endBpm);
                    bpmTransitionTimer.cancel();
                    return;
                }

                currentBpm += bpmStep;
                double normalizedBpm = (currentBpm - minBpm) / (maxBpm - minBpm);
                transport.tempo().value().set(normalizedBpm);
                currentStep++;
            }
        };

        bpmTransitionTimer.schedule(bpmTask, 0, (long) interval);
    }

    public void setTimeSignature(String arg) {
        String[] parts = arg.split(":");
        if (parts.length != 2) {
            host.println("Invalid STS command format. Usage: ()STS <numerator>:<denominator>");
            return;
        }

        try {
            int numerator = Integer.parseInt(parts[0].trim());
            int denominator = Integer.parseInt(parts[1].trim());

            if (numerator < 1 || numerator > 32 || denominator < 1 || (denominator & (denominator - 1)) != 0) {
                host.println("Invalid time signature. Numerator: 1-32, Denominator: 1,2,4,8,16,32.");
                return;
            }

            transport.timeSignature().numerator().set(numerator);
            transport.timeSignature().denominator().set(denominator);
            host.println("Time Signature set to: " + numerator + "/" + denominator);
        } catch (NumberFormatException e) {
            host.println("Invalid time signature format. Usage: ()STS <numerator>:<denominator>");
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

            Device deviceForSelectedLayer = layerDeviceBanks[trackIndex][layerIndex].getDevice(0);
            if (deviceForSelectedLayer != null) {
                deviceForSelectedLayer.selectInEditor();
                host.println("Instrument layer selected: " + instrumentName);

                CursorRemoteControlsPage cursorPage = cursorRemoteControlsPages[trackIndex][layerIndex];
                if (cursorPage != null && remotePageIndex >= 0) {
                    int totalPages = cursorPage.pageCount().get();
                    if (remotePageIndex < totalPages) {
                        cursorPage.selectedPageIndex().set(remotePageIndex);
                        host.println("Remote Controls Page selected: " + (remotePageIndex + 1));
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

    private void sendDataToJavaFX(String message) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println(message);
        } catch (Exception e) {
            host.println("❌ Error sending data to JavaFX: " + e.getMessage());
        }
    }

    public void jumpToClipLauncherRectangleByName(String arg) {
        String[] parts = arg.split(":", 2);
        if (parts.length != 2) {
            host.println("Invalid JUMPTO format. Use: ()JUMPTO <trackName>:<clipName>");
            return;
        }
        String targetTrackName = parts[0].trim();
        String targetClipName = parts[1].trim();

        int trackIndex = -1;
        for (Map.Entry<Integer, String> e : cachedTrackNames.entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(targetTrackName)) {
                trackIndex = e.getKey();
                break;
            }
        }
        if (trackIndex == -1) {
            host.println("Track not found: " + targetTrackName);
            return;
        }

        Track track = trackBank.getItemAt(trackIndex);
        if (track == null) {
            host.println("Track is null at index: " + trackIndex);
            return;
        }

        ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
        int foundScene = -1;
        for (int s = 0; s < maxScenes; s++) {
            ClipLauncherSlot slot = slotBank.getItemAt(s);
            String clipName = slot.name().get();
            if (clipName != null && clipName.equalsIgnoreCase(targetClipName)) {
                foundScene = s;
                break;
            }
        }
        if (foundScene == -1) {
            host.println("Clip not found: " + targetClipName + " on track " + targetTrackName);
            return;
        }

        // Move the focus rectangle by selecting the slot




        int sceneWindowSize = sceneBank.getSizeOfBank();
        int scenePage  = foundScene / sceneWindowSize;
        int sceneSlot  = foundScene % sceneWindowSize;
        sceneBank.setIndication(true);
        sceneBank.scrollByPages(scenePage);
        sceneBank.scrollBy(sceneSlot);
        sceneBank.scrollIntoView(foundScene);
        sceneBank.getScene(foundScene);

        track.selectInMixer();
        track.selectInEditor();
        slotBank.getItemAt(foundScene).select();
        slotBank.getItemAt(foundScene).showInEditor();


        // sceneBank.scrollByPages(5);


        host.showPopupNotification("Jumped to \"" + targetTrackName + "\" / \"" + targetClipName + "\"");
    }



    private int validatePageNumber(String input) {
        try {
            int pageNumber = Integer.parseInt(input.trim());
            return Math.max(pageNumber, 1);
        } catch (NumberFormatException e) {
            host.println("⚠️ Invalid page number input: " + input + ". Defaulting to page 1.");
            return 1;
        }
    }
}
