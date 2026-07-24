package com.jiyi.infra.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class LicenseManager {
    private static final Set<String> VALID_CODES = Set.of(
        "JIYI-A1B2-C3D4", "JIYI-E5F6-G7H8", "JIYI-I9J0-K1L2",
        "JIYI-M3N4-O5P6", "JIYI-Q7R8-S9T0", "JIYI-U1V2-W3X4",
        "JIYI-Y5Z6-A7B8", "JIYI-C9D0-E1F2", "JIYI-G3H4-I5J6",
        "JIYI-K7L8-M9N0", "JIYI-O1P2-Q3R4", "JIYI-S5T6-U7V8"
    );
    private static final Path USED_FILE = Path.of("license.dat");

    public static boolean activate(String code) {
        if (!VALID_CODES.contains(code)) return false;
        var used = loadUsed();
        if (used.contains(code)) return false;
        used.add(code);
        saveUsed(used);
        return true;
    }

    public static boolean isActivated() {
        var used = loadUsed();
        return used.stream().anyMatch(VALID_CODES::contains);
    }

    private static Set<String> loadUsed() {
        if (!Files.exists(USED_FILE)) return new HashSet<>();
        try {
            return new HashSet<>(Files.readAllLines(USED_FILE));
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    private static void saveUsed(Set<String> used) {
        try {
            Files.write(USED_FILE, used);
        } catch (IOException e) {
            // silent
        }
    }
}
