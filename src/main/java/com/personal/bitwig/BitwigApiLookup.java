package com.personal.bitwig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BitwigApiLookup {
    private static final String RESOURCE = "/bitwigapi/BitwigAPI25.txt";

    public static List<String> search(String query) {
        List<String> results = new ArrayList<>();
        try (InputStream in = BitwigApiLookup.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return results;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = r.readLine()) != null) {
                    lineNo++;
                    if (line.contains(query)) {
                        results.add("L" + lineNo + ": " + line.trim());
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return results;
    }
}
