package com.wordbook;

import com.wordbook.db.Database;
import com.wordbook.ui.I18n;
import com.wordbook.ui.MainWindow;
import com.wordbook.ui.Theme;
import com.wordbook.ui.TrayService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        // Keep the JVM (and global word-capture) alive after the window is closed —
        // the app lives in the menu bar and only shows the window when summoned.
        Platform.setImplicitExit(false);

        Database.initDb();

        // Load the saved appearance (light / dark / follow-system) before building UI.
        try { Theme.apply(Theme.Mode.valueOf(Database.getMeta("theme", "SYSTEM"))); }
        catch (Exception e) { Theme.apply(Theme.Mode.SYSTEM); }

        // Load the saved interface language (empty → first run → ask after launch).
        String savedLang = Database.getMeta("lang", "");
        if (savedLang != null && !savedLang.isEmpty()) {
            try { I18n.set(I18n.Lang.valueOf(savedLang)); } catch (Exception ignored) {}
        }

        MainWindow root = new MainWindow(stage);
        Scene scene = new Scene(root, 1180, 740);
        var css = App.class.getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.setFill(Theme.color(Theme.BG));

        stage.setTitle("WordBook");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);

        // Closing the window hides it (app keeps running in the menu bar) instead of quitting.
        stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); });

        stage.show();

        root.startHotkey();
        TrayService.install(stage);   // menu-bar icon with Open / Quit
        // As a menu-bar agent the window can open behind other apps — pull it forward.
        Platform.runLater(com.wordbook.platform.MacNative::activateSelf);

        // First launch (no saved language): ask the user to pick one.
        if (savedLang == null || savedLang.isEmpty()) {
            Platform.runLater(root::promptLanguage);
        }
    }

    @Override
    public void stop() {
        com.wordbook.capture.GlobalCapture.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
