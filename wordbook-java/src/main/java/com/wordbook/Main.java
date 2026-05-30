package com.wordbook;

/**
 * Plain launcher (does NOT extend Application) so the app starts cleanly
 * from a shaded jar / jpackage bundle without the "JavaFX runtime
 * components are missing" error.
 */
public final class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}
