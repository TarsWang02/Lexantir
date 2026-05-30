package com.wordbook.ui.components;

import com.wordbook.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Tiny rounded label badge (POS, counters, etc.). */
public class Pill extends Label {
    public Pill(String text, String bg, String fg) {
        this(text, bg, fg, Theme.MONO_LABEL, 8, 3, 8);
    }

    public Pill(String text, String bg, String fg, Font font, int padX, int padY, int radius) {
        super(text);
        setFont(font);
        setTextFill(Color.web(fg));
        setAlignment(Pos.CENTER);
        setPadding(new Insets(padY, padX, padY, padX));
        setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + radius + ";");
    }

    public static Pill pos(String posTag) {
        String[] c = Theme.posColors(posTag);
        return new Pill(posTag == null ? "" : posTag, c[0], c[1]);
    }
}
