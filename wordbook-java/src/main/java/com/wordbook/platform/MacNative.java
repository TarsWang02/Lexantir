package com.wordbook.platform;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Minimal Cocoa bridge (via JNA + the Objective-C runtime) used to keep the
 * floating capture popup from dragging WordBook's main window to the front.
 *
 * JavaFX has no concept of a non-activating panel: showing any Stage activates
 * the whole application, and macOS then surfaces all of the app's windows. To
 * avoid that we remember which application was frontmost just before we show the
 * popup and immediately hand the foreground back to it. The popup itself stays
 * visible because it is always-on-top (floating window level).
 *
 * Everything is guarded — on non-macOS, or if the runtime can't be reached,
 * the methods degrade to no-ops so the app keeps working.
 */
public final class MacNative {
    private MacNative() {}

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private static Function msgSend, getClass, selReg;

    static {
        if (MAC) {
            try {
                NativeLibrary objc = NativeLibrary.getInstance("objc");
                msgSend  = objc.getFunction("objc_msgSend");
                getClass = objc.getFunction("objc_getClass");
                selReg   = objc.getFunction("sel_registerName");
            } catch (Throwable t) {
                msgSend = getClass = selReg = null;
                System.err.println("[macnative] Objective-C runtime unavailable: " + t.getMessage());
            }
        }
    }

    private static boolean ready() { return msgSend != null; }

    private static Pointer cls(String name) { return getClass.invokePointer(new Object[]{name}); }
    private static Pointer sel(String name) { return selReg.invokePointer(new Object[]{name}); }
    private static Pointer msg(Pointer receiver, String selector) {
        return msgSend.invokePointer(new Object[]{receiver, sel(selector)});
    }

    /** @return pid of the frontmost application, or -1 if unknown. */
    public static long frontmostPid() {
        if (!ready()) return -1;
        try {
            Pointer ws  = msg(cls("NSWorkspace"), "sharedWorkspace");
            Pointer app = msg(ws, "frontmostApplication");
            if (app == null) return -1;
            return msgSend.invokeInt(new Object[]{app, sel("processIdentifier")});
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Bring our own app to the foreground (used when summoning from the menu bar). */
    public static void activateSelf() {
        activatePid(ProcessHandle.current().pid());
    }

    /** Bring the application owning {@code pid} back to the foreground. */
    public static void activatePid(long pid) {
        if (!ready() || pid <= 0) return;
        try {
            Pointer app = msgSend.invokePointer(new Object[]{
                    cls("NSRunningApplication"),
                    sel("runningApplicationWithProcessIdentifier:"),
                    (int) pid});
            if (app == null) return;
            // NSApplicationActivateIgnoringOtherApps = 1 << 1
            msgSend.invokeInt(new Object[]{app, sel("activateWithOptions:"), 2});
        } catch (Throwable ignored) {
        }
    }
}
