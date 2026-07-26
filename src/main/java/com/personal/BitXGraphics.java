package com.personal;

import com.bitwig.extension.controller.api.*;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BitXGraphics {
    private final ControllerHost host;
    private Process displayProcess;

    public BitXGraphics(ControllerHost host) {
        this.host = host;
    }

    public void sendTrackColorData(int trackIndex, double r, double g, double b) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {

            int red = (int) (r * 255);
            int green = (int) (g * 255);
            int blue = (int) (b * 255);

            writer.println("COLOR:" + trackIndex + ":" + red + ":" + green + ":" + blue);
        } catch (Exception e) {
            host.println("Error sending Track Color: " + e.getMessage());
        }
    }

    public void sendVuMeterData(int trackIndex, int vuValue) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println("VU:" + trackIndex + ":" + vuValue);
        } catch (Exception ignored) {
            // The display is optional and may not be running.
        }
    }

    public void sendDataToJavaFX(String message) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println(message);
        } catch (Exception ignored) {
            // The display is optional and may not be running.
        }
    }

    void startDisplayProcess() {
        if (isDisplayRunning() || (displayProcess != null && displayProcess.isAlive())) {
            return;
        }

        new Thread(() -> {
            try {
                displayProcess = createDisplayProcessBuilder().inheritIO().start();
                Thread.sleep(1000);
            } catch (Exception e) {
                host.println("Error starting JavaFX app: " + e.getMessage());
                host.showPopupNotification("BitX Display could not be started. Check the controller log.");
            }
        }, "BitX Display Launcher").start();
    }

    private ProcessBuilder createDisplayProcessBuilder() {
        if (host.platformIsWindows()) {
            Path executable = findWindowsDisplayExecutable();
            if (executable == null) {
                throw new IllegalStateException(
                        "BitXDisplayApp.exe was not found in the PerSonal extension folder"
                );
            }
            return new ProcessBuilder(executable.toString());
        }

        if (host.platformIsMac()) {
            Path application = Paths.get(
                    System.getProperty("user.home"),
                    "Documents",
                    "Bitwig Studio",
                    "Extensions",
                    "PerSonal",
                    "BitXDisplayApp.app"
            );
            if (!Files.isDirectory(application)) {
                throw new IllegalStateException("BitXDisplayApp.app was not found at " + application);
            }
            return new ProcessBuilder("open", application.toString());
        }

        throw new IllegalStateException("BitX Display is not packaged for this operating system");
    }

    private Path findWindowsDisplayExecutable() {
        List<Path> documentRoots = new ArrayList<>();
        documentRoots.add(Paths.get(System.getProperty("user.home"), "Documents"));
        addOneDriveDocuments(documentRoots, "OneDrive");
        addOneDriveDocuments(documentRoots, "OneDriveConsumer");
        addOneDriveDocuments(documentRoots, "OneDriveCommercial");

        for (Path documents : documentRoots) {
            Path executable = documents.resolve(
                    Paths.get(
                            "Bitwig Studio",
                            "Extensions",
                            "PerSonal",
                            "BitXDisplayApp",
                            "BitXDisplayApp.exe"
                    )
            );
            if (Files.isRegularFile(executable)) {
                return executable;
            }
        }
        return null;
    }

    private void addOneDriveDocuments(List<Path> documentRoots, String environmentVariable) {
        String oneDriveRoot = System.getenv(environmentVariable);
        if (oneDriveRoot != null && !oneDriveRoot.isBlank()) {
            documentRoots.add(Paths.get(oneDriveRoot, "Documents"));
        }
    }

    private boolean isDisplayRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 9876), 200);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
