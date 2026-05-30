package com.wordbook.ui.components;

import com.wordbook.ui.Theme;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * A StackPane whose background is a faint dot grid (port of paint_dot_grid).
 * Add content with {@link #getChildren()} — it layers above the dots.
 */
public class DotGridPane extends StackPane {

    private final Canvas canvas = new Canvas();
    private final int step;
    private final double radius;
    private final Color dot;

    public DotGridPane() { this(24, 1, Theme.DOT, Theme.BG); }

    public DotGridPane(int step, double radius, String dotColor, String bgColor) {
        this.step = step;
        this.radius = radius;
        this.dot = Color.web(dotColor);
        setStyle("-fx-background-color: " + bgColor + ";");
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
        widthProperty().addListener((o, a, b) -> repaint());
        heightProperty().addListener((o, a, b) -> repaint());
    }

    private void repaint() {
        double w = getWidth(), h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(dot);
        for (double x = step; x < w + step; x += step) {
            for (double y = step; y < h + step; y += step) {
                g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }
    }
}
