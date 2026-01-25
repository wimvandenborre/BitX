package com.personal;

import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BitXFunctions {
    private final ControllerHost host;
    private final Map<String, OscOutput> oscOutputs = new HashMap<>();
    private final Map<String, String> oscOutputAliases = new HashMap<>();
    private final String defaultOscOutput;
    private final Transport transport;
    private final String drumPresetsPath;

    private final DeviceBank[] deviceBanks;
    private final DeviceLayerBank[] layerBanks;
    private final ChainSelector[] chainSelectors;
    private final DeviceBank[][] layerDeviceBanks;

    // FX Selector
    private final DeviceLayerBank[] fxLayerBanks;
    private final ChainSelector[] fxChainSelectors;
    private final DeviceBank[][] fxLayerDeviceBanks;

    private final DeviceBank[] drumRackDeviceBanks;
    private final Map<Integer, Map<String, Integer>> trackLayerNames;
    private final Map<Integer, Map<String, Integer>> fxTrackLayerNames;

    private final CursorRemoteControlsPage[][] cursorRemoteControlsPages;
    private final CursorRemoteControlsPage[][] fxCursorRemoteControlsPages;

    private final Map<Integer, List<Parameter>> trackChannelFilterParameters;
    private final Map<Integer, List<Parameter>> trackNoteFilterParameters;
    private final Map<Integer, List<Parameter>> trackNoteTransposeParameters;
    private final Map<Integer, List<Parameter>> mpcParameters;

    private final TrackBank trackBank;
    private final SceneBank sceneBank;
    private final int maxTracks;
    private final int maxScenes;
    private final Map<Integer, String> cachedTrackNames;
    private final CursorTrack followCursorTrack;
    private final PinnableCursorClip launcherCursorClip;

    private Timer bpmTransitionTimer;

    public static final class OscOutputConfig {
        public final String name;
        public final String host;
        public final int port;
        public final boolean enabled;
        public final List<String> aliases;

        public OscOutputConfig(String name, String host, int port, boolean enabled, List<String> aliases) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.enabled = enabled;
            this.aliases = aliases == null ? Collections.emptyList() : aliases;
        }
    }

    private static final class OscOutput {
        private final String name;
        private final String host;
        private final int port;
        private final boolean enabled;
        private final OscConnection connection;

        private OscOutput(String name, String host, int port, boolean enabled, OscConnection connection) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.enabled = enabled;
            this.connection = connection;
        }
    }

    public BitXFunctions(ControllerHost host,
                         Transport transport,
                         String drumPresetsPath,
                         DeviceBank[] deviceBanks,
                         DeviceBank[] channelFilterDeviceBanks,
                         DeviceLayerBank[] layerBanks,
                         ChainSelector[] chainSelectors,
                         DeviceBank[][] layerDeviceBanks,
                         DeviceLayerBank[] fxLayerBanks,
                         ChainSelector[] fxChainSelectors,
                         DeviceBank[][] fxLayerDeviceBanks,
                         DeviceBank[] drumRackDeviceBanks,
                         Map<Integer, Map<String, Integer>> trackLayerNames,
                         Map<Integer, Map<String, Integer>> fxTrackLayerNames,
                         CursorRemoteControlsPage[][] cursorRemoteControlsPages,
                         CursorRemoteControlsPage[][] fxCursorRemoteControlsPages,
                         Map<Integer, List<Parameter>> trackChannelFilterParameters,
                         Map<Integer, List<Parameter>> trackNoteFilterParameters,
                         Map<Integer, List<Parameter>> trackNoteTransposeParameters,
                         Map<Integer, List<Parameter>> mpcParameters,
                         List<OscOutputConfig> oscOutputConfigs,
                         String defaultOscOutput,
                         TrackBank trackBank,
                         SceneBank sceneBank,
                         int maxTracks,
                         int maxScenes,
                         Map<Integer, String> cachedTrackNames,
                         CursorTrack followCursorTrack,
                         PinnableCursorClip launcherCursorClip) {

        this.host = host;
        this.transport = transport;
        this.drumPresetsPath = drumPresetsPath;
        this.deviceBanks = deviceBanks;
        this.layerBanks = layerBanks;
        this.chainSelectors = chainSelectors;
        this.layerDeviceBanks = layerDeviceBanks;

        this.fxLayerBanks = fxLayerBanks;
        this.fxChainSelectors = fxChainSelectors;
        this.fxLayerDeviceBanks = fxLayerDeviceBanks;
        this.drumRackDeviceBanks = drumRackDeviceBanks;

        this.trackLayerNames = trackLayerNames;
        this.fxTrackLayerNames = fxTrackLayerNames;

        this.cursorRemoteControlsPages = cursorRemoteControlsPages;
        this.fxCursorRemoteControlsPages = fxCursorRemoteControlsPages;

        this.trackChannelFilterParameters = trackChannelFilterParameters;
        this.trackNoteFilterParameters = trackNoteFilterParameters;
        this.trackNoteTransposeParameters = trackNoteTransposeParameters;
        this.mpcParameters = mpcParameters;

        this.defaultOscOutput = normalizeOscKey(defaultOscOutput);
        initializeOscOutputs(oscOutputConfigs);

        this.trackBank = trackBank;
        this.sceneBank = sceneBank;
        this.maxTracks = maxTracks;
        this.maxScenes = maxScenes;
        this.cachedTrackNames = cachedTrackNames;
        this.followCursorTrack = followCursorTrack;
        this.launcherCursorClip = launcherCursorClip;

    }

    private void initializeOscOutputs(List<OscOutputConfig> oscOutputConfigs) {
        if (oscOutputConfigs == null || oscOutputConfigs.isEmpty()) {
            return;
        }
        OscModule oscModule = host.getOscModule();
        for (OscOutputConfig config : oscOutputConfigs) {
            if (config == null) {
                continue;
            }
            String key = normalizeOscKey(config.name);
            String hostValue = config.host == null ? "" : config.host.trim();
            int portValue = config.port;
            if (!config.enabled || hostValue.isEmpty() || portValue <= 0) {
                oscOutputs.put(key, new OscOutput(config.name, hostValue, portValue, false, null));
                registerOscAliases(key, config.aliases);
                continue;
            }
            try {
                OscConnection connection = oscModule.connectToUdpServer(hostValue, portValue, null);
                oscOutputs.put(key, new OscOutput(config.name, hostValue, portValue, true, connection));
                registerOscAliases(key, config.aliases);
                host.println("OSC output ready: " + config.name + " -> " + hostValue + ":" + portValue);
            } catch (Exception e) {
                host.println("Error connecting OSC output " + config.name + ": " + e.getMessage());
                oscOutputs.put(key, new OscOutput(config.name, hostValue, portValue, false, null));
                registerOscAliases(key, config.aliases);
            }
        }
    }

    private void registerOscAliases(String key, List<String> aliases) {
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            String normalized = normalizeOscKey(alias);
            if (!normalized.isEmpty()) {
                oscOutputAliases.put(normalized, key);
            }
        }
    }

    private String resolveOscOutputKey(String name) {
        String normalized = normalizeOscKey(name);
        if (normalized.isEmpty()) {
            return defaultOscOutput;
        }
        String resolved = oscOutputAliases.get(normalized);
        return resolved == null ? normalized : resolved;
    }

    private String normalizeOscKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
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

        String outputName = defaultOscOutput;
        String address = parts[0];
        int argStart = 1;
        if (!address.startsWith("/")) {
            outputName = parts[0];
            if (parts.length < 2) {
                host.println("OSC command requires an address after the output name.");
                return;
            }
            address = parts[1];
            argStart = 2;
        }

        Object[] arguments = new Object[parts.length - argStart];
        for (int i = argStart; i < parts.length; i++) {
            try {
                arguments[i - argStart] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e1) {
                try {
                    arguments[i - argStart] = Float.parseFloat(parts[i]);
                } catch (NumberFormatException e2) {
                    arguments[i - argStart] = parts[i];
                }
            }
        }

        sendOscMessageToOutput(outputName, address, arguments);
    }

    private void sendOscMessageToOutput(String outputName, String address, Object[] arguments) {
        String key = resolveOscOutputKey(outputName);
        OscOutput output = oscOutputs.get(key);
        if (output == null) {
            host.println("OSC output not found: " + outputName);
            return;
        }
        if (!output.enabled || output.connection == null) {
            host.println("OSC output disabled: " + output.name);
            return;
        }
        host.println("Sending OSC to " + output.host + ":" + output.port + " (" + output.name + ") → " + address + " " + Arrays.toString(arguments));
        try {
            output.connection.sendMessage(address, arguments);
        } catch (IOException e) {
            host.println("Error sending OSC message: " + e.getMessage());
        }
    }

    public void sendOscMessageToNamedOutput(String outputName, String address, List<Object> arguments) {
        Object[] args = arguments == null ? new Object[0] : arguments.toArray();
        sendOscMessageToOutput(outputName, address, args);
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

    public void setMPC(String arg, int trackIndex) {
        host.println("Setting MPC for Track " + trackIndex + " with args: " + arg);

        List<Parameter> params = mpcParameters.get(trackIndex);
        if (params == null || params.size() < 4) {
            host.println("No MPC parameters found on Track " + trackIndex);
            return;
        }

        Parameter programParam  = params.get(0);
        Parameter bankMsbParam  = params.get(1);
        Parameter bankLsbParam  = params.get(2);
        Parameter channelParam  = params.get(3);

        String[] parts = arg.split(":");

        Integer program = null;
        Integer bankMsb = null;
        Integer bankLsb = null;
        Integer channel = null;

        try {
            if (parts.length > 0 && !parts[0].trim().isEmpty() && !parts[0].trim().equals("*")) {
                program = Integer.parseInt(parts[0].trim());
            }
            if (parts.length > 1 && !parts[1].trim().isEmpty() && !parts[1].trim().equals("*")) {
                bankMsb = Integer.parseInt(parts[1].trim());
            }
            if (parts.length > 2 && !parts[2].trim().isEmpty() && !parts[2].trim().equals("*")) {
                bankLsb = Integer.parseInt(parts[2].trim());
            }
            if (parts.length > 3 && !parts[3].trim().isEmpty() && !parts[3].trim().equals("*")) {
                channel = Integer.parseInt(parts[3].trim());
            }
        } catch (NumberFormatException e) {
            host.println("Invalid MPC number format in: " + arg + " (" + e.getMessage() + ")");
            return;
        }

        // --- PROGRAM (1–128 expected)
        if (program != null) {
            int adj = program - 1;
            if (adj < 0 || adj > 127) {
                host.println("MPC PROGRAM out of range (1–128): " + program);
            } else {
                double norm = adj / 127.0;
                programParam.setImmediately(norm);
                host.println("MPC PROGRAM set to " + program + " (adj " + adj + ", norm " + norm + ")");
            }
        }

        // --- BANK_MSB (1–128 expected)
        if (bankMsb != null) {
            int adj = bankMsb - 1;
            if (adj < 0 || adj > 127) {
                host.println("MPC BANK_MSB out of range (1–128): " + bankMsb);
            } else {
                double norm = adj / 127.0;
                bankMsbParam.setImmediately(norm);
                host.println("MPC BANK_MSB set to " + bankMsb + " (adj " + adj + ", norm " + norm + ")");
            }
        }

        // --- BANK_LSB (1–128 expected)
        if (bankLsb != null) {
            int adj = bankLsb - 1;
            if (adj < 0 || adj > 127) {
                host.println("MPC BANK_LSB out of range (1–128): " + bankLsb);
            } else {
                double norm = adj / 127.0;
                bankLsbParam.setImmediately(norm);
                host.println("MPC BANK_LSB set to " + bankLsb + " (adj " + adj + ", norm " + norm + ")");
            }
        }

        // --- CHANNEL (1–16 → 0–15)
        if (channel != null) {
            if (channel < 1 || channel > 16) {
                host.println("MPC CHANNEL out of range (1–16): " + channel);
            } else {
                int zeroBased = channel - 1;
                double norm = zeroBased / 15.0;
                channelParam.setImmediately(norm);
                host.println("MPC CHANNEL set to " + channel + " (zero-based " + zeroBased + ", norm " + norm + ")");
            }
        }
    }

    public void setMPCXD(String arg, int trackIndex) {
        arg = arg.trim();
        if (arg.isEmpty()) {
            host.println("MPCXD: No slot provided. Usage: ()MPCXD <1-500>");
            return;
        }

        int slot;
        try {
            slot = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            host.println("MPCXD: Invalid slot number: " + arg);
            return;
        }

        if (slot < 1 || slot > 500) {
            host.println("MPCXD: Slot out of range (1–500): " + slot);
            return;
        }

        int zeroBased = slot - 1;
        int subBank   = zeroBased / 100 + 1;
        int pc        = zeroBased % 100;

        int programArg  = pc + 1;
        int bankMsbArg  = 1;
        int bankLsbArg  = subBank;

        String mpcArgs = programArg + ":" + bankMsbArg + ":" + bankLsbArg + ":*";

        host.println("MPCXD slot " + slot +
                " -> PROGRAM " + programArg +
                ", BANK_MSB " + bankMsbArg +
                ", BANK_LSB " + bankLsbArg +
                " (subBank " + subBank + ", pc " + pc + ")");

        setMPC(mpcArgs, trackIndex);
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
        final String drumFile = Paths.get(drumPresetsPath, presetName + ".bwpreset").toString();
        host.println("LDR request: " + drumFile + " (track " + trackIndex + ")");

        checkPreset(drumFile);
        if (!Files.exists(Paths.get(drumFile))) {
            host.println("Error: Preset file does not exist: " + drumFile);
            return;
        }

        // IMPORTANT: schedule on Bitwig's thread/context
        host.scheduleTask(() -> doLdrReplaceNow(drumFile, trackIndex), 0);
    }


    private void doLdrReplaceNow(String drumFile, int trackIndex) {
        DeviceBank drumBank = drumRackDeviceBanks[trackIndex];
        if (drumBank == null) {
            host.println("LDR: drumRackDeviceBanks[" + trackIndex + "] is null");
            return;
        }

        Device drumRack = drumBank.getDevice(0);
        if (drumRack == null) {
            host.println("LDR: matched drumRack device is null");
            return;
        }

        // If you added exists().markInterested() during init, this becomes meaningful
        if (!drumRack.exists().get()) {
            host.println("LDR: No DrumRack matched on track " + trackIndex + " (exists=false)");
            return;
        }

        // Focus it; helps reliability
        drumRack.selectInEditor();

        // Tiny delay often helps Bitwig actually perform the replacement
        host.scheduleTask(() -> {
            InsertionPoint ip = drumRack.replaceDeviceInsertionPoint();
            if (ip == null) {
                host.println("LDR: insertionPoint is null");
                return;
            }

            host.println("LDR inserting preset into matched DrumRack: " + drumFile);
            ip.insertFile(drumFile);
            host.println("LDR insertFile() called");
        }, 50);
    }



    public void checkPreset(String name) {
        try {
            Path presetPath = Paths.get(name);
            if (Files.exists(presetPath)) host.println("Preset FOUND: " + presetPath);
            else host.println("Preset NOT found: " + presetPath);
        } catch (Exception e) {
            host.println("Preset check FAILED:");
            host.println(e.getMessage());
        }
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

    // LFR: same behaviour as LIR but for FX Selector
    public void selectFxInLayer(String commandArgs, int trackIndex) {
        String[] parts = commandArgs.split(":");
        String fxName = parts[0].trim();
        int remotePageIndex = (parts.length > 1) ? validatePageNumber(parts[1].trim()) - 1 : -1;

        ChainSelector selector = fxChainSelectors[trackIndex];
        DeviceLayerBank layerBank = fxLayerBanks[trackIndex];

        if (selector == null || layerBank == null) {
            host.println("Error: FX Selector or FX layer bank not found for track " + trackIndex);
            return;
        }

        Integer layerIndex = fxTrackLayerNames.get(trackIndex).get(fxName);
        if (layerIndex != null) {
            selector.activeChainIndex().set(layerIndex);
        } else {
            host.println("Error: FX layer not found: " + fxName);
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

        // AFTER sceneBank.scrollIntoView(foundScene);

        sendOscMessageToOutput(defaultOscOutput, "/bitx/jumpScene", new Object[]{ trackIndex, foundScene });


        host.showPopupNotification("Jumped to \"" + targetTrackName + "\" / \"" + targetClipName + "\"");
    }

    public void resetAllSendsOnAllTracks() {
        host.println("Resetting all sends on all tracks...");

        int numTracks = trackBank.getSizeOfBank();

        for (int t = 0; t < numTracks; t++) {
            Track track = trackBank.getItemAt(t);
            if (track == null) continue;

            SendBank sendBank = track.sendBank();
            int numSends = sendBank.getSizeOfBank();
            host.println("Track " + t + " has " + numSends + " sends in bank.");

            for (int s = 0; s < numSends; s++) {
                Send send = sendBank.getItemAt(s);
                if (send == null) continue;

                send.setImmediately(0.0);
            }
        }

        host.println("✅ All sends reset to 0.0");
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
