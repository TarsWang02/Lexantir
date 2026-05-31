package com.wordbook.service.tts;

import com.wordbook.db.Database;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Natural word pronunciation via an online dictionary audio endpoint
 * (Youdao {@code dictvoice} — keyless, US/UK accents, dictionary-grade audio:
 * the same approach most Chinese vocabulary tools use, which is why it sounds
 * "normal" rather than synthetic).
 *
 * <p>Each utterance is fetched as mp3, cached per (accent, word), and played
 * with {@code afplay}. If the network is unavailable or the request fails, it
 * falls back to the fully-offline macOS {@code say} engine so there is always
 * sound.
 *
 * <p>The active "voice" is a mode id stored in {@code meta.tts_voice}:
 * {@code youdao-us} (default) · {@code youdao-uk} · {@code system} (offline say).
 */
public final class OnlineDictTts implements TtsEngine {

    public static final String US = "youdao-us";
    public static final String UK = "youdao-uk";
    public static final String SYSTEM = "system";
    private static final String META_KEY = "tts_voice";

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Path CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "wordbook_tts");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final MacSayTts offline = new MacSayTts();   // offline fallback

    public OnlineDictTts() {
        try { Files.createDirectories(CACHE_DIR); } catch (Exception ignored) {}
    }

    // ── TtsEngine ───────────────────────────────────────────────────
    @Override public void speak(String text) {
        if (text == null || text.isBlank()) return;
        final String mode = currentVoice();
        if (SYSTEM.equals(mode)) { offline.speak(text); return; }

        final int type = UK.equals(mode) ? 1 : 2;   // youdao: 1=UK, 2=US
        Thread t = new Thread(() -> {
            LOCK.lock();
            try {
                File mp3 = fetch(text, type);
                if (mp3 != null) run("/usr/bin/afplay", mp3.getAbsolutePath());
                else offline.speak(text);            // network failed → offline voice
            } finally {
                LOCK.unlock();
            }
        }, "tts-online");
        t.setDaemon(true);
        t.start();
    }

    @Override public List<String> availableVoices() { return List.of(US, UK, SYSTEM); }

    @Override public String currentVoice() {
        String saved = Database.getMeta(META_KEY, "");
        return (US.equals(saved) || UK.equals(saved) || SYSTEM.equals(saved)) ? saved : US;
    }

    @Override public void setVoice(String voice) {
        if (US.equals(voice) || UK.equals(voice) || SYSTEM.equals(voice)) Database.setMeta(META_KEY, voice);
    }

    // ── Youdao fetch + cache ────────────────────────────────────────
    private File fetch(String text, int type) {
        Path path = CACHE_DIR.resolve("youdao" + type + "__" + sanitize(text) + ".mp3");
        File f = path.toFile();
        if (f.exists() && f.length() > 0) return f;
        try {
            String url = "https://dict.youdao.com/dictvoice?type=" + type
                    + "&audio=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(6))
                    .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            // Guard: real audio, not an HTML error page.
            if (resp.statusCode() == 200 && body != null && body.length > 200 && body[0] != '<') {
                Files.write(path, body);
                return f;
            }
        } catch (Exception ignored) {}
        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
        return null;
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
            p.getInputStream().readAllBytes();
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }
}
