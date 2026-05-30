package com.wordbook.ui.components;

import com.wordbook.ui.Icons;
import com.wordbook.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Duolingo-style 3D flat button: rounded face over a hard offset shadow that
 * "depresses" on click. Mirrors Lexantir's
 *   shadow-[0_3px_0_#A8895F] ... active:translate-y-[2px].
 */
public class FlatButton extends StackPane {

    private final Label label = new Label();
    private final DropShadow pillar = new DropShadow();

    private String bg, hoverBg, pressBg, shadowBg, fg;
    private int radius = 10, depth = 3;
    private boolean outlined = false;
    private String borderColor = Theme.BORDER;
    private boolean enabled = true;
    private Runnable command;

    private boolean hovered = false, pressed = false;

    private ImageView btnIcon;
    private String iconName;
    private double iconSize;

    /** Add a tinted leading icon (recoloured to the button's foreground colour). */
    public FlatButton icon(String name, double size) {
        this.iconName = name;
        this.iconSize = size;
        if (btnIcon == null) {
            btnIcon = new ImageView();
            btnIcon.setMouseTransparent(true);
            btnIcon.setPreserveRatio(true);
            HBox box = new HBox(7, btnIcon, label);
            box.setAlignment(Pos.CENTER);
            box.setMouseTransparent(true);
            getChildren().setAll(box);
        }
        redraw();
        return this;
    }

    public FlatButton(String text) { this(text, null); }

    public FlatButton(String text, Runnable command) {
        this.command = command;
        this.bg = Theme.ACCENT;
        this.fg = "#F7F2E7";
        this.hoverBg = Theme.lighten(bg, 0.07);
        this.shadowBg = Theme.darken(bg, 0.20);
        this.pressBg = Theme.darken(bg, 0.05);

        label.setText(text);
        label.setFont(Theme.BTN);
        label.setMouseTransparent(true);
        getChildren().add(label);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 14, 8, 14));

        pillar.setBlurType(BlurType.ONE_PASS_BOX);
        pillar.setRadius(0);
        pillar.setSpread(1.0);
        pillar.setOffsetX(0);
        setEffect(pillar);

        setOnMouseEntered(e -> { hovered = true; redraw(); });
        setOnMouseExited(e -> { hovered = false; pressed = false; redraw(); });
        setOnMousePressed(e -> { pressed = true; redraw(); });
        setOnMouseReleased(e -> {
            boolean wasPressed = pressed;
            pressed = false; redraw();
            if (enabled && wasPressed && isInside(e.getX(), e.getY()) && command != null) {
                command.run();
            }
        });
        redraw();
    }

    private boolean isInside(double x, double y) {
        return x >= 0 && y >= 0 && x <= getWidth() && y <= getHeight();
    }

    // ── fluent config ───────────────────────────────────────────────
    public FlatButton colors(String bg, String fg) {
        this.bg = bg; this.fg = fg;
        this.hoverBg = Theme.lighten(bg, 0.07);
        this.shadowBg = Theme.darken(bg, 0.20);
        this.pressBg = Theme.darken(bg, 0.05);
        redraw(); return this;
    }
    public FlatButton hover(String h) { this.hoverBg = h; redraw(); return this; }
    public FlatButton shadow(String s) { this.shadowBg = s; redraw(); return this; }
    public FlatButton radius(int r) { this.radius = r; redraw(); return this; }
    public FlatButton depth(int d) { this.depth = d; redraw(); return this; }
    public FlatButton font(Font f) { label.setFont(f); return this; }
    public FlatButton outlined(boolean o, String border) {
        this.outlined = o; if (border != null) this.borderColor = border; redraw(); return this;
    }
    public FlatButton onClick(Runnable r) { this.command = r; return this; }
    public FlatButton prefW(double w) { setPrefWidth(w); return this; }
    public FlatButton pad(double v, double h) { setPadding(new Insets(v, h, v, h)); return this; }

    public void setText(String t) { label.setText(t); }
    public String getText() { return label.getText(); }

    public void setEnabledState(boolean en) {
        this.enabled = en;
        setCursor(en ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        setOpacity(en ? 1.0 : 0.55);
        redraw();
    }

    private void redraw() {
        setCursor(enabled ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        String face;
        if (!enabled) face = Theme.lighten(bg, 0.30);
        else if (pressed) face = pressBg;
        else if (hovered) face = hoverBg;
        else face = bg;

        String border = outlined ? ("-fx-border-color: " + borderColor + ";"
                + " -fx-border-radius: " + radius + "; -fx-border-width: 1;") : "";
        setStyle("-fx-background-color: " + face + ";"
                + " -fx-background-radius: " + radius + ";" + border);
        label.setTextFill(Color.web(fg));
        if (btnIcon != null) {
            btnIcon.setImage(Icons.img(iconName));
            btnIcon.setFitWidth(iconSize);
            btnIcon.setFitHeight(iconSize);
            Icons.tint(btnIcon, iconSize, fg);
        }

        // 3D pillar via hard drop shadow; sinks on press
        pillar.setColor(Color.web(shadowBg));
        if (pressed) {
            pillar.setOffsetY(1);
            setTranslateY(depth - 1);
        } else {
            pillar.setOffsetY(depth);
            setTranslateY(0);
        }
    }
}
