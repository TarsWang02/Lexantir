package com.wordbook.service.tts;

import java.util.List;

/**
 * Platform-agnostic text-to-speech. Each OS plugs in its own implementation
 * (macOS: {@link MacSayTts} via the built-in `say` engine; Windows SAPI/WinRT,
 * iOS/Android native, etc. can follow). The rest of the app speaks through
 * {@link com.wordbook.service.TtsService}, which delegates to one of these.
 */
public interface TtsEngine {

    /** Speak {@code text} asynchronously (non-blocking, offline where possible). */
    void speak(String text);

    /** Display names of the voices a user may pick from, recommended first. */
    List<String> availableVoices();

    /** The voice currently in use. */
    String currentVoice();

    /** Persist and switch the active voice. */
    void setVoice(String voice);

    /** Whether this engine can actually produce audio on this machine. */
    default boolean isAvailable() { return true; }
}
