package com.wordbook.ui.components;

import com.wordbook.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/** Sand-orange 3D CTA with an integrated cream count badge (port of SpellCheckButton). */
public class SpellCheckButton extends StackPane {

    private final Label text = new Label("✦   " + com.wordbook.ui.I18n.t("spell.button"));
    private final Label badge = new Label();
    private final HBox row = new HBox(8);
    private final DropShadow pillar = new DropShadow();
    private final Runnable command;

    private int count = 0;
    private boolean hovered = false, pressed = false;
    private final int radius = 12, depth = 4;

    public SpellCheckButton(Runnable command) {
        this.command = command;

        text.setFont(Theme.BTN);
        text.setTextFill(Color.web(Theme.CARD));

        badge.setFont(Theme.MONO_LABEL);
        badge.setTextFill(Color.web(Theme.ACCENT_DARK));
        badge.setAlignment(Pos.CENTER);
        badge.setMinWidth(20);
        badge.setPadding(new Insets(1, 7, 1, 7));
        badge.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-background-radius: 9;");
        badge.setVisible(false);
        badge.setManaged(false);

        row.setAlignment(Pos.CENTER);
        row.getChildren().addAll(text, badge);
        getChildren().add(row);
        setAlignment(Pos.CENTER);
        setPrefHeight(44);
        setMinHeight(44);
        setPadding(new Insets(0, 16, 0, 16));
        setCursor(Cursor.HAND);

        pillar.setBlurType(BlurType.ONE_PASS_BOX);
        pillar.setRadius(0);
        pillar.setSpread(1.0);
        pillar.setOffsetX(0);
        pillar.setColor(Color.web(Theme.ACCENT_DARK));
        setEffect(pillar);

        setOnMouseEntered(e -> { hovered = true; redraw(); });
        setOnMouseExited(e -> { hovered = false; pressed = false; redraw(); });
        setOnMousePressed(e -> { pressed = true; redraw(); });
        setOnMouseReleased(e -> {
            boolean was = pressed; pressed = false; redraw();
            if (was && command != null) command.run();
        });
        redraw();
    }

    public void setCount(int c) {
        this.count = Math.max(0, c);
        boolean show = count > 0;
        badge.setText(String.valueOf(count));
        badge.setVisible(show);
        badge.setManaged(show);
    }

    private void redraw() {
        String face = pressed ? Theme.ACCENT_PRESS : hovered ? Theme.ACCENT_HOVER : Theme.ACCENT;
        setStyle("-fx-background-color: " + face + "; -fx-background-radius: " + radius + ";");
        if (pressed) { pillar.setOffsetY(1); setTranslateY(depth - 1); }
        else { pillar.setOffsetY(depth); setTranslateY(0); }
    }
}
