package com.wordbook.ui.modals;

import com.wordbook.service.DeepSeekService;
import com.wordbook.ui.I18n;
import com.wordbook.ui.Theme;
import com.wordbook.ui.components.FlatButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Settings window for generating today's article: domain (AI-suggested + custom),
 *  difficulty and article type. */
public class ArticleGenDialog extends Stage {

    public interface OnGenerate { void generate(String domain, String difficulty, String type); }

    private final OnGenerate onGenerate;
    private final TextField customField = new TextField();
    private final VBox domainBox = new VBox(8);
    private final List<Label> domainChips = new ArrayList<>();
    private String selectedDomain = null;
    private Runnable repaintDomains = () -> {};

    private String difficulty = "medium";
    private String type = "blog post";

    public ArticleGenDialog(Stage owner, List<String> words, OnGenerate onGenerate) {
        this.onGenerate = onGenerate;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.t("gen.title"));
        setResizable(false);

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color: " + Theme.BG + ";");
        root.setPadding(new Insets(22, 24, 20, 24));
        if (Theme.dark) root.getStyleClass().add("dark");

        Label hl = new Label("✦  " + I18n.t("gen.title"));
        hl.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 16));
        hl.setTextFill(Color.web(Theme.TEXT_MAIN));

        // Direction
        VBox dir = new VBox(6);
        dir.getChildren().addAll(section(I18n.t("gen.direction")), subtitle(I18n.t("gen.directionSub")));
        Label analyzing = new Label(I18n.t("gen.analyzing"));
        analyzing.setFont(Theme.SERIF_ITALIC);
        analyzing.setTextFill(Color.web(Theme.ACCENT_DARK));
        domainBox.getChildren().add(analyzing);
        customField.setPromptText(I18n.t("gen.custom"));
        customField.setFont(Font.font(Theme.SERIF, 12));
        customField.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.isBlank()) { selectedDomain = null; repaintDomains.run(); }
        });
        VBox.setMargin(customField, new Insets(4, 0, 0, 0));
        dir.getChildren().addAll(domainBox, customField);

        // Difficulty
        VBox diff = new VBox(8);
        diff.getChildren().addAll(section(I18n.t("gen.difficulty")),
                singleSelect(new String[]{"easy", "medium", "advanced"},
                        new String[]{I18n.t("gen.diff.easy"), I18n.t("gen.diff.medium"), I18n.t("gen.diff.advanced")},
                        "medium", v -> difficulty = v));

        // Type
        VBox typ = new VBox(8);
        typ.getChildren().addAll(section(I18n.t("gen.type")),
                singleSelect(new String[]{"blog post", "short story", "news report", "dialogue", "explainer"},
                        new String[]{I18n.t("gen.type.blog"), I18n.t("gen.type.story"), I18n.t("gen.type.news"),
                                I18n.t("gen.type.dialogue"), I18n.t("gen.type.explainer")},
                        "blog post", v -> type = v));

        // Output language (hook — English only for now)
        VBox lang = new VBox(8);
        lang.getChildren().addAll(section(I18n.t("gen.outlang")),
                singleSelect(new String[]{"en"}, new String[]{"English"}, "en", v -> {}));

        // Buttons
        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(btns, new Insets(6, 0, 0, 0));
        FlatButton cancel = new FlatButton(I18n.t("gen.cancel"), this::close)
                .colors(Theme.BG_ALT, Theme.TEXT_SUB).shadow(Theme.SHADOW)
                .font(Theme.BTN).radius(8).depth(2).pad(7, 16).outlined(true, Theme.BORDER);
        FlatButton go = new FlatButton(I18n.t("gen.generate"), this::submit)
                .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(7, 22);
        btns.getChildren().addAll(cancel, go);

        root.getChildren().addAll(hl, dir, diff, typ, lang, btns);

        Scene scene = new Scene(root, 460, 560);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close(); });
        setScene(scene);

        // Fetch domain suggestions in the background.
        Thread t = new Thread(() -> {
            List<String> domains = DeepSeekService.suggestDomains(words);
            Platform.runLater(() -> fillDomains(domains));
        }, "domain-suggest");
        t.setDaemon(true);
        t.start();
    }

    private void fillDomains(List<String> domains) {
        domainBox.getChildren().clear();
        domainChips.clear();
        FlowPane chips = new FlowPane(8, 8);
        repaintDomains = () -> {
            for (Label c : domainChips) {
                boolean sel = c.getText().equals(selectedDomain);
                c.setTextFill(Color.web(sel ? Theme.CARD : Theme.TEXT_SUB));
                c.setStyle("-fx-background-color: " + (sel ? Theme.ACCENT : Theme.CARD_ALT)
                        + "; -fx-background-radius: 8;"
                        + (sel ? "" : " -fx-border-color: " + Theme.BORDER + "; -fx-border-radius: 8; -fx-border-width: 1;"));
            }
        };
        for (String d : domains) {
            Label chip = new Label(d);
            chip.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 11));
            chip.setPadding(new Insets(6, 14, 6, 14));
            chip.setCursor(Cursor.HAND);
            chip.setOnMouseClicked(e -> { selectedDomain = d; customField.clear(); repaintDomains.run(); });
            domainChips.add(chip);
            chips.getChildren().add(chip);
        }
        domainBox.getChildren().add(chips);
        if (!domains.isEmpty()) selectedDomain = domains.get(0);
        repaintDomains.run();
    }

    private void submit() {
        String dom = customField.getText() != null && !customField.getText().isBlank()
                ? customField.getText().strip() : selectedDomain;
        if (dom == null || dom.isBlank()) return;   // need a direction
        onGenerate.generate(dom, difficulty, type);
        close();
    }

    private Label section(String text) {
        Label l = new Label(text);
        l.setFont(Theme.MONO_LABEL);
        l.setTextFill(Color.web(Theme.TEXT_SUB));
        return l;
    }

    private Label subtitle(String text) {
        Label l = new Label(text);
        l.setFont(Theme.SERIF_ITALIC);
        l.setTextFill(Color.web(Theme.TEXT_HINT));
        return l;
    }

    private FlowPane singleSelect(String[] values, String[] labels, String def, Consumer<String> setter) {
        FlowPane pane = new FlowPane(8, 8);
        Label[] chips = new Label[values.length];
        String[] cur = {def};
        Runnable repaint = () -> {
            for (int i = 0; i < values.length; i++) {
                boolean sel = values[i].equals(cur[0]);
                chips[i].setTextFill(Color.web(sel ? Theme.CARD : Theme.TEXT_SUB));
                chips[i].setStyle("-fx-background-color: " + (sel ? Theme.ACCENT : Theme.CARD_ALT)
                        + "; -fx-background-radius: 8;"
                        + (sel ? "" : " -fx-border-color: " + Theme.BORDER + "; -fx-border-radius: 8; -fx-border-width: 1;"));
            }
        };
        for (int i = 0; i < values.length; i++) {
            final String v = values[i];
            Label chip = new Label(labels[i]);
            chip.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 11));
            chip.setPadding(new Insets(6, 14, 6, 14));
            chip.setCursor(Cursor.HAND);
            chip.setOnMouseClicked(e -> { cur[0] = v; setter.accept(v); repaint.run(); });
            chips[i] = chip;
            pane.getChildren().add(chip);
        }
        setter.accept(def);
        repaint.run();
        return pane;
    }
}
