package com.personal;

import com.bitwig.extension.controller.api.*;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class BitXGraphics {
    private final ControllerHost host;

    public BitXGraphics(ControllerHost host) {
        this.host = host;
    }

    public void sendTrackColorData(int trackIndex, double r, double g, double b) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {

            // Normalize 0-1 to 0-255 for JavaFX
            int red = (int) (r * 255);
            int green = (int) (g * 255);
            int blue = (int) (b * 255);

            writer.println("COLOR:" + trackIndex + ":" + red + ":" + green + ":" + blue);
            host.println("✅ Sent Track Color to JavaFX: Track " + trackIndex + ", RGB(" + red + "," + green + "," + blue + ")");

        } catch (Exception e) {
            host.println("❌ Error sending Track Color: " + e.getMessage());
        }
    }

    public void sendVuMeterData(int trackIndex, int vuValue) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println("VU:" + trackIndex + ":" + vuValue);

        } catch (Exception e) {
            host.println("❌ Error sending VU data: " + e.getMessage());
        }
    }

    public void sendDataToJavaFX(String message) {
        try (Socket socket = new Socket("127.0.0.1", 9876);
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(outputStream, true)) {
            writer.println(message);

        } catch (Exception e) {
            host.println("❌ Error sending data to JavaFX: " + e.getMessage());
        }
    }

    public Process startDisplayProcess() {
        Process displayProcess = null;
        try {
            displayProcess = new ProcessBuilder(
                    "java",
                    "--module-path", "/opt/javafx/lib",
                    "--add-modules", "javafx.controls,javafx.graphics",
                    "-jar",
                    "/Users/borrie/Documents/Programming/BitXDisplayApp/target/BitXDisplayApp-1.0-SNAPSHOT.jar"
            ).start();
        } catch (Exception e) {
            host.println("Error starting JavaFX app: " + e.getMessage());
        }
        return displayProcess;
    }
}
