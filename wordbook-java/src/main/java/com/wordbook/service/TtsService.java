package com.wordbook.service;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Text-to-speech — port of the speak() logic in main.py.
 * Prefers the high-quality edge-tts voice (cached .mp3 played via afplay),
 * falling back to the macOS `say` command.
 */
public final class TtsService {
    private TtsService() {}

    private static final String VOICE = "en-US-AriaNeural";
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Path CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "wordbook_tts");

    // Candidate edge-tts CLI locations (resolved once).
    private static final String EDGE_TTS = findEdgeTts();

    static {
        try { Files.createDirectories(CACHE_DIR); } catch (Exception ignored) {}
    }

    private static String findEdgeTts() {
        String[] candidates = {
                "edge-tts",
                System.getProperty("user.home") + "/wordbook/venv/bin/edge-tts",
                "/opt/homebrew/bin/edge-tts",
                "/usr/local/bin/edge-tts",
        };
        for (String c : candidates) {
            try {
                if (c.contains("/")) {
                    if (Files.isExecutable(Paths.get(c))) return c;
                } else {
                    Process p = new ProcessBuilder("/usr/bin/which", c)
                            .redirectErrorStream(true).start();
                    if (p.waitFor() == 0) return c;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Speak asynchronously (non-blocking). */
    public static void speak(String text) {
        if (text == null || text.isBlank()) return;
        Thread t = new Thread(() -> {
            LOCK.lock();
            try {
                if (EDGE_TTS != null) {
                    File mp3 = edgeTtsFile(text);
                    if (mp3 != null) {
                        run("/usr/bin/afplay", mp3.getAbsolutePath());
                        return;
                    }
                }
                run("/usr/bin/say", text);
            } finally {
                LOCK.unlock();
            }
        }, "tts");
        t.setDaemon(true);
        t.start();
    }

    private static File edgeTtsFile(String text) {
        StringBuilder safe = new StringBuilder();
        for (char ch : text.toLowerCase().toCharArray()) {
            safe.append(Character.isLetterOrDigit(ch) ? ch : '_');
            if (safe.length() >= 64) break;
        }
        Path path = CACHE_DIR.resolve(VOICE + "__" + safe + ".mp3");
        File f = path.toFile();
        if (f.exists() && f.length() > 0) return f;
        try {
            int code = run(EDGE_TTS, "--voice", VOICE, "--text", text,
                    "--write-media", path.toString());
            if (code == 0 && f.exists() && f.length() > 0) return f;
        } catch (Exception e) {
            System.err.println("[tts] edge-tts failed: " + e.getMessage());
        }
        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
        return null;
    }

    private static int run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes(); // drain
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }
}
