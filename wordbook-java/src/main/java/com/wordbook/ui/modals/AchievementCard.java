package com.wordbook.ui.modals;

import com.wordbook.db.Database;
import com.wordbook.model.Article;
import com.wordbook.service.DeepSeekService;
import com.wordbook.ui.I18n;
import com.wordbook.ui.Theme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.Map;

/**
 * Portrait (iOS-proportioned) reward card shown after finishing today's article:
 * a domain image (placeholder for now), a domain-related quote, and this week's
 * study activity.
 */
public class AchievementCard extends Stage {

    public AchievementCard(Stage owner, Article article) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.t("ach.completed"));
        setResizable(false);

        String domain = article.domain == null ? "" : article.domain;

        VBox root = new VBox(0);
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: " + Theme.BG + ";");
        if (Theme.dark) root.getStyleClass().add("dark");

        // ── Kicker
        Label kicker = new Label("✦  " + I18n.t("ach.completed"));
        kicker.setFont(Theme.MONO_LABEL);
        kicker.setTextFill(Color.web(Theme.ACCENT_DARK));
        VBox.setMargin(kicker, new Insets(22, 0, 12, 0));

        // ── Domain image (placeholder gradient + domain name) — main subject
        StackPane image = new StackPane();
        image.setPrefSize(296, 224);
        image.setMinSize(296, 224);
        image.setMaxSize(296, 224);
        image.setStyle("-fx-background-radius: 20;"
                + " -fx-background-color: linear-gradient(to bottom right, "
                + Theme.ACCENT_LIGHT + ", " + Theme.ACCENT + ");");
        Label domLbl = new Label(domain);
        domLbl.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 24));
        domLbl.setTextFill(Color.web(Theme.dark ? Theme.TEXT_MAIN : Theme.ACCENT_DARK));
        domLbl.setWrapText(true);
        domLbl.setMaxWidth(240);
        domLbl.setTextAlignment(TextAlignment.CENTER);
        StackPane.setAlignment(domLbl, Pos.CENTER);
        image.getChildren().add(domLbl);

        // ── Quote (loaded from the API)
        Label quote = new Label("…");
        quote.setFont(Font.font(Theme.SERIF, FontPosture.ITALIC, 14));
        quote.setTextFill(Color.web(Theme.TEXT_MAIN));
        quote.setWrapText(true);
        quote.setMaxWidth(280);
        quote.setTextAlignment(TextAlignment.CENTER);
        quote.setAlignment(Pos.CENTER);
        VBox.setMargin(quote, new Insets(22, 0, 0, 0));

        // ── This week
        Label weekHdr = new Label(I18n.t("ach.thisWeek"));
        weekHdr.setFont(Theme.MONO_LABEL);
        weekHdr.setTextFill(Color.web(Theme.TEXT_SUB));
        VBox.setMargin(weekHdr, new Insets(24, 0, 10, 0));

        HBox week = buildWeek();
        VBox.setMargin(week, new Insets(0, 0, 24, 0));

        root.getChildren().addAll(kicker, image, quote, weekHdr, week);

        Scene scene = new Scene(root, 340, 520);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.setFill(Theme.color(Theme.BG));
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close(); });
        setScene(scene);

        // Fetch the domain quote in the background.
        Thread t = new Thread(() -> {
            String q = DeepSeekService.domainQuote(domain);
            Platform.runLater(() -> quote.setText(q == null || q.isBlank() ? "—" : q));
        }, "domain-quote");
        t.setDaemon(true);
        t.start();
    }

    /** Last 7 days as a row of heat cells (today on the right). */
    private HBox buildWeek() {
        Map<String, Integer> counts = Database.getSpellCounts();
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        LocalDate today = LocalDate.now();
        String[] dow = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int c = counts.getOrDefault(d.toString(), 0);
            VBox cell = new VBox(4);
            cell.setAlignment(Pos.CENTER);
            Region box = new Region();
            box.setMinSize(26, 26);
            box.setPrefSize(26, 26);
            box.setStyle("-fx-background-color: " + Theme.heatColor(c) + "; -fx-background-radius: 6;");
            Label dl = new Label(dow[d.getDayOfWeek().getValue() % 7]);
            dl.setFont(Theme.MONO_TINY);
            dl.setTextFill(Color.web(Theme.TEXT_HINT));
            cell.getChildren().addAll(box, dl);
            row.getChildren().add(cell);
        }
        return row;
    }
}
