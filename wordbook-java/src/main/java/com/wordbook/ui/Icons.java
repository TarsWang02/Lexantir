package com.wordbook.ui;

import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the in-app PNG icons (src/main/resources/icons/ui) and recolours them to a
 * theme colour. The icons are single-colour line art, so we use each PNG's alpha as
 * a mask and fill it with the requested colour — that keeps them visible in both the
 * light and dark palettes and lets the same icon serve normal / active / danger states.
 */
public final class Icons {
    private Icons() {}

    private static final Map<String, Image> CACHE = new HashMap<>();

    public static Image img(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            var url = Icons.class.getResource("/icons/ui/" + n + ".png");
            return url == null ? null : new Image(url.toExternalForm());
        });
    }

    /** Tinted icon view at the given square size. Falls back to null image gracefully. */
    public static ImageView view(String name, double size, String colorHex) {
        ImageView iv = new ImageView(img(name));
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        tint(iv, size, colorHex);
        return iv;
    }

    /** Recolour an existing icon view (e.g. when hover/active state changes). */
    public static void tint(ImageView iv, double size, String colorHex) {
        Blend b = new Blend(BlendMode.SRC_ATOP);
        b.setTopInput(new ColorInput(0, 0, size, size, Color.web(colorHex)));
        iv.setEffect(b);
    }
}
