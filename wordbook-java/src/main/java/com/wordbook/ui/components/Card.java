package com.wordbook.ui.components;

import com.wordbook.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Rounded panel (port of RoundedFrame). A VBox you add children to directly,
 * with rounded background, optional border, optional 3D offset shadow and
 * hover state.
 */
public class Card extends VBox {

    private String fill = Theme.CARD;
    private String border = null;
    private int borderW = 1;
    private int radius = 14;
    private String hoverFill = null;
    private String hoverBorder = null;
    private int shadowDepth = 0;
    private String shadowColor = Theme.SHADOW;

    private final DropShadow shadow = new DropShadow();
    private boolean hoverEnabled = false;

    public Card() {
        shadow.setBlurType(BlurType.ONE_PASS_BOX);
        shadow.setRadius(0);
        shadow.setSpread(1.0);
        shadow.setOffsetX(0);
        apply();
    }

    public Card fill(String c) { this.fill = c; apply(); return this; }
    public Card border(String c) { this.border = c; apply(); return this; }
    public Card border(String c, int w) { this.border = c; this.borderW = w; apply(); return this; }
    public Card radius(int r) { this.radius = r; apply(); return this; }
    public Card pad(double p) { setPadding(new Insets(p)); return this; }
    public Card pad(double v, double h) { setPadding(new Insets(v, h, v, h)); return this; }
    public Card hover(String fillH, String borderH) {
        this.hoverFill = fillH; this.hoverBorder = borderH; return this;
    }
    public Card shadow(int depth) { this.shadowDepth = depth; apply(); return this; }
    public Card shadow(int depth, String color) {
        this.shadowDepth = depth; this.shadowColor = color; apply(); return this;
    }

    public Card enableHover() {
        if (hoverEnabled) return this;
        hoverEnabled = true;
        setOnMouseEntered(e -> {
            baseFill = fill; baseBorder = border; baseShadow = shadowColor;
            if (hoverFill != null) fill = hoverFill;
            if (hoverBorder != null) border = hoverBorder;
            if (shadowDepth > 0) shadowColor = Theme.ACCENT_DARK;
            apply();
        });
        setOnMouseExited(e -> {
            if (baseFill != null || baseBorder != null || baseShadow != null) {
                fill = baseFill; border = baseBorder; shadowColor = baseShadow;
            }
            apply();
        });
        return this;
    }

    private String baseFill, baseBorder, baseShadow;

    private void apply() {
        StringBuilder s = new StringBuilder();
        s.append("-fx-background-color: ").append(fill).append(";");
        s.append("-fx-background-radius: ").append(radius).append(";");
        if (border != null) {
            s.append("-fx-border-color: ").append(border).append(";");
            s.append("-fx-border-radius: ").append(radius).append(";");
            s.append("-fx-border-width: ").append(borderW).append(";");
        }
        setStyle(s.toString());
        if (shadowDepth > 0) {
            shadow.setOffsetY(shadowDepth);
            shadow.setColor(Color.web(shadowColor));
            setEffect(shadow);
        } else {
            setEffect(null);
        }
    }
}
