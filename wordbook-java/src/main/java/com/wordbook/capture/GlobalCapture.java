package com.wordbook.capture;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System-wide capture (port of hotkey.py). Listens for:
 *   • mouse double-click or drag-select of up to 4 words, or
 *   • the global hotkey ⌘⇧D
 * then copies the selection (simulated ⌘C) and routes the text to a callback.
 *
 * Uses JNativeHook for global listening and macOS CLI tools (pbpaste / pbcopy /
 * osascript) for clipboard access and keystroke simulation — the same approach
 * pyperclip + Quartz provided in the original. Keystroke simulation requires
 * the app to be granted Accessibility permission in System Settings.
 */
public final class GlobalCapture {

    /** Receives a captured phrase plus the screen point to anchor the popup
     *  (x and y are {@link Double#NaN} for the hotkey path → centre on screen). */
    public interface Callback { void onCapture(String text, double x, double y); }

    private static final double DOUBLE_CLICK_TIME = 0.45;  // seconds
    private static final double DOUBLE_CLICK_DIST = 5;     // px
    private static final double DRAG_DIST = 8;             // px
    private static final double COOLDOWN = 1.0;            // seconds

    private static Callback callback;
    private static boolean started = false;

    // gesture state
    private static double pressX, pressY;
    private static boolean pressed = false;
    private static double lastReleaseTime = -1, lastReleaseX, lastReleaseY;
    private static double cooldownUntil = 0;

    private GlobalCapture() {}

    public static void setCallback(Callback c) { callback = c; }

    public static synchronized void start() {
        if (started) return;

        // Silence JNativeHook's verbose native logging.
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            System.err.println("[capture] Could not start global listener: " + e.getMessage());
            System.err.println("[capture] Grant Accessibility permission in "
                    + "System Settings → Privacy & Security → Accessibility, then relaunch.");
            return;
        }

        GlobalScreen.addNativeMouseListener(new MouseHandler());
        GlobalScreen.addNativeKeyListener(new KeyHandler());
        started = true;
        System.out.println("[capture] global listener started — double-click / drag-select "
                + "a word, or press ⌘⇧D");
    }

    public static synchronized void stop() {
        if (!started) return;
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            System.err.println("[capture] stop failed: " + e.getMessage());
        }
        started = false;
    }

    // ── Listeners ────────────────────────────────────────────────────
    private static final class MouseHandler implements NativeMouseListener {
        @Override public void nativeMousePressed(NativeMouseEvent e) {
            if (e.getButton() != NativeMouseEvent.BUTTON1) return;
            pressX = e.getX();
            pressY = e.getY();
            pressed = true;
        }

        @Override public void nativeMouseReleased(NativeMouseEvent e) {
            if (e.getButton() != NativeMouseEvent.BUTTON1 || !pressed) return;
            pressed = false;
            double x = e.getX(), y = e.getY();
            double now = System.currentTimeMillis() / 1000.0;

            double dragDist = Math.hypot(x - pressX, y - pressY);
            boolean isDrag = dragDist > DRAG_DIST;

            boolean isDouble = false;
            if (lastReleaseTime >= 0) {
                double dt = now - lastReleaseTime;
                double dd = Math.hypot(x - lastReleaseX, y - lastReleaseY);
                isDouble = dt < DOUBLE_CLICK_TIME && dd < DOUBLE_CLICK_DIST;
            }
            lastReleaseTime = now;
            lastReleaseX = x;
            lastReleaseY = y;

            if ((isDrag || isDouble) && now > cooldownUntil) {
                cooldownUntil = now + COOLDOWN;
                final double fx = x, fy = y;
                Thread t = new Thread(() -> handleMouseSelection(fx, fy), "capture-mouse");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private static final class KeyHandler implements NativeKeyListener {
        @Override public void nativeKeyPressed(NativeKeyEvent e) {
            boolean cmd = (e.getModifiers() & NativeKeyEvent.META_MASK) != 0;
            boolean shift = (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0;
            if (e.getKeyCode() == NativeKeyEvent.VC_D && cmd && shift) {
                Thread t = new Thread(GlobalCapture::handleHotkey, "capture-hotkey");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // ── Capture flows ────────────────────────────────────────────────
    private static void handleMouseSelection(double x, double y) {
        try {
            sleep(120);
            String oldClip = pbpaste();
            pressCmdC();
            sleep(100);
            String text = pbpaste().strip();
            if (text.equals(oldClip) || text.isEmpty()) return;
            if (wordCount(text) > 4) { pbcopy(oldClip); return; }
            pbcopy(oldClip);                       // restore the user's clipboard
            if (callback != null) callback.onCapture(text, x, y);
        } catch (Exception ex) {
            System.err.println("[capture] mouse capture error: " + ex.getMessage());
        }
    }

    private static void handleHotkey() {
        try {
            String word = pbpaste().strip();
            if (word.isEmpty() || wordCount(word) > 4) return;
            if (callback != null) callback.onCapture(word, Double.NaN, Double.NaN);
        } catch (Exception ex) {
            System.err.println("[capture] hotkey capture error: " + ex.getMessage());
        }
    }

    private static int wordCount(String s) {
        String t = s.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    // ── macOS clipboard / keystroke helpers ──────────────────────────
    private static String pbpaste() {
        try {
            Process p = new ProcessBuilder("/usr/bin/pbpaste").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static void pbcopy(String text) {
        try {
            Process p = new ProcessBuilder("/usr/bin/pbcopy").start();
            try (OutputStream os = p.getOutputStream()) {
                os.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            }
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void pressCmdC() {
        try {
            new ProcessBuilder("/usr/bin/osascript", "-e",
                    "tell application \"System Events\" to keystroke \"c\" using command down")
                    .start().waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
