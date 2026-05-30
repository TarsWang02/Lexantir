package com.wordbook.ui;

import com.wordbook.capture.GlobalCapture;
import com.wordbook.platform.MacNative;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * Menu-bar (status bar) presence so the app can run in the background: the main
 * window hides on close, and a small icon in the top menu bar offers Open / Quit.
 * Uses AWT SystemTray (native on macOS) alongside JavaFX.
 */
public final class TrayService {
    private TrayService() {}

    private static TrayIcon trayIcon;

    public static void install(Stage stage) {
        if (!SystemTray.isSupported()) {
            System.err.println("[tray] SystemTray not supported on this platform");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem(I18n.t("tray.open"));
            open.addActionListener(e -> Platform.runLater(() -> showMain(stage)));
            MenuItem quit = new MenuItem(I18n.t("tray.quit"));
            quit.addActionListener(e -> quit());
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            trayIcon = new TrayIcon(loadIcon(), "WordBook", menu);
            trayIcon.setImageAutoSize(true);
            // Click the icon itself → open the window too.
            trayIcon.addActionListener(e -> Platform.runLater(() -> showMain(stage)));
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Throwable t) {
            System.err.println("[tray] install failed: " + t.getMessage());
        }
    }

    /** Pick the icon variant that reads on the current (system) menu-bar appearance. */
    private static Image loadIcon() throws Exception {
        String name = Theme.systemIsDark() ? "menubar_icon_light.png" : "menubar_icon_dark.png";
        try (var in = TrayService.class.getResourceAsStream("/icons/" + name)) {
            return ImageIO.read(in);
        }
    }

    private static void showMain(Stage stage) {
        if (stage.isIconified()) stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
        MacNative.activateSelf();   // the app is an accessory; pull it to the front
    }

    private static void quit() {
        try {
            if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception ignored) {}
        GlobalCapture.stop();
        Platform.exit();
        System.exit(0);
    }
}
