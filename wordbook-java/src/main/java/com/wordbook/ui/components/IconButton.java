package com.wordbook.ui.components;

import com.wordbook.ui.Icons;
import com.wordbook.ui.Theme;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Small rounded icon-only button for in-card actions (port of IconButton). */
public class IconButton extends StackPane {

    private final Label icon = new Label();
    private final Runnable command;

    private final String fg, defaultBg, defaultBorder, hoverBg, hoverFg;
    private final String activeBg, activeFg;
    private final boolean danger;
    private final String dangerBg = Theme.TIER3_BG, dangerFg = Theme.TIER3;
    private final int radius;

    private boolean active = false, hovered = false;

    // optional image icon (recoloured per state); replaces the text glyph when set
    private ImageView iconImg;
    private String imgNormal, imgActive;
    private double imgSize;

    /** Use tinted PNG icons instead of a text glyph. {@code activeName} may be null. */
    public IconButton icon(String normalName, String activeName, double size) {
        this.imgNormal = normalName;
        this.imgActive = activeName;
        this.imgSize = size;
        icon.setText("");
        if (iconImg == null) {
            iconImg = new ImageView();
            iconImg.setMouseTransparent(true);
            iconImg.setPreserveRatio(true);
            getChildren().add(iconImg);
        }
        redraw();
        return this;
    }

    public IconButton(String iconText, Runnable command) {
        this(iconText, command, null, null, null, null, null, false, null, null, 30, 8,
                Font.font(Theme.SERIF, 13));
    }

    public IconButton(String iconText, Runnable command,
                      String fg, String defaultBg, String defaultBorder,
                      String hoverBg, String hoverFg,
                      boolean active, String activeBg, String activeFg,
                      int size, int radius, Font font) {
        this.command = command;
        this.fg = fg != null ? fg : Theme.TEXT_SUB;
        this.defaultBg = defaultBg != null ? defaultBg : Theme.CARD_ALT;
        this.defaultBorder = defaultBorder != null ? defaultBorder : Theme.BORDER;
        this.hoverBg = hoverBg != null ? hoverBg : Theme.ACCENT_LIGHT;
        this.hoverFg = hoverFg != null ? hoverFg : Theme.ACCENT_TEXT;
        this.active = active;
        this.activeBg = activeBg != null ? activeBg : Theme.ACCENT;
        this.activeFg = activeFg != null ? activeFg : Theme.CARD;
        this.danger = false;
        this.radius = radius;

        icon.setText(iconText);
        icon.setFont(font);
        icon.setMouseTransparent(true);
        getChildren().add(icon);
        setAlignment(Pos.CENTER);
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);
        setCursor(Cursor.HAND);

        setOnMouseEntered(e -> { hovered = true; redraw(); });
        setOnMouseExited(e -> { hovered = false; redraw(); });
        setOnMouseClicked(e -> { if (command != null) command.run(); });
        redraw();
    }

    /** Convenience for a danger (delete) button. */
    public static IconButton danger(String iconText, Runnable command) {
        IconButton b = new IconButton(iconText, command);
        b.makeDanger();
        return b;
    }

    private boolean dangerFlag = false;
    private void makeDanger() { dangerFlag = true; redraw(); }

    public void setActive(boolean a) { this.active = a; redraw(); }

    private void redraw() {
        String bg, color, border = "";
        if (active) { bg = activeBg; color = activeFg; }
        else if (hovered && dangerFlag) { bg = dangerBg; color = dangerFg; }
        else if (hovered) { bg = hoverBg; color = hoverFg; }
        else { bg = defaultBg; color = fg; border = "-fx-border-color: " + defaultBorder
                + "; -fx-border-radius: " + radius + "; -fx-border-width: 1;"; }
        setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + radius + ";" + border);
        icon.setTextFill(Color.web(color));
        if (iconImg != null) {
            String nm = (active && imgActive != null) ? imgActive : imgNormal;
            iconImg.setImage(Icons.img(nm));
            iconImg.setFitWidth(imgSize);
            iconImg.setFitHeight(imgSize);
            Icons.tint(iconImg, imgSize, color);
        }
    }
}
