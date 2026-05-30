package com.wordbook.ui.modals;

import com.wordbook.db.Database;
import com.wordbook.model.Word;
import com.wordbook.service.Translator;
import com.wordbook.service.TtsService;
import com.wordbook.ui.I18n;
import com.wordbook.ui.Theme;
import com.wordbook.ui.components.Card;
import com.wordbook.ui.components.FlatButton;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Port of SpellCheckModal: Duolingo-style spelling quiz with letter feedback,
 *  first-try mastery, and a confetti celebration. */
public class SpellCheckModal extends Stage {

    private static final double PROG_W = 512;

    private final Runnable onComplete;
    private final List<Word> words;
    private int index = 0;
    private int correctCount = 0;
    private int masteredCount = 0;
    private boolean firstTry = true;
    private boolean showingResult = false;
    private boolean finished = false;
    private boolean awaitingRetry = false;   // wrong answer shown; clear it once user retypes

    private final VBox root = new VBox();

    // quiz widgets
    private Label progLabel, posLabel, transLabel, feedbackPill;
    private VBox sensesBox;
    private Canvas progCanvas;
    private HBox feedbackRow;
    private TextField entry;
    private FlatButton skipBtn, checkBtn;

    /** Quizzes the supplied pool (e.g. the current page's word list). */
    public SpellCheckModal(Stage owner, List<Word> pool, Runnable onComplete) {
        this.onComplete = onComplete;
        this.words = (pool == null) ? new ArrayList<>() : new ArrayList<>(pool);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.t("spell.windowTitle"));
        setResizable(false);

        root.setStyle("-fx-background-color: " + Theme.BG + ";");

        Scene scene = new Scene(root, words.isEmpty() ? 480 : 560,
                words.isEmpty() ? 340 : 520);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if (Theme.dark) root.getStyleClass().add("dark");
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close(); });
        setScene(scene);

        if (words.isEmpty()) buildEmpty();
        else buildQuiz();
    }

    // ── Empty state ──────────────────────────────────────────────────
    private void buildEmpty() {
        VBox wrap = new VBox();
        wrap.setAlignment(Pos.TOP_CENTER);
        wrap.setPadding(new Insets(20));
        VBox.setVgrow(wrap, Priority.ALWAYS);

        Label warn = new Label("⚠");
        warn.setFont(Font.font(Theme.SERIF, 56));
        warn.setTextFill(Color.web(Theme.TIER3));
        VBox.setMargin(warn, new Insets(20, 0, 8, 0));

        Label title = new Label(I18n.t("spell.emptyTitle"));
        title.setFont(Theme.SERIF_H2);
        title.setTextFill(Color.web(Theme.TEXT_MAIN));
        VBox.setMargin(title, new Insets(0, 0, 8, 0));

        Label msg = new Label(I18n.t("spell.emptyMsg"));
        msg.setFont(Theme.SERIF_SMALL);
        msg.setTextFill(Color.web(Theme.TEXT_SUB));
        msg.setWrapText(true);
        msg.setMaxWidth(380);
        msg.setTextAlignment(TextAlignment.CENTER);
        VBox.setMargin(msg, new Insets(0, 0, 20, 0));

        FlatButton close = new FlatButton(I18n.t("spell.close"), this::close)
                .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(10).depth(3).pad(8, 18);

        wrap.getChildren().addAll(warn, title, msg, close);
        root.getChildren().setAll(wrap);
    }

    // ── Quiz ─────────────────────────────────────────────────────────
    private void buildQuiz() {
        // Header
        VBox hdrWrap = new VBox();
        hdrWrap.setPadding(new Insets(14, 16, 0, 16));
        Card hdr = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(10);
        hdr.setFillWidth(true);
        HBox hi = new HBox();
        hi.setAlignment(Pos.CENTER_LEFT);
        Label htitle = new Label(I18n.t("spell.arena"));
        htitle.setFont(Theme.MONO_SMALL);
        htitle.setTextFill(Color.web(Theme.TEXT_SUB));
        HBox.setMargin(htitle, new Insets(0, 0, 0, 6));
        Region hgrow = new Region();
        HBox.setHgrow(hgrow, Priority.ALWAYS);
        Label x = new Label("✕");
        x.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 12));
        x.setTextFill(Color.web(Theme.TEXT_SUB));
        x.setCursor(javafx.scene.Cursor.HAND);
        x.setOnMouseClicked(e -> close());
        HBox.setMargin(x, new Insets(0, 6, 0, 0));
        hi.getChildren().addAll(htitle, hgrow, x);
        hdr.getChildren().add(hi);
        hdrWrap.getChildren().add(hdr);

        // Progress
        VBox prog = new VBox(6);
        prog.setPadding(new Insets(16, 24, 0, 24));
        HBox prow = new HBox();
        Label pl = new Label(I18n.t("spell.progress"));
        pl.setFont(Theme.MONO_TINY);
        pl.setTextFill(Color.web(Theme.TEXT_SUB));
        Region pgrow = new Region();
        HBox.setHgrow(pgrow, Priority.ALWAYS);
        progLabel = new Label("1 / 1 words");
        progLabel.setFont(Theme.MONO_TINY);
        progLabel.setTextFill(Color.web(Theme.TEXT_SUB));
        prow.getChildren().addAll(pl, pgrow, progLabel);
        progCanvas = new Canvas(PROG_W, 8);
        prog.getChildren().addAll(prow, progCanvas);

        // Target panel
        VBox target = new VBox();
        target.setAlignment(Pos.TOP_CENTER);
        target.setPadding(new Insets(24, 24, 0, 24));
        VBox.setVgrow(target, Priority.ALWAYS);

        posLabel = new Label("[adj]");
        posLabel.setFont(Theme.MONO_LABEL);
        posLabel.setPadding(new Insets(2, 10, 2, 10));

        transLabel = new Label("");
        // Songti (宋体) — a soft literary serif that matches the Georgia look, instead
        // of the rigid default sans fallback for Chinese.
        transLabel.setFont(Font.font("Songti SC", FontWeight.NORMAL, 26));
        transLabel.setTextFill(Color.web(Theme.TEXT_MAIN));
        transLabel.setWrapText(true);
        transLabel.setMaxWidth(440);
        transLabel.setAlignment(Pos.CENTER);            // center the line within the label
        transLabel.setTextAlignment(TextAlignment.CENTER);
        VBox.setMargin(transLabel, new Insets(14, 0, 8, 0));

        // "Hear word" — a soft rounded pill (speaker glyph + label) instead of a plain
        // text link, with a subtle hover.
        javafx.scene.image.ImageView hearIcon =
                com.wordbook.ui.Icons.view("action_hear", 15, Theme.ACCENT_DARK);
        Label hearTxt = new Label(I18n.t("spell.hear"));
        hearTxt.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 12));
        hearTxt.setTextFill(Color.web(Theme.ACCENT_DARK));
        HBox hear = new HBox(7, hearIcon, hearTxt);
        hear.setAlignment(Pos.CENTER);
        hear.setPadding(new Insets(6, 16, 6, 14));
        hear.setCursor(javafx.scene.Cursor.HAND);
        String hearBase = Theme.ACCENT_LIGHT, hearHover = Theme.darken(Theme.ACCENT_LIGHT, 0.07);
        String hearStyle = "-fx-background-radius: 18; -fx-border-radius: 18;"
                + " -fx-border-color: " + Theme.ACCENT + "; -fx-border-width: 1;"
                + " -fx-background-color: ";
        hear.setStyle(hearStyle + hearBase + ";");
        hear.setOnMouseEntered(e -> hear.setStyle(hearStyle + hearHover + ";"));
        hear.setOnMouseExited(e -> hear.setStyle(hearStyle + hearBase + ";"));
        hear.setOnMouseClicked(e -> speakCurrent());
        VBox.setMargin(hear, new Insets(2, 0, 8, 0));

        sensesBox = new VBox(2);
        sensesBox.setAlignment(Pos.CENTER);
        VBox.setMargin(sensesBox, new Insets(0, 0, 6, 0));

        target.getChildren().addAll(posLabel, transLabel, sensesBox, hear);

        // Input panel pinned at bottom
        VBox ifw = new VBox();
        ifw.setPadding(new Insets(0, 16, 16, 16));
        Card inputFrame = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(14).pad(18);
        inputFrame.setFillWidth(true);

        feedbackRow = new HBox(1);
        feedbackRow.setAlignment(Pos.CENTER);
        VBox.setMargin(feedbackRow, new Insets(0, 0, 8, 0));

        feedbackPill = new Label("");
        feedbackPill.setFont(Theme.BTN);
        feedbackPill.setTextFill(Color.web(Theme.TEXT_SUB));
        feedbackPill.setMaxWidth(Double.MAX_VALUE);
        feedbackPill.setAlignment(Pos.CENTER);
        feedbackPill.setPadding(new Insets(4, 0, 4, 0));
        VBox.setMargin(feedbackPill, new Insets(0, 0, 8, 0));

        entry = new TextField();
        entry.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 16));
        entry.setAlignment(Pos.CENTER);
        entry.setOnAction(e -> check());
        // After a wrong answer the correct-spelling feedback stays on screen; clear it
        // the moment the user starts typing their next attempt.
        entry.textProperty().addListener((o, old, nw) -> {
            if (awaitingRetry && nw != null && !nw.isEmpty()) {
                awaitingRetry = false;
                feedbackRow.getChildren().clear();
                feedbackPill.setText("");
                feedbackPill.setStyle("");
            }
        });

        HBox ctrl = new HBox(12);
        VBox.setMargin(ctrl, new Insets(12, 0, 0, 0));
        skipBtn = new FlatButton(I18n.t("spell.skip"), this::skip)
                .colors(Theme.BG_ALT, Theme.TEXT_SUB).shadow(Theme.SHADOW)
                .font(Theme.BTN).radius(8).depth(2).pad(6, 14).outlined(true, Theme.BORDER);
        checkBtn = new FlatButton(I18n.t("spell.check"), this::check)
                .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(6, 22);
        checkBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(checkBtn, Priority.ALWAYS);
        ctrl.getChildren().addAll(skipBtn, checkBtn);

        inputFrame.getChildren().addAll(feedbackRow, feedbackPill, entry, ctrl);
        ifw.getChildren().add(inputFrame);

        root.getChildren().setAll(hdrWrap, prog, target, ifw);
        loadWord();
    }

    private void loadWord() {
        showingResult = false;
        firstTry = true;
        awaitingRetry = false;
        Word word = words.get(index);
        int total = words.size();
        progLabel.setText((index + 1) + " / " + total + " " + I18n.t("spell.words"));
        drawProgress();

        String pos = (word.pos == null ? "noun" : word.pos).toLowerCase();
        String[] c = Theme.posColors(pos);
        posLabel.setText("[" + pos + "]");
        posLabel.setStyle("-fx-background-color: " + c[0] + "; -fx-background-radius: 4;");
        posLabel.setTextFill(Color.web(c[1]));
        transLabel.setText(word.translation == null || word.translation.isBlank()
                ? "—" : word.translation);

        // Multiple meanings (polysemy) for context under the prompt.
        sensesBox.getChildren().clear();
        for (String line : Translator.senseLines(Translator.parseSenses(word.senses), 3, 4)) {
            Label sl = new Label(line);
            sl.setFont(Font.font(Theme.SERIF, 11));
            sl.setTextFill(Color.web(Theme.TEXT_MUTE));
            sl.setWrapText(true);
            sl.setMaxWidth(440);
            sl.setTextAlignment(TextAlignment.CENTER);
            sensesBox.getChildren().add(sl);
        }

        feedbackRow.getChildren().clear();
        feedbackPill.setText("");
        feedbackPill.setStyle("");
        feedbackPill.setTextFill(Color.web(Theme.TEXT_SUB));
        entry.setDisable(false);
        entry.clear();
        skipBtn.setEnabledState(true);
        checkBtn.setText(I18n.t("spell.check"));
        entry.requestFocus();
    }

    private void drawProgress() {
        GraphicsContext g = progCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, PROG_W, 8);
        g.setFill(Color.web(Theme.BG_ALT));
        g.fillRect(0, 0, PROG_W, 8);
        double pct = (index + 1) / (double) Math.max(1, words.size());
        g.setFill(Color.web(Theme.ACCENT));
        g.fillRect(0, 0, PROG_W * pct, 8);
    }

    private void speakCurrent() {
        TtsService.speak(words.get(index).word);
    }

    private void check() {
        if (showingResult) return;
        Word word = words.get(index);
        String targetW = word.word.strip().toLowerCase();
        String val = entry.getText().strip().toLowerCase();
        if (val.isEmpty()) return;

        showLetterFeedback(targetW, val);

        if (val.equals(targetW)) {
            showingResult = true;
            correctCount++;
            Database.recordSpellCorrect(1);
            if (firstTry) {
                Database.markMastered(word.id);
                masteredCount++;
                feedbackPill.setText(I18n.t("spell.perfect"));
                feedbackPill.setTextFill(Color.web(Theme.CARD));
                feedbackPill.setStyle("-fx-background-color: " + Theme.TIER0 + "; -fx-background-radius: 6;");
            } else {
                feedbackPill.setText(I18n.t("spell.correct"));
                feedbackPill.setTextFill(Color.web(Theme.CARD));
                feedbackPill.setStyle("-fx-background-color: " + Theme.ACCENT + "; -fx-background-radius: 6;");
            }
            TtsService.speak(word.word);
            entry.setDisable(true);
            skipBtn.setEnabledState(false);
            after(1500, this::advance);
        } else {
            firstTry = false;
            Database.incrementWrong(word.id);
            feedbackPill.setText(I18n.t("spell.wrong"));
            feedbackPill.setTextFill(Color.web(Theme.CARD));
            feedbackPill.setStyle("-fx-background-color: " + Theme.TIER3 + "; -fx-background-radius: 6;");
            TtsService.speak(word.word);
            // Keep the correct-spelling feedback on screen (no timed reset). Empty the
            // box for the retry; the feedback clears when the user types again.
            awaitingRetry = true;
            entry.clear();
            entry.requestFocus();
        }
    }

    /**
     * Always shows the CORRECT spelling, one tile per letter. Each position is
     * compared against what the user typed: matching letters are green, wrong or
     * missing letters are red. We never display the user's incorrect letters —
     * only the correct word with the mistakes marked.
     */
    private void showLetterFeedback(String target, String typed) {
        feedbackRow.getChildren().clear();
        for (int i = 0; i < target.length(); i++) {
            char tch = target.charAt(i);
            boolean ok = i < typed.length() && typed.charAt(i) == tch;
            String bg = ok ? Theme.TIER0_BG : Theme.TIER3_BG;
            String fg = ok ? Theme.TIER0_FG : Theme.TIER3_FG;

            Label tile = new Label(String.valueOf(tch).toUpperCase());
            tile.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 14));
            tile.setTextFill(Color.web(fg));
            tile.setMinWidth(22);
            tile.setAlignment(Pos.CENTER);
            tile.setPadding(new Insets(2, 6, 2, 6));
            tile.setStyle("-fx-background-color: " + bg + ";"
                    + " -fx-border-color: " + fg + "; -fx-border-width: 1;");
            HBox.setMargin(tile, new Insets(0, 1, 0, 1));
            feedbackRow.getChildren().add(tile);
        }
    }

    private void skip() { advance(); }

    private void advance() {
        index++;
        if (index >= words.size()) showCelebration();
        else loadWord();
    }

    private void showCelebration() {
        if (finished) return;
        finished = true;
        setWidth(560);
        setHeight(680);

        int total = words.size();
        int acc = total == 0 ? 0 : (int) Math.round((correctCount / (double) total) * 100);
        String icon, title, sub, icBg, icFg;
        if (acc == 100) {
            icon = "celebrate_star"; title = I18n.t("celeb.perfect.title"); sub = I18n.t("celeb.perfect.sub");
            icBg = Theme.ACCENT_LIGHT; icFg = Theme.ACCENT;
        } else if (acc >= 75) {
            icon = "celebrate_check"; title = I18n.t("celeb.well.title"); sub = I18n.t("celeb.well.sub");
            icBg = Theme.TIER0_BG; icFg = Theme.TIER0;
        } else {
            icon = "celebrate_arrow"; title = I18n.t("celeb.good.title"); sub = I18n.t("celeb.good.sub");
            icBg = Theme.TIER1_BG; icFg = Theme.TIER1;
        }

        Canvas anim = new Canvas(560, 200);

        VBox body = new VBox();
        body.setAlignment(Pos.TOP_CENTER);
        body.setPadding(new Insets(12, 24, 12, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        javafx.scene.layout.StackPane ic =
                new javafx.scene.layout.StackPane(com.wordbook.ui.Icons.view(icon, 46, icFg));
        ic.setPadding(new Insets(16, 22, 16, 22));
        ic.setStyle("-fx-background-color: " + icBg + "; -fx-background-radius: 16;"
                + " -fx-border-color: " + Theme.ACCENT + "; -fx-border-radius: 16; -fx-border-width: 1;");
        VBox.setMargin(ic, new Insets(0, 0, 12, 0));

        Label tl = new Label(title);
        tl.setFont(Theme.SERIF_TITLE);
        tl.setTextFill(Color.web(Theme.TEXT_MAIN));
        Label sl = new Label(sub);
        sl.setFont(Theme.SERIF_ITALIC);
        sl.setTextFill(Color.web(Theme.TEXT_SUB));
        VBox.setMargin(sl, new Insets(2, 0, 18, 0));

        HBox stats = new HBox(12);
        stats.setAlignment(Pos.CENTER);
        stats.getChildren().addAll(
                statBox(correctCount + " / " + total, I18n.t("celeb.accuracy")),
                statBox(acc + "%", I18n.t("celeb.successRate")),
                statBox(String.valueOf(masteredCount), I18n.t("celeb.mastered")));

        Card hint = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(14);
        hint.setFillWidth(true);
        hint.setAlignment(Pos.CENTER);
        VBox.setMargin(hint, new Insets(18, 20, 0, 20));
        Label hk = new Label(I18n.t("celeb.logged"));
        hk.setFont(Theme.MONO_LABEL);
        hk.setTextFill(Color.web(Theme.ACCENT_DARK));
        VBox.setMargin(hk, new Insets(0, 0, 4, 0));
        Label hb = new Label(I18n.t("celeb.loggedBody"));
        hb.setFont(Theme.SERIF_SMALL);
        hb.setTextFill(Color.web(Theme.TEXT_SUB));
        hb.setWrapText(true);
        hb.setMaxWidth(400);
        hb.setTextAlignment(TextAlignment.CENTER);
        hint.getChildren().addAll(hk, hb);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        FlatButton back = new FlatButton(I18n.t("spell.back"), this::closeFinish)
                .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN_LG).radius(10).depth(3).pad(8, 20);
        back.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(back, new Insets(18, 0, 6, 0));

        body.getChildren().addAll(ic, tl, sl, stats, hint, grow, back);
        root.getChildren().setAll(anim, body);

        TtsService.speak(title);
        runConfetti(anim);
    }

    private Region statBox(String val, String lbl) {
        Card box = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(14);
        box.setAlignment(Pos.CENTER);
        Label v = new Label(val);
        v.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 18));
        v.setTextFill(Color.web(Theme.TEXT_MAIN));
        Label l = new Label(lbl);
        l.setFont(Theme.MONO_TINY);
        l.setTextFill(Color.web(Theme.TEXT_SUB));
        box.getChildren().addAll(v, l);
        return box;
    }

    private void closeFinish() {
        if (onComplete != null) onComplete.run();
        close();
    }

    // ── Confetti ─────────────────────────────────────────────────────
    private void runConfetti(Canvas c) {
        GraphicsContext g = c.getGraphicsContext2D();
        Color[] palette = {
            Color.web(Theme.ACCENT), Color.web(Theme.TIER0), Color.web(Theme.TIER3),
            Color.web(Theme.TIER1), Color.web(Theme.TIER2)
        };
        Random rnd = new Random();
        double w = c.getWidth();
        List<double[]> dots = new ArrayList<>(); // x, y, vx, vy, rx, ry, colorIdx
        for (int i = 0; i < 28; i++) {
            dots.add(new double[]{
                20 + rnd.nextDouble() * (w - 40),
                -80 + rnd.nextDouble() * 70,
                rnd.nextDouble() * 1.5 - 0.75,
                1.0 + rnd.nextDouble() * 1.5,
                4 + rnd.nextInt(5),
                2 + rnd.nextInt(3),
                rnd.nextInt(palette.length)
            });
        }
        AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) {
                g.clearRect(0, 0, c.getWidth(), c.getHeight());
                boolean alive = false;
                for (double[] d : dots) {
                    d[0] += d[2];
                    d[1] += d[3];
                    d[3] += 0.04;
                    g.setFill(palette[(int) d[6]]);
                    g.fillOval(d[0] - d[4], d[1] - d[5], d[4] * 2, d[5] * 2);
                    if (d[1] < 220) alive = true;
                }
                if (!alive) { g.clearRect(0, 0, c.getWidth(), c.getHeight()); stop(); }
            }
        };
        timer.start();
    }

    private void after(double millis, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(millis));
        p.setOnFinished(e -> r.run());
        p.play();
    }
}
