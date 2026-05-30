package com.wordbook.ui;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;

/**
 * Lexantir palette + fonts + helpers. The colour fields are mutable so the whole
 * app can switch between the warm light palette and a warm "black" dark palette;
 * call {@link #apply(Mode)} then rebuild the UI to recolour everything.
 */
public final class Theme {
    private Theme() {}

    /** User-selectable appearance. SYSTEM follows macOS dark/light. */
    public enum Mode { LIGHT, DARK, SYSTEM }

    private static Mode choice = Mode.SYSTEM;
    public static boolean dark = false;            // resolved value currently in effect
    public static Mode choice() { return choice; }

    // ── Palette (assigned by apply(); never final) ──────────────────
    public static String BG, BG_ALT, SIDEBAR, CARD, CARD_ALT,
            SHADOW, SHADOW_DEEP, BORDER, DOT,
            TEXT_MAIN, TEXT_SUB, TEXT_HINT, TEXT_MUTE,
            ACCENT, ACCENT_DARK, ACCENT_HOVER, ACCENT_PRESS, ACCENT_LIGHT, ACCENT_TEXT,
            POS_ADJ_BG, POS_ADJ_FG, POS_VERB_BG, POS_VERB_FG,
            POS_NOUN_BG, POS_NOUN_FG, POS_ADV_BG, POS_ADV_FG,
            TIER0, TIER0_BG, TIER0_FG, TIER1, TIER1_BG, TIER1_FG,
            TIER2, TIER2_BG, TIER2_FG, TIER3, TIER3_BG, TIER3_FG;
    public static String[] HEAT;

    static { apply(Mode.LIGHT); }   // sensible defaults before the real choice loads

    /** Resolve the user's choice (LIGHT/DARK/SYSTEM) and load the matching palette. */
    public static void apply(Mode mode) {
        choice = mode;
        dark = (mode == Mode.DARK) || (mode == Mode.SYSTEM && systemIsDark());
        if (dark) applyDark(); else applyLight();
    }

    private static void applyLight() {
        BG = "#EFE9DD"; BG_ALT = "#E8E1D2"; SIDEBAR = "#E8E1D2"; CARD = "#F7F2E7"; CARD_ALT = "#FAF6EE";
        SHADOW = "#D6CDBC"; SHADOW_DEEP = "#A89C84"; BORDER = "#D9D1C2"; DOT = "#D6CDBC";
        TEXT_MAIN = "#4D4233"; TEXT_SUB = "#877C6D"; TEXT_HINT = "#B0A795"; TEXT_MUTE = "#9E8F7A";
        ACCENT = "#C9A578"; ACCENT_DARK = "#A8895F"; ACCENT_HOVER = "#D5B387";
        ACCENT_PRESS = "#B9966A"; ACCENT_LIGHT = "#EDDFC4"; ACCENT_TEXT = "#A8895F";
        POS_ADJ_BG = "#EDDFC4"; POS_ADJ_FG = "#A8895F";
        POS_VERB_BG = "#D0E2EC"; POS_VERB_FG = "#557A95";
        POS_NOUN_BG = "#E3E8DF"; POS_NOUN_FG = "#72856A";
        POS_ADV_BG = "#E8E1D2"; POS_ADV_FG = "#877C6D";
        TIER0 = "#A8B89C"; TIER0_BG = "#E3E8DF"; TIER0_FG = "#72856A";
        TIER1 = "#B5A78F"; TIER1_BG = "#E8E1D2"; TIER1_FG = "#877C6D";
        TIER2 = "#C9A578"; TIER2_BG = "#EDDFC4"; TIER2_FG = "#A8895F";
        TIER3 = "#B88B7A"; TIER3_BG = "#F5E2DE"; TIER3_FG = "#B88B7A";
        HEAT = new String[]{"#E5DDCD", "#E0CFAA", "#C9A578", "#A8895F", "#80683F"};
    }

    private static void applyDark() {
        BG = "#16140F"; BG_ALT = "#1E1B15"; SIDEBAR = "#1E1B15"; CARD = "#232019"; CARD_ALT = "#2B2720";
        SHADOW = "#0C0B08"; SHADOW_DEEP = "#000000"; BORDER = "#3A352B"; DOT = "#2B2720";
        TEXT_MAIN = "#ECE3D4"; TEXT_SUB = "#AEA391"; TEXT_HINT = "#7E7563"; TEXT_MUTE = "#968976";
        ACCENT = "#C9A578"; ACCENT_DARK = "#A8895F"; ACCENT_HOVER = "#D8B98C";
        ACCENT_PRESS = "#B9966A"; ACCENT_LIGHT = "#3A3325"; ACCENT_TEXT = "#D8B98C";
        POS_ADJ_BG = "#3A3325"; POS_ADJ_FG = "#D8B98C";
        POS_VERB_BG = "#24333B"; POS_VERB_FG = "#8FB8CE";
        POS_NOUN_BG = "#283324"; POS_NOUN_FG = "#A6C295";
        POS_ADV_BG = "#2E2A22"; POS_ADV_FG = "#AEA391";
        TIER0 = "#8FA882"; TIER0_BG = "#283324"; TIER0_FG = "#A6C295";
        TIER1 = "#9C8E76"; TIER1_BG = "#2E2A22"; TIER1_FG = "#AEA391";
        TIER2 = "#C9A578"; TIER2_BG = "#3A3325"; TIER2_FG = "#D8B98C";
        TIER3 = "#C28A78"; TIER3_BG = "#3A2723"; TIER3_FG = "#E0A593";
        HEAT = new String[]{"#252119", "#4A3E2A", "#7A6238", "#A8895F", "#D8B98C"};
    }

    /** Whether macOS is currently in Dark Mode. */
    public static boolean systemIsDark() {
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return out.toLowerCase().contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    // ── POS lookup ──────────────────────────────────────────────────
    public static String[] posColors(String pos) {
        if (pos == null) pos = "";
        return switch (pos.toLowerCase()) {
            case "adj"  -> new String[]{POS_ADJ_BG, POS_ADJ_FG};
            case "verb" -> new String[]{POS_VERB_BG, POS_VERB_FG};
            case "noun" -> new String[]{POS_NOUN_BG, POS_NOUN_FG};
            case "adv"  -> new String[]{POS_ADV_BG, POS_ADV_FG};
            default     -> new String[]{POS_NOUN_BG, POS_NOUN_FG};
        };
    }

    public static String heatColor(int count) {
        if (count >= 10) return HEAT[4];
        if (count >= 6)  return HEAT[3];
        if (count >= 3)  return HEAT[2];
        if (count >= 1)  return HEAT[1];
        return HEAT[0];
    }

    /** rim color, badge text, badge bg, badge fg. */
    public record WrongTier(String rim, String badge, String badgeBg, String badgeFg) {}

    public static WrongTier wrongTier(int wc) {
        if (wc == 0) return new WrongTier(TIER0, "✓", TIER0_BG, TIER0_FG);
        if (wc <= 2) return new WrongTier(TIER1, "× " + wc, TIER1_BG, TIER1_FG);
        if (wc <= 5) return new WrongTier(TIER2, "× " + wc, TIER2_BG, TIER2_FG);
        return new WrongTier(TIER3, "× " + wc, TIER3_BG, TIER3_FG);
    }

    // ── Color math (matches _hex_lighten / _hex_darken) ─────────────
    public static String lighten(String hex, double amt) {
        int[] c = rgb(hex);
        int r = (int) Math.min(255, c[0] + (255 - c[0]) * amt);
        int g = (int) Math.min(255, c[1] + (255 - c[1]) * amt);
        int b = (int) Math.min(255, c[2] + (255 - c[2]) * amt);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public static String darken(String hex, double amt) {
        int[] c = rgb(hex);
        return String.format("#%02x%02x%02x",
                (int) (c[0] * (1 - amt)), (int) (c[1] * (1 - amt)), (int) (c[2] * (1 - amt)));
    }

    private static int[] rgb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    public static Color color(String hex) {
        return Color.web(hex);
    }

    // ── Fonts (Georgia serif throughout; Menlo for mono labels) ─────
    public static final String SERIF = "Georgia";
    public static final String MONO  = "Menlo";

    public static final Font SERIF_TITLE  = Font.font(SERIF, FontWeight.BOLD, 26);
    public static final Font SERIF_H1     = Font.font(SERIF, FontWeight.BOLD, 22);
    public static final Font SERIF_H2     = Font.font(SERIF, FontWeight.BOLD, 18);
    public static final Font SERIF_H3     = Font.font(SERIF, FontWeight.BOLD, 15);
    public static final Font SERIF_BODY   = Font.font(SERIF, 13);
    public static final Font SERIF_SMALL  = Font.font(SERIF, 11);
    public static final Font SERIF_ITALIC = Font.font(SERIF, FontPosture.ITALIC, 11);
    public static final Font SERIF_TINY   = Font.font(SERIF, 10);

    public static final Font MONO_TINY    = Font.font(MONO, 9);
    public static final Font MONO_LABEL   = Font.font(MONO, FontWeight.BOLD, 10);
    public static final Font MONO_SMALL   = Font.font(MONO, 11);

    public static final Font BTN          = Font.font(SERIF, FontWeight.BOLD, 11);
    public static final Font BTN_LG       = Font.font(SERIF, FontWeight.BOLD, 13);
}
