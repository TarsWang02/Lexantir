package com.wordbook.service.tts;

import com.wordbook.db.Database;

import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS text-to-speech via the built-in, fully offline {@code say} engine.
 *
 * <p>Each utterance is rendered to an {@code .aiff} with {@code say -v <voice> -o},
 * then played with {@code afplay} — the same render-to-file + afplay path that
 * reliably produced sound for the old edge-tts mp3s (calling {@code say} for live
 * playback was unreliable inside the signed .app bundle). Files are cached per
 * (voice, word), so repeats are instant and act as a prefetch cache.
 *
 * <p>Quality scales with the installed voice: stock voices (Samantha…) are robotic;
 * <b>Enhanced/Premium</b> voices (System Settings → Accessibility → Spoken Content →
 * System Voice → Manage Voices…) sound far more natural and are preferred by default.
 */
public final class MacSayTts implements TtsEngine {

    private static final String META_KEY = "tts_voice";
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Path CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "wordbook_tts");

    /** Known natural (non-novelty) base voice names worth offering in the picker. */
    private static final Set<String> NICE = Set.of(
            "Samantha", "Alex", "Ava", "Allison", "Susan", "Tom", "Nicky", "Aaron",
            "Daniel", "Kate", "Oliver", "Serena", "Stephanie", "Karen", "Lee",
            "Matilda", "Moira", "Tessa", "Rishi", "Veena", "Fiona");

    private record Voice(String name, String locale) {}

    private static List<Voice> VOICES;          // installed English voices (parsed once)

    public MacSayTts() {
        try { Files.createDirectories(CACHE_DIR); } catch (Exception ignored) {}
    }

    // ── TtsEngine ───────────────────────────────────────────────────
    @Override public void speak(String text) {
        if (text == null || text.isBlank()) return;
        final String voice = currentVoice();
        Thread t = new Thread(() -> {
            LOCK.lock();
            try {
                File aiff = render(text, voice);
                if (aiff != null) run("/usr/bin/afplay", aiff.getAbsolutePath());
            } finally {
                LOCK.unlock();
            }
        }, "tts");
        t.setDaemon(true);
        t.start();
    }

    @Override public List<String> availableVoices() {
        List<String> out = new ArrayList<>();
        // Enhanced / Premium first (best quality), then the curated stock voices.
        for (Voice v : voices()) if (isHiFi(v.name())) out.add(v.name());
        for (Voice v : voices()) if (!isHiFi(v.name()) && NICE.contains(baseName(v.name()))) out.add(v.name());
        if (out.isEmpty()) out.add("Samantha");
        return out;
    }

    @Override public String currentVoice() {
        String saved = Database.getMeta(META_KEY, "");
        if (saved != null && !saved.isBlank() && isInstalled(saved)) return saved;
        return defaultVoice();
    }

    @Override public void setVoice(String voice) {
        if (voice != null && !voice.isBlank()) Database.setMeta(META_KEY, voice);
    }

    @Override public boolean isAvailable() {
        return Files.isExecutable(Paths.get("/usr/bin/say"));
    }

    // ── internals ───────────────────────────────────────────────────
    private File render(String text, String voice) {
        Path path = CACHE_DIR.resolve(sanitize(voice) + "__" + sanitize(text) + ".aiff");
        File f = path.toFile();
        if (f.exists() && f.length() > 0) return f;
        try {
            int code = run("/usr/bin/say", "-v", voice, "-o", path.toString(), text);
            if (code == 0 && f.exists() && f.length() > 0) return f;
        } catch (Exception ignored) {}
        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
        return null;
    }

    /** Best default: a Premium then Enhanced en_US voice, else Samantha, else any nice voice. */
    private String defaultVoice() {
        for (Voice v : voices()) if (v.name().contains("(Premium)") && v.locale().startsWith("en_US")) return v.name();
        for (Voice v : voices()) if (v.name().contains("(Enhanced)") && v.locale().startsWith("en_US")) return v.name();
        for (Voice v : voices()) if (v.name().contains("(Premium)") || v.name().contains("(Enhanced)")) return v.name();
        if (isInstalled("Samantha")) return "Samantha";
        for (Voice v : voices()) if (NICE.contains(baseName(v.name()))) return v.name();
        return "Samantha";
    }

    private static boolean isHiFi(String name) {
        return name.contains("(Premium)") || name.contains("(Enhanced)");
    }

    /** "Ava (Premium)" → "Ava"; "Eddy (English (US))" → "Eddy". */
    private static String baseName(String name) {
        int p = name.indexOf('(');
        return (p > 0 ? name.substring(0, p) : name).trim();
    }

    private boolean isInstalled(String voice) {
        for (Voice v : voices()) if (v.name().equalsIgnoreCase(voice)) return true;
        return false;
    }

    /** Parse `say -v ?` once → installed English voices, in listed order. */
    private static synchronized List<Voice> voices() {
        if (VOICES != null) return VOICES;
        List<Voice> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            Process p = new ProcessBuilder("/usr/bin/say", "-v", "?").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            // e.g.  "Ava (Premium)        en_US    # Hello..."  (name may contain spaces/parens)
            Pattern re = Pattern.compile("^(.+?)\\s{2,}([a-z]{2}_[A-Z]{2})\\b");
            for (String line : out.split("\\R")) {
                Matcher m = re.matcher(line);
                if (m.find()) {
                    String name = m.group(1).trim();
                    String locale = m.group(2);
                    if (locale.startsWith("en") && seen.add(name)) list.add(new Voice(name, locale));
                }
            }
        } catch (Exception ignored) {}
        VOICES = list;
        return VOICES;
    }

    private static String sanitize(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            b.append(Character.isLetterOrDigit(c) ? c : '_');
            if (b.length() >= 64) break;
        }
        return b.toString();
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
