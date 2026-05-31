package com.wordbook.service;

import com.wordbook.service.tts.OnlineDictTts;
import com.wordbook.service.tts.TtsEngine;

import java.util.List;

/**
 * App-wide text-to-speech façade. Delegates to a platform {@link TtsEngine}
 * (currently {@link OnlineDictTts} — natural online dictionary audio with a
 * fully-offline macOS `say` fallback). All callers use {@link #speak(String)};
 * voice selection lives in Settings.
 */
public final class TtsService {
    private TtsService() {}

    private static final TtsEngine ENGINE = new OnlineDictTts();

    /** Speak text asynchronously in the user's chosen voice. */
    public static void speak(String text) { ENGINE.speak(text); }

    /** Voices the user can pick (recommended first). */
    public static List<String> availableVoices() { return ENGINE.availableVoices(); }

    public static String currentVoice() { return ENGINE.currentVoice(); }

    /** Switch the active voice and speak a short sample so the user can hear it. */
    public static void setVoice(String voice) {
        ENGINE.setVoice(voice);
        speak("ephemeral");
    }

    public static boolean isAvailable() { return ENGINE.isAvailable(); }
}
