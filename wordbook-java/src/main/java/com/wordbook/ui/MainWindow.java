package com.wordbook.ui;

import com.wordbook.data.Articles;
import com.wordbook.db.Database;
import com.wordbook.model.Word;
import com.wordbook.service.TtsService;
import com.wordbook.service.Translator;
import com.wordbook.ui.components.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Root window: sidebar (brand + spell button + nav + stats) and main pane
 * (header + scrollable dot-grid content). Acts as the controller for views.
 * Mirrors WordBookApp in main.py.
 */
public class MainWindow extends BorderPane {

    static final double SIDEBAR_W = 220;

    /** key, label, glyph */
    static final String[][] NAV_ITEMS = {
        {"daily",      "Daily Pool",   "◆"},
        {"unfinished", "Unfinished",   "◇"},
        {"all",        "All Words",    "▦"},
        {"reading",    "Reading Room", "✦"},
        {"calendar",   "Calendar",     "▢"},
        {"settings",   "Setting",      "⚙"},
    };
    static final Map<String, String> HEADER_TITLES = Map.ofEntries(
        Map.entry("daily",      "Daily Pool"),
        Map.entry("unfinished", "Unfinished Words"),
        Map.entry("all",        "Mastered Archive"),
        Map.entry("reading",    "Interactive Reading Room"),
        Map.entry("calendar",   "Self-Evaluation Analytics"),
        Map.entry("settings",   "Settings")
    );

    final Stage stage;

    // view state
    String currentView = "daily";
    String searchText = "";
    boolean selectMode = false;
    final Set<Integer> selectedIds = new HashSet<>();
    boolean spellActive = false;   // true while a Spell Check quiz is open

    // reading-room state
    String readingArticleId = com.wordbook.data.Articles.SAMPLE_ARTICLES.get(0).id();
    boolean readingCustomMode = false;
    boolean readingTodayMode = true;     // Reading Room opens on "Today's Article"
    boolean articleGenerating = false;
    String readingCustomText = "";

    // widgets
    private SpellCheckButton spellBtn;
    private Label titleLabel;
    private Label statStreak, statMastered, statDaily;
    private final Map<String, NavRow> navRows = new LinkedHashMap<>();
    private final VBox contentBox = new VBox();
    private final VBox cardsBox = new VBox();
    private ScrollPane scroll;

    private static final Font BTN10 = Font.font(Theme.SERIF, FontWeight.BOLD, 10);

    /** The local date that the current 4 AM→4 AM daily session started on. Used to
     *  detect when we cross a new 4 AM boundary so the rollover runs once per day. */
    private java.time.LocalDate currentSessionDate() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return now.toLocalTime().isBefore(java.time.LocalTime.of(4, 0))
                ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }
    private java.time.LocalDate lastSweepSession = null;

    public MainWindow(Stage stage) {
        this.stage = stage;
        setStyle("-fx-background-color: " + Theme.BG + ";");
        syncDarkClass();
        setLeft(buildSidebar());
        setCenter(buildMain());
        Database.rolloverDailyPool();             // catch up on any missed 4 AM boundary
        lastSweepSession = currentSessionDate();  // …and mark this session handled
        loadView("daily");
        startRolloverTimer();
        backfillSenses();   // fill in meanings for existing words (one-time, background)
    }

    /** Toggle the CSS "dark" class on the scene root so app.css picks the dark tokens. */
    private void syncDarkClass() {
        getStyleClass().remove("dark");
        if (Theme.dark) getStyleClass().add("dark");
    }

    /** Switch appearance (light / dark / follow-system), persist it, and recolour. */
    void changeTheme(Theme.Mode choice) {
        Theme.apply(choice);
        Database.setMeta("theme", choice.name());
        rebuildUi();
    }

    /** Rebuild the whole UI so every programmatically-coloured node picks up the
     *  new palette (colours are baked into nodes at build time). */
    private void rebuildUi() {
        setStyle("-fx-background-color: " + Theme.BG + ";");
        syncDarkClass();
        setLeft(buildSidebar());
        setCenter(buildMain());
        loadView(currentView);
        if (getScene() != null) getScene().setFill(Theme.color(Theme.BG));
    }

    /**
     * Runs the daily→unfinished rollover at most ONCE per 4 AM boundary:
     *   • only fires when we've crossed into a new session (a 4 AM has passed),
     *   • if a Spell Check is open it's deferred — we call this again when it closes.
     * Outside the 4 AM crossing this is a no-op, so the pool isn't churned all day.
     */
    private void maybeRollover() {
        if (spellActive) return;
        java.time.LocalDate sess = currentSessionDate();
        if (sess.equals(lastSweepSession)) return;   // already handled this session
        lastSweepSession = sess;
        Database.rolloverDailyPool();
        if ("daily".equals(currentView) || "unfinished".equals(currentView)) refreshView();
        updateStats();
    }

    /** Ticks often enough to notice the 4 AM boundary; the work itself runs once/day. */
    private void startRolloverTimer() {
        javafx.animation.Timeline t = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.minutes(2), e -> maybeRollover()));
        t.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        t.play();
    }

    // ── Sidebar ─────────────────────────────────────────────────────
    private Region buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(SIDEBAR_W);
        sb.setMinWidth(SIDEBAR_W);
        sb.setStyle("-fx-background-color: " + Theme.SIDEBAR + ";");

        // Brand
        VBox brand = new VBox(2);
        brand.setPadding(new Insets(22, 20, 12, 20));
        Label name = new Label("LEXANTIR");
        name.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 22));
        name.setTextFill(Color.web(Theme.TEXT_MAIN));
        Label tag = new Label(I18n.t("brand.tagline"));
        tag.setFont(Theme.MONO_TINY);
        tag.setTextFill(Color.web(Theme.TEXT_SUB));
        brand.getChildren().addAll(name, tag);

        // Spell check button
        StackPane topWrap = new StackPane();
        topWrap.setPadding(new Insets(8, 18, 14, 18));
        spellBtn = new SpellCheckButton(this::openSpellCheck);
        spellBtn.setMaxWidth(Double.MAX_VALUE);
        topWrap.getChildren().add(spellBtn);

        // Nav
        VBox nav = new VBox(4);
        nav.setPadding(new Insets(0, 14, 8, 14));
        for (String[] item : NAV_ITEMS) {
            NavRow row = new NavRow(item[0], I18n.t("nav." + item[0]), item[2]);
            navRows.put(item[0], row);
            nav.getChildren().add(row);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Stats footer
        VBox stats = new VBox(3);
        stats.setPadding(new Insets(8, 20, 22, 20));
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setStyle("-fx-background-color: " + Theme.BORDER + ";");
        VBox.setMargin(sep, new Insets(0, 0, 14, 0));
        statStreak = new Label("—");
        statMastered = new Label("—");
        statDaily = new Label("—");
        stats.getChildren().addAll(sep,
                statRow("🔥 " + I18n.t("stat.streak"), statStreak),
                statRow("🎓 " + I18n.t("stat.mastered"), statMastered),
                statRow("📖 " + I18n.t("stat.pool"), statDaily));

        sb.getChildren().addAll(brand, topWrap, nav, spacer, stats);
        return sb;
    }

    private HBox statRow(String label, Label valueLabel) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.setFont(Theme.SERIF_SMALL);
        l.setTextFill(Color.web(Theme.TEXT_SUB));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        valueLabel.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 13));
        valueLabel.setTextFill(Color.web(Theme.TEXT_MAIN));
        row.getChildren().addAll(l, grow, valueLabel);
        return row;
    }

    /** A clickable sidebar nav row with hover + active states. */
    private class NavRow extends HBox {
        final String key;
        final Label label, count;
        final ImageView iconView;
        boolean active = false;

        NavRow(String key, String text, String glyph) {
            this.key = key;
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(2, 4, 2, 4));
            setCursor(javafx.scene.Cursor.HAND);
            setStyle("-fx-background-radius: 8;");

            iconView = Icons.view("nav_" + key, 18, Theme.TEXT_SUB);
            HBox.setMargin(iconView, new Insets(0, 0, 0, 12));

            label = new Label(text);
            label.setFont(Font.font(Theme.SERIF, 12));
            label.setTextFill(Color.web(Theme.TEXT_SUB));
            label.setPadding(new Insets(7, 10, 7, 9));
            HBox.setHgrow(label, Priority.ALWAYS);
            label.setMaxWidth(Double.MAX_VALUE);

            count = new Label("");
            count.setFont(Theme.MONO_TINY);
            count.setTextFill(Color.web(Theme.TEXT_SUB));
            count.setPadding(new Insets(0, 10, 0, 10));

            getChildren().addAll(iconView, label, count);
            setOnMouseClicked(e -> loadView(key));
            setOnMouseEntered(e -> { if (!active) paint(Theme.CARD, Theme.TEXT_MAIN, false); });
            setOnMouseExited(e -> { if (!active) paint(Theme.SIDEBAR, Theme.TEXT_SUB, false); });
        }

        void setActive(boolean a) {
            this.active = a;
            if (a) paint(Theme.CARD, Theme.ACCENT_TEXT, true);
            else paint(Theme.SIDEBAR, Theme.TEXT_SUB, false);
        }

        void paint(String bg, String fg, boolean bold) {
            setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 8;");
            label.setTextFill(Color.web(fg));
            label.setFont(Font.font(Theme.SERIF, bold ? FontWeight.BOLD : FontWeight.NORMAL, 12));
            count.setTextFill(Color.web(fg.equals(Theme.TEXT_MAIN) ? Theme.TEXT_SUB : fg));
            Icons.tint(iconView, 18, fg);
        }
    }

    // ── Main pane ───────────────────────────────────────────────────
    private Region buildMain() {
        VBox main = new VBox();
        main.setStyle("-fx-background-color: " + Theme.BG + ";");

        // header
        VBox header = new VBox(2);
        header.setPadding(new Insets(28, 36, 16, 36));
        Label kicker = new Label("LEXANTIR JOURNAL");
        kicker.setFont(Theme.MONO_LABEL);
        kicker.setTextFill(Color.web(Theme.ACCENT_DARK));
        titleLabel = new Label("—");
        titleLabel.setFont(Theme.SERIF_TITLE);
        titleLabel.setTextFill(Color.web(Theme.TEXT_MAIN));
        header.getChildren().addAll(kicker, titleLabel);

        Region sep = new Region();
        sep.setMinHeight(2);
        sep.setMaxHeight(2);
        sep.setStyle("-fx-background-color: " + Theme.BORDER + ";");
        VBox.setMargin(sep, new Insets(0, 36, 0, 36));

        // content (dot grid + scroll)
        DotGridPane grid = new DotGridPane();
        VBox.setVgrow(grid, Priority.ALWAYS);

        contentBox.setFillWidth(true);
        contentBox.setPadding(new Insets(20, 36, 24, 36));

        scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // NOTE: do NOT set "-fx-background: transparent" here — that poisons modena's
        // text-colour laddering for every Label in this subtree (they get computed as
        // white). Keep -fx-background a light colour; transparency comes purely from
        // -fx-background-color (+ the .scroll-pane rules in app.css).
        scroll.setStyle("-fx-background: " + Theme.BG + "; -fx-background-color: transparent;");
        scroll.getStyleClass().add("edge-to-edge");
        grid.getChildren().add(scroll);

        main.getChildren().addAll(header, sep, grid);
        return main;
    }

    // ── View routing ────────────────────────────────────────────────
    public void loadView(String view) {
        currentView = view;
        selectMode = false;
        selectedIds.clear();
        searchText = "";
        titleLabel.setText(I18n.t("hdr." + view));
        navRows.forEach((k, r) -> r.setActive(k.equals(view)));
        updateStats();
        refreshView();
        if (scroll != null) {
            // Reading Room has auto-height text surfaces whose width feeds back into
            // their height; a scrollbar that appears/disappears on hover would make
            // them re-wrap and visibly resize. Reserve the scrollbar permanently here
            // so the content width stays constant.
            scroll.setVbarPolicy("reading".equals(view)
                    ? ScrollPane.ScrollBarPolicy.ALWAYS
                    : ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setVvalue(0);
        }
    }

    public void refreshView() {
        contentBox.getChildren().clear();
        switch (currentView) {
            case "daily"      -> renderDaily();
            case "unfinished" -> renderUnfinished();
            case "all"        -> renderAllWords();
            case "reading"    -> renderReadingRoom();
            case "calendar"   -> renderCalendar();
            case "settings"   -> renderSettings();
            default -> {}
        }
    }

    void updateStats() {
        List<Word> daily = Database.getTodaySessionWords();   // today's words (incl. mastered)
        List<Word> mastered = Database.getMasteredWords();
        int streak = Database.getStreak();

        int unfinished = Database.getUnfinishedWords().size();
        statStreak.setText(streak + " " + I18n.t(streak == 1 ? "unit.day" : "unit.days"));
        statMastered.setText(String.valueOf(mastered.size()));
        statDaily.setText(String.valueOf(daily.size()));
        navRows.get("daily").count.setText(String.valueOf(daily.size()));
        navRows.get("unfinished").count.setText(String.valueOf(unfinished));
        navRows.get("all").count.setText(String.valueOf(mastered.size()));
        // Spell Check quizzes the CURRENT page's pool, so its badge reflects that.
        spellBtn.setCount(spellPoolForCurrentView().size());
    }

    // ── View renderers ──────────────────────────────────────────────
    private void renderDaily()       { renderWordList(false); }
    private void renderUnfinished()  { renderWordList(false); }
    private void renderAllWords()    { renderWordList(true); }

    // ── Settings ────────────────────────────────────────────────────
    private void renderSettings() {
        // Appearance
        Card appearance = settingsCard(I18n.t("settings.appearance"), I18n.t("settings.appearanceSub"));
        VBox opts = new VBox(10);
        opts.getChildren().addAll(
            optionRow(I18n.t("settings.day"),    I18n.t("settings.daySub"),    "☀",
                    Theme.choice() == Theme.Mode.LIGHT,  () -> changeTheme(Theme.Mode.LIGHT)),
            optionRow(I18n.t("settings.night"),  I18n.t("settings.nightSub"),  "☾",
                    Theme.choice() == Theme.Mode.DARK,   () -> changeTheme(Theme.Mode.DARK)),
            optionRow(I18n.t("settings.system"), I18n.t("settings.systemSub"), "⌥",
                    Theme.choice() == Theme.Mode.SYSTEM, () -> changeTheme(Theme.Mode.SYSTEM))
        );
        appearance.getChildren().add(opts);
        contentBox.getChildren().add(appearance);

        // Language
        Card langCard = settingsCard(I18n.t("settings.language"), I18n.t("settings.languageSub"));
        VBox lopts = new VBox(10);
        for (I18n.Lang lng : I18n.Lang.values()) {
            lopts.getChildren().add(optionRow(lng.label, "", "✎",
                    I18n.lang() == lng, () -> changeLang(lng)));
        }
        langCard.getChildren().add(lopts);
        contentBox.getChildren().add(langCard);
    }

    private Card settingsCard(String title, String subtitle) {
        Card card = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(18).pad(24);
        card.setFillWidth(true);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));
        card.getChildren().add(monoLabel(title, Theme.ACCENT_DARK));
        if (subtitle != null && !subtitle.isEmpty()) {
            Label sub = new Label(subtitle);
            sub.setFont(Theme.SERIF_ITALIC);
            sub.setTextFill(Color.web(Theme.TEXT_SUB));
            sub.setWrapText(true);
            sub.setMaxWidth(560);
            VBox.setMargin(sub, new Insets(4, 0, 16, 0));
            card.getChildren().add(sub);
        }
        return card;
    }

    /** A selectable settings row (icon + title + optional subtitle + ●/○ marker). */
    private Node optionRow(String title, String desc, String glyph, boolean selected, Runnable onPick) {
        Card row = new Card()
                .fill(selected ? Theme.ACCENT_LIGHT : Theme.CARD_ALT)
                .border(selected ? Theme.ACCENT : Theme.BORDER, selected ? 2 : 1)
                .radius(12).pad(14, 16);
        row.setFillWidth(true);
        row.setCursor(Cursor.HAND);

        HBox h = new HBox(14);
        h.setAlignment(Pos.CENTER_LEFT);

        Label g = new Label(glyph);
        g.setFont(Font.font(Theme.SERIF, 20));
        g.setTextFill(Color.web(selected ? Theme.ACCENT_DARK : Theme.TEXT_SUB));

        VBox txt = new VBox(2);
        HBox.setHgrow(txt, Priority.ALWAYS);
        Label t = new Label(title);
        t.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 14));
        t.setTextFill(Color.web(Theme.TEXT_MAIN));
        txt.getChildren().add(t);
        if (desc != null && !desc.isEmpty()) {
            Label d = new Label(desc);
            d.setFont(Theme.SERIF_SMALL);
            d.setTextFill(Color.web(Theme.TEXT_SUB));
            txt.getChildren().add(d);
        }

        Label check = new Label(selected ? "●" : "○");
        check.setFont(Font.font(Theme.SERIF, 14));
        check.setTextFill(Color.web(selected ? Theme.ACCENT : Theme.TEXT_HINT));

        h.getChildren().addAll(g, txt, check);
        row.getChildren().add(h);
        row.setOnMouseClicked(e -> { if (!selected) onPick.run(); });
        return row;
    }

    /** Switch the interface language, persist it, and rebuild the UI. */
    void changeLang(I18n.Lang lng) {
        I18n.set(lng);
        Database.setMeta("lang", lng.name());
        rebuildUi();
    }

    /** First-run modal that lets the user pick a language (shown when none is saved). */
    public void promptLanguage() {
        Stage dlg = new Stage();
        dlg.initOwner(stage);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setTitle("Language · 语言 · 言語");
        dlg.setResizable(false);

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28));
        box.setStyle("-fx-background-color: " + Theme.BG + ";");
        if (Theme.dark) box.getStyleClass().add("dark");

        Label title = new Label("Choose your language\n选择语言 · 言語を選択");
        title.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 16));
        title.setTextFill(Color.web(Theme.TEXT_MAIN));
        title.setTextAlignment(TextAlignment.CENTER);

        VBox opts = new VBox(10);
        opts.setFillWidth(true);
        for (I18n.Lang lng : I18n.Lang.values()) {
            FlatButton b = new FlatButton(lng.label, () -> { dlg.close(); changeLang(lng); })
                    .colors(Theme.CARD_ALT, Theme.TEXT_MAIN).hover(Theme.ACCENT_LIGHT)
                    .shadow(Theme.SHADOW).font(Font.font(Theme.SERIF, FontWeight.BOLD, 15))
                    .radius(10).depth(2).pad(10, 24).outlined(true, Theme.BORDER);
            b.setMaxWidth(Double.MAX_VALUE);
            opts.getChildren().add(b);
        }
        box.getChildren().addAll(title, opts);

        Scene sc = new Scene(box, 340, 300);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        dlg.setScene(sc);
        dlg.showAndWait();
    }

    // ── Daily Pool / All Words ───────────────────────────────────────
    private void renderWordList(boolean mastered) {
        contentBox.getChildren().add(buildToolbar(mastered));
        cardsBox.getChildren().clear();
        cardsBox.setFillWidth(true);
        contentBox.getChildren().add(cardsBox);
        populateCards(mastered);
    }

    private List<Word> filteredWords(boolean mastered) {
        List<Word> words;
        if (mastered)                              words = Database.getMasteredWords();
        else if ("unfinished".equals(currentView)) words = Database.getUnfinishedWords();
        else                                       words = Database.getTodaySessionWords();
        String q = searchText.strip().toLowerCase();
        if (q.isEmpty()) return words;
        return words.stream().filter(w ->
                contains(w.word, q) || contains(w.translation, q)
                        || contains(w.example, q) || contains(w.note, q)
        ).collect(Collectors.toList());
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private Region buildToolbar(boolean mastered) {
        Card toolbar = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(14).pad(14);
        VBox.setMargin(toolbar, new Insets(0, 0, 16, 0));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        // search
        HBox searchWrap = new HBox(4);
        searchWrap.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchWrap, Priority.ALWAYS);
        Label mag = new Label("🔍");
        mag.setTextFill(Color.web(Theme.TEXT_SUB));
        TextField search = new TextField(searchText);
        search.setPromptText(I18n.t("toolbar.search"));
        search.setFont(Theme.SERIF_BODY);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((o, a, b) -> { searchText = b; populateCards(mastered); });
        searchWrap.getChildren().addAll(mag, search);

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(
            new FlatButton(I18n.t("toolbar.export"), this::chooseExport)
                .colors(Theme.BG_ALT, Theme.TEXT_SUB).hover(Theme.ACCENT_LIGHT)
                .shadow(Theme.SHADOW).font(BTN10).radius(8).depth(2).pad(5, 10)
                .icon("action_export", 13),
            new FlatButton(I18n.t(selectMode ? "toolbar.select.on" : "toolbar.select"), this::toggleSelectMode)
                .colors(selectMode ? Theme.ACCENT_LIGHT : Theme.CARD_ALT,
                        selectMode ? Theme.ACCENT_DARK : Theme.TEXT_SUB)
                .hover(Theme.ACCENT_LIGHT).shadow(Theme.SHADOW)
                .font(BTN10).radius(8).depth(2).pad(5, 10)
                .outlined(true, Theme.BORDER)
                .icon(selectMode ? "action_select_active" : "action_select", 13)
        );
        if ("daily".equals(currentView)) {
            actions.getChildren().add(
                new FlatButton(I18n.t("toolbar.addword"), () -> openAddDialog(""))
                    .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                    .shadow(Theme.ACCENT_DARK).font(BTN10).radius(8).depth(3).pad(5, 12)
                    .icon("action_add", 13)
            );
        }

        row.getChildren().addAll(searchWrap, actions);
        toolbar.getChildren().add(row);
        return toolbar;
    }

    private void populateCards(boolean mastered) {
        cardsBox.getChildren().clear();
        List<Word> words = filteredWords(mastered);
        boolean searching = !searchText.strip().isEmpty();

        if (words.isEmpty()) {
            Card empty = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(16).pad(48);
            VBox.setMargin(empty, new Insets(24, 8, 24, 8));
            String msg;
            if (searching) {
                msg = I18n.t("empty.search");
            } else if (mastered) {
                msg = I18n.t("empty.mastered");
            } else if ("unfinished".equals(currentView)) {
                msg = I18n.t("empty.unfinished");
            } else {
                msg = I18n.t("empty.daily");
            }
            Label l = new Label(msg);
            l.setFont(Theme.SERIF_ITALIC);
            l.setTextFill(Color.web(Theme.TEXT_SUB));
            l.setWrapText(true);
            l.setMaxWidth(520);
            l.setAlignment(Pos.CENTER);
            empty.setAlignment(Pos.CENTER);
            empty.getChildren().add(l);
            cardsBox.getChildren().add(empty);
            return;
        }

        // The select / batch bar sits at the TOP so "All / None" is reachable immediately.
        if (selectMode) cardsBox.getChildren().add(buildBatchBar(mastered));

        for (Word w : words) cardsBox.getChildren().add(buildWordCard(w, mastered));
    }

    private Node buildWordCard(Word word, boolean mastered) {
        int wid = word.id;
        int wc = word.wrongCount;

        Card card = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(18).pad(0)
                .shadow(4, Theme.SHADOW).hover(Theme.CARD_ALT, Theme.ACCENT).enableHover();
        VBox.setMargin(card, new Insets(0, 0, 14, 0));

        HBox outer = new HBox();
        outer.setFillHeight(true);

        // left rim
        boolean doneToday = !mastered && word.mastered == 1;   // mastered word shown in Daily Pool
        String rim = null;
        if (mastered) rim = Theme.wrongTier(wc).rim();
        else if (doneToday) rim = Theme.TIER0;                 // completed today → green
        else if (wc > 0) rim = Theme.TIER3;
        if (rim != null) {
            Region bar = new Region();
            bar.setMinWidth(7); bar.setPrefWidth(7); bar.setMaxHeight(Double.MAX_VALUE);
            // Plain rectangle — the card's rounded clip (below) trims its corners so
            // the strip lines up flush with the card's rounded edges.
            bar.setStyle("-fx-background-color: " + rim + ";");
            outer.getChildren().add(bar);
        }

        // checkbox column (select mode)
        if (selectMode) {
            CheckBox cb = new CheckBox();
            cb.setSelected(selectedIds.contains(wid));
            cb.setOnAction(e -> {
                if (cb.isSelected()) selectedIds.add(wid); else selectedIds.remove(wid);
                populateCards(mastered);
            });
            HBox cbCol = new HBox(cb);
            cbCol.setAlignment(Pos.CENTER);
            cbCol.setPadding(new Insets(0, 0, 0, 12));
            outer.getChildren().add(cbCol);
        }

        VBox body = new VBox();
        body.setPadding(new Insets(18));
        HBox.setHgrow(body, Priority.ALWAYS);
        outer.getChildren().add(body);

        // top row
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        HBox leftRow = new HBox(10);
        leftRow.setAlignment(Pos.CENTER_LEFT);

        String pos = (word.pos == null ? "noun" : word.pos).toLowerCase();
        String[] pc = Theme.posColors(pos);
        leftRow.getChildren().add(new Pill("[" + pos + "]", pc[0], pc[1], Theme.MONO_LABEL, 8, 3, 6));

        Label wordLbl = new Label(word.word);
        wordLbl.setFont(Theme.SERIF_H3);
        wordLbl.setTextFill(Color.web(Theme.TEXT_MAIN));
        wordLbl.setCursor(Cursor.HAND);
        wordLbl.setOnMouseClicked(e -> TtsService.speak(word.word));
        leftRow.getChildren().add(wordLbl);

        if (mastered) {
            Theme.WrongTier t = Theme.wrongTier(wc);
            leftRow.getChildren().add(new Pill(t.badge(), t.badgeBg(), t.badgeFg(), Theme.MONO_LABEL, 8, 3, 8));
        } else if (doneToday) {
            leftRow.getChildren().add(new Pill("✓", Theme.TIER0_BG, Theme.TIER0_FG, Theme.MONO_LABEL, 8, 3, 8));
        } else if (wc > 0) {
            leftRow.getChildren().add(new Pill("× " + wc, Theme.TIER3_BG, Theme.TIER3_FG, Theme.MONO_LABEL, 8, 3, 8));
        }

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox tbar = new HBox(6);
        tbar.setAlignment(Pos.CENTER_RIGHT);
        // Flag = "pick this word for Spell Check". Available on every page so you can
        // select words within each of the three pools (daily / unfinished / all).
        boolean inPractice = "practice".equals(word.status);
        IconButton flag = new IconButton("", null,
                Theme.TEXT_SUB, Theme.CARD_ALT, Theme.BORDER,
                Theme.ACCENT_LIGHT, Theme.ACCENT_TEXT,
                inPractice, Theme.ACCENT, Theme.CARD, 30, 9, Font.font(Theme.SERIF, 13));
        flag.icon("action_practice", "action_practice_active", 16);
        flag.setOnMouseClicked(e -> togglePractice(wid));
        tbar.getChildren().add(flag);
        tbar.getChildren().addAll(
            new IconButton("", () -> TtsService.speak(word.word)).icon("action_hear", null, 16),
            new IconButton("", () -> openNoteDialog(word)).icon("action_note", null, 16),
            IconButton.danger("", () -> deleteWord(wid)).icon("action_delete_cross", null, 16)
        );

        top.getChildren().addAll(leftRow, grow, tbar);
        body.getChildren().add(top);

        // translation
        if (word.translation != null && !word.translation.isBlank()) {
            Label tr = new Label(word.translation);
            tr.setFont(Theme.SERIF_BODY);
            tr.setTextFill(Color.web(Theme.TEXT_SUB));
            tr.setWrapText(true);
            tr.setMaxWidth(Double.MAX_VALUE);
            VBox.setMargin(tr, new Insets(8, 0, 0, 0));
            body.getChildren().add(tr);
        }

        // multiple meanings (polysemy), grouped by part-of-speech
        List<String> senseLines = Translator.senseLines(Translator.parseSenses(word.senses), 2, 4);
        if (!senseLines.isEmpty()) {
            VBox sv = new VBox(2);
            VBox.setMargin(sv, new Insets(6, 0, 0, 0));
            for (String line : senseLines) {
                Label l = new Label(line);
                l.setFont(Font.font(Theme.SERIF, 11));
                l.setTextFill(Color.web(Theme.TEXT_MUTE));
                l.setWrapText(true);
                l.setMaxWidth(Double.MAX_VALUE);
                sv.getChildren().add(l);
            }
            body.getChildren().add(sv);
        }

        // example
        if (word.hasExample()) {
            HBox exWrap = new HBox(10);
            VBox.setMargin(exWrap, new Insets(10, 0, 0, 0));
            Region exBar = new Region();
            exBar.setMinWidth(2); exBar.setPrefWidth(2); exBar.setMaxHeight(Double.MAX_VALUE);
            exBar.setStyle("-fx-background-color: " + Theme.BORDER + ";");
            VBox exText = new VBox(2);
            HBox.setHgrow(exText, Priority.ALWAYS);
            Label ex = new Label(word.example);
            ex.setFont(Theme.SERIF_ITALIC);
            ex.setTextFill(Color.web(Theme.TEXT_SUB));
            ex.setWrapText(true);
            ex.setMaxWidth(Double.MAX_VALUE);
            exText.getChildren().add(ex);
            if (word.exampleTranslation != null && !word.exampleTranslation.isBlank()) {
                Label ext = new Label(word.exampleTranslation);
                ext.setFont(Theme.SERIF_TINY);
                ext.setTextFill(Color.web(Theme.TEXT_HINT));
                ext.setWrapText(true);
                ext.setMaxWidth(Double.MAX_VALUE);
                exText.getChildren().add(ext);
            }
            exWrap.getChildren().addAll(exBar, exText);
            body.getChildren().add(exWrap);
        }

        // note pill
        if (word.hasNote()) {
            Card note = new Card().fill(Theme.ACCENT_LIGHT).border(Theme.ACCENT).radius(10).pad(10);
            VBox.setMargin(note, new Insets(12, 0, 0, 0));
            HBox ni = new HBox(8);
            ni.setAlignment(Pos.TOP_LEFT);
            Label pen = new Label("✎");
            pen.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 12));
            pen.setTextFill(Color.web(Theme.ACCENT_DARK));
            Label nt = new Label(word.note);
            nt.setFont(Theme.SERIF_ITALIC);
            nt.setTextFill(Color.web(Theme.TEXT_SUB));
            nt.setWrapText(true);
            nt.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nt, Priority.ALWAYS);
            ni.getChildren().addAll(pen, nt);
            note.getChildren().add(ni);
            body.getChildren().add(note);
        }

        // mastered timestamp
        if (mastered && word.masteredAt != null && word.masteredAt.length() >= 10) {
            Label ts = new Label(I18n.t("card.graduated") + word.masteredAt.substring(0, 10));
            ts.setFont(Theme.MONO_TINY);
            ts.setTextFill(Color.web(Theme.TEXT_HINT));
            ts.setMaxWidth(Double.MAX_VALUE);
            ts.setAlignment(Pos.CENTER_RIGHT);
            VBox.setMargin(ts, new Insets(10, 0, 0, 0));
            body.getChildren().add(ts);
        }

        // Clip the INNER content (rim strip + body) to the rounded rectangle so the
        // colored left strip's corners follow the card's 18px rounding exactly. We
        // clip `outer` rather than the card itself so the card keeps its drop shadow
        // (a clip on the card would also crop the shadow).
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(34);   // ~2 × (radius − border)
        clip.setArcHeight(34);
        clip.widthProperty().bind(outer.widthProperty());
        clip.heightProperty().bind(outer.heightProperty());
        outer.setClip(clip);

        card.getChildren().add(outer);
        return card;
    }

    private Node buildBatchBar(boolean mastered) {
        Card bar = new Card().fill(Theme.CARD).border(Theme.ACCENT, 2).radius(12).pad(10, 14);
        VBox.setMargin(bar, new Insets(0, 2, 12, 2));
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Pill sel = new Pill("  " + selectedIds.size() + " SELECTED  ",
                Theme.ACCENT_LIGHT, Theme.ACCENT_DARK, Theme.MONO_LABEL, 6, 4, 6);

        Label all = linkLabel(I18n.t("batch.all"), Theme.ACCENT, this::selectAllVisible);
        Label sep = new Label("|");
        sep.setTextFill(Color.web(Theme.TEXT_HINT));
        Label none = linkLabel(I18n.t("batch.none"), Theme.TEXT_SUB, this::deselectAll);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox right = new HBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        if (!mastered) {
            right.getChildren().add(
                new FlatButton(I18n.t("batch.addPractice"), () -> batchUpdate("practice"))
                    .colors(Theme.BG_ALT, Theme.TEXT_MAIN).hover(Theme.ACCENT_LIGHT)
                    .shadow(Theme.SHADOW).font(BTN10).radius(8).depth(3).pad(5, 14)
                    .outlined(true, Theme.BORDER));
        }
        right.getChildren().add(
            new FlatButton(I18n.t("batch.delete"), this::batchDelete)
                .colors(Theme.TIER3_BG, Theme.TIER3)
                .hover(Theme.lighten(Theme.TIER3_BG, 0.05))
                .shadow(Theme.darken(Theme.TIER3_BG, 0.15))
                .font(BTN10).radius(8).depth(3).pad(5, 14)
                .outlined(true, Theme.TIER3));

        row.getChildren().addAll(sel, all, sep, none, grow, right);
        bar.getChildren().add(row);
        return bar;
    }

    private Label linkLabel(String text, String color, Runnable onClick) {
        Label l = new Label(text);
        l.setFont(BTN10);
        l.setTextFill(Color.web(color));
        l.setCursor(Cursor.HAND);
        l.setOnMouseClicked(e -> onClick.run());
        return l;
    }

    // ── Actions ──────────────────────────────────────────────────────
    private boolean isMasteredView() { return "all".equals(currentView); }

    void togglePractice(int wordId) {
        // Look across ALL words — the card may live in daily, unfinished, or mastered;
        // limiting to the daily pool meant un-flagging never worked on the other pages.
        Word w = Database.getAllWords().stream().filter(x -> x.id == wordId).findFirst().orElse(null);
        if (w != null && "practice".equals(w.status)) Database.updateStatus(wordId, "default");
        else Database.updateStatus(wordId, "practice");
        refreshView();
        updateStats();
    }

    void deleteWord(int wordId) {
        if (confirm(I18n.t("delete.title"), I18n.t("delete.msg"))) {
            Database.deleteWord(wordId);
            refreshView();
            updateStats();
        }
    }

    void toggleSelectMode() {
        selectMode = !selectMode;
        selectedIds.clear();
        refreshView();
    }

    void selectAllVisible() {
        selectedIds.clear();
        for (Word w : filteredWords(isMasteredView())) selectedIds.add(w.id);
        populateCards(isMasteredView());
    }

    void deselectAll() {
        selectedIds.clear();
        populateCards(isMasteredView());
    }

    void batchUpdate(String status) {
        for (int id : selectedIds) Database.updateStatus(id, status);
        selectedIds.clear();
        selectMode = false;
        refreshView();
        updateStats();
    }

    void batchDelete() {
        if (selectedIds.isEmpty()) return;
        if (confirm("Delete", "Remove " + selectedIds.size() + " cards?")) {
            for (int id : selectedIds) Database.deleteWord(id);
            selectedIds.clear();
            selectMode = false;
            refreshView();
            updateStats();
        }
    }

    /** One Export button → a small chooser dialog for the format. */
    void chooseExport() {
        if (filteredWords(isMasteredView()).isEmpty()) {
            info(I18n.t("export.title"), I18n.t("export.nothing"));
            return;
        }
        ButtonType txt = new ButtonType(I18n.t("export.txt"));
        ButtonType csv = new ButtonType(I18n.t("export.csv"));
        Alert a = new Alert(Alert.AlertType.NONE,
                I18n.t("export.choose"), txt, csv, ButtonType.CANCEL);
        a.setTitle(I18n.t("export.title"));
        a.setHeaderText(null);
        a.initOwner(stage);
        a.showAndWait().ifPresent(b -> {
            if (b == txt) export("txt");
            else if (b == csv) export("csv");
        });
    }

    void export(String fmt) {
        List<Word> words = filteredWords(isMasteredView());
        if (words.isEmpty()) { info(I18n.t("export.title"), I18n.t("export.nothing")); return; }
        String content = Database.exportWords(words, fmt);
        FileChooser fc = new FileChooser();
        String ext = "txt".equals(fmt) ? ".txt" : ".csv";
        fc.setInitialFileName("wordbook" + ext);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "txt".equals(fmt) ? "Text file" : "CSV file", "*" + ext));
        File f = fc.showSaveDialog(stage);
        if (f != null) {
            try {
                Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                info(I18n.t("export.savedTitle"), I18n.t("export.savedMsg") + f.getAbsolutePath());
            } catch (Exception e) {
                info(I18n.t("export.failed"), e.getMessage());
            }
        }
    }

    // ── Dialog helpers ───────────────────────────────────────────────
    private boolean confirm(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        a.setTitle(title);
        a.setHeaderText(null);
        a.initOwner(stage);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.initOwner(stage);
        a.showAndWait();
    }

    // ── Reading Room ─────────────────────────────────────────────────
    private void renderReadingRoom() {
        VBox col = new VBox(12);
        col.setFillWidth(true);

        FlowPane tabs = new FlowPane(6, 6);
        tabs.getChildren().add(makeArticleTab("__today__", I18n.t("today.tab")));
        tabs.getChildren().add(makeArticleTab("__custom__", "✎ " + I18n.t("reading.customWorkspace.tab")));

        Card paper = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(20).pad(28);
        paper.setFillWidth(true);
        paper.setSpacing(0);
        if (readingCustomMode) buildCustomSpace(paper);
        else buildTodaySpace(paper);   // default = today's generated article

        col.getChildren().addAll(tabs, paper);
        contentBox.getChildren().add(col);
    }

    private FlatButton makeArticleTab(String artId, String label) {
        boolean active = switch (artId) {
            case "__today__"  -> readingTodayMode;
            case "__custom__" -> readingCustomMode;
            default -> !readingCustomMode && !readingTodayMode && readingArticleId.equals(artId);
        };
        String bg = active ? Theme.ACCENT : Theme.CARD;
        String fg = active ? Theme.CARD : Theme.TEXT_SUB;
        String shadow = active ? Theme.ACCENT_DARK : Theme.SHADOW;
        FlatButton b = new FlatButton(label, () -> selectArticle(artId))
                .colors(bg, fg)
                .hover(active ? Theme.ACCENT_HOVER : Theme.ACCENT_LIGHT)
                .shadow(shadow)
                .font(Font.font(Theme.SERIF, FontWeight.BOLD, 10))
                .radius(10).depth(active ? 3 : 2).pad(5, 12);
        if (!active) b.outlined(true, Theme.BORDER);
        return b;
    }

    private void selectArticle(String artId) {
        readingTodayMode = artId.equals("__today__");
        readingCustomMode = artId.equals("__custom__");
        if (!readingTodayMode && !readingCustomMode) readingArticleId = artId;
        refreshView();
    }

    // ── Today's generated article ────────────────────────────────────
    private void buildTodaySpace(Card parent) {
        // Clicking blank space anywhere in the article page drops the word selection
        // (word double-clicks consume their event, so they don't reach here).
        parent.setOnMouseClicked(e -> clearArticleSelection());
        com.wordbook.model.Article a =
                (com.wordbook.service.DeepSeekService.hasKey() && !articleGenerating)
                        ? Database.getLatestArticleForToday() : null;

        // ── Top row: kicker + (regenerate, when an article exists)
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().add(monoLabel(I18n.t("today.kicker"), Theme.ACCENT_DARK));
        Region topGrow = new Region(); HBox.setHgrow(topGrow, Priority.ALWAYS);
        top.getChildren().add(topGrow);
        if (a != null) {
            top.getChildren().add(new FlatButton(I18n.t("today.regenerate"), this::openArticleGenDialog)
                    .colors(Theme.BG_ALT, Theme.TEXT_SUB).hover(Theme.ACCENT_LIGHT)
                    .shadow(Theme.SHADOW).font(Theme.BTN).radius(8).depth(2).pad(6, 14)
                    .outlined(true, Theme.BORDER));
        }
        VBox.setMargin(top, new Insets(0, 0, 12, 0));
        parent.getChildren().add(top);

        if (!com.wordbook.service.DeepSeekService.hasKey()) {
            parent.getChildren().add(italicNote(I18n.t("today.noKey")));
            return;
        }
        if (articleGenerating) {
            Label l = italicNote(I18n.t("today.generating"));
            l.setTextFill(Color.web(Theme.ACCENT_DARK));
            parent.getChildren().add(l);
            return;
        }
        if (a == null) {
            parent.getChildren().add(italicNote(I18n.t("today.intro")));
            int n = Database.getTodaySessionWords().size();
            FlatButton gen = new FlatButton(I18n.t("today.generate"), this::openArticleGenDialog)
                    .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                    .shadow(Theme.ACCENT_DARK).font(Theme.BTN_LG).radius(10).depth(3).pad(9, 22);
            gen.setEnabledState(n > 0);
            VBox.setMargin(gen, new Insets(8, 0, 0, 0));
            Label hint = new Label(n > 0 ? I18n.t("today.wordCount").replace("{n}", String.valueOf(n))
                                         : I18n.t("today.empty"));
            hint.setFont(Theme.SERIF_SMALL);
            hint.setTextFill(Color.web(Theme.TEXT_HINT));
            VBox.setMargin(hint, new Insets(10, 0, 0, 0));
            parent.getChildren().addAll(gen, hint);
            return;
        }

        // ── Domain title + meta
        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(a.domain == null ? "—" : a.domain);
        title.setFont(Theme.SERIF_H2);
        title.setTextFill(Color.web(Theme.TEXT_MAIN));
        Region hg = new Region(); HBox.setHgrow(hg, Priority.ALWAYS);
        Label meta = new Label(metaLabel(a));
        meta.setFont(Theme.MONO_TINY);
        meta.setTextFill(Color.web(Theme.TEXT_HINT));
        head.getChildren().addAll(title, hg, meta);
        VBox.setMargin(head, new Insets(0, 0, 12, 0));
        parent.getChildren().add(head);

        // ── 导读 (reading guide, in the translation language) + body
        String[] gb = splitGuide(a.content);
        if (!gb[0].isEmpty()) {
            Card guide = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(12);
            guide.setFillWidth(true);
            guide.getChildren().add(monoLabel(I18n.t("today.guide"), Theme.ACCENT_DARK));
            Label g = new Label(gb[0]);
            g.setFont(Font.font("Songti SC", 13));
            g.setTextFill(Color.web(Theme.TEXT_SUB));
            g.setWrapText(true);
            g.setMaxWidth(Double.MAX_VALUE);
            VBox.setMargin(g, new Insets(4, 0, 0, 0));
            guide.getChildren().add(g);
            VBox.setMargin(guide, new Insets(0, 0, 14, 0));
            parent.getChildren().add(guide);
        }
        parent.getChildren().add(articleFlow(gb[1]));

        // ── My notes
        parent.getChildren().add(buildArticleNotes(a));

        // ── Footer: Done (in the target language) / already-read badge
        HBox foot = new HBox(10);
        foot.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(foot, new Insets(16, 0, 0, 0));
        if (a.readAt == null) {
            foot.getChildren().add(new FlatButton("Done",
                    () -> { Database.markArticleRead(a.id); openAchievement(a); refreshView(); })
                    .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                    .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(7, 22));
        } else {
            Label badge = new Label("✓ " + I18n.t("today.readBadge"));
            badge.setFont(Theme.BTN);
            badge.setTextFill(Color.web(Theme.TIER0_FG));
            badge.setPadding(new Insets(7, 14, 7, 14));
            badge.setStyle("-fx-background-color: " + Theme.TIER0_BG + "; -fx-background-radius: 8;");
            badge.setCursor(Cursor.HAND);
            badge.setOnMouseClicked(e -> openAchievement(a));
            foot.getChildren().add(badge);
        }
        parent.getChildren().add(foot);
    }

    private Label italicNote(String text) {
        Label l = new Label(text);
        l.setFont(Theme.SERIF_ITALIC);
        l.setTextFill(Color.web(Theme.TEXT_SUB));
        l.setWrapText(true);
        l.setMaxWidth(620);
        return l;
    }

    /** Split the stored content into [reading-guide, body]. The guide is the first
     *  line when it starts with "导读：/导读:"; otherwise there is no guide. */
    private String[] splitGuide(String content) {
        if (content == null) return new String[]{"", ""};
        String c = content.strip();
        int nl = c.indexOf('\n');
        String first = nl >= 0 ? c.substring(0, nl).strip() : c;
        if (first.startsWith("导读：") || first.startsWith("导读:")) {
            String guide = first.replaceFirst("^导读[：:]\\s*", "").strip();
            String body = nl >= 0 ? c.substring(nl + 1).strip() : "";
            return new String[]{guide, body};
        }
        return new String[]{"", c};
    }

    /** A free-text reflection note for the article, saved when focus leaves it. */
    private VBox buildArticleNotes(com.wordbook.model.Article a) {
        VBox box = new VBox(6);
        VBox.setMargin(box, new Insets(18, 0, 0, 0));
        box.getChildren().add(monoLabel(I18n.t("today.notes"), Theme.ACCENT_DARK));
        TextArea ta = new TextArea(a.userNote == null ? "" : a.userNote);
        ta.setWrapText(true);
        ta.setPrefRowCount(3);
        ta.setFont(Font.font(Theme.SERIF, 12));
        ta.setPromptText(I18n.t("today.notesPrompt"));
        ta.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) Database.updateArticleNote(a.id, ta.getText().strip());
        });
        box.getChildren().add(ta);
        return box;
    }

    private String metaLabel(com.wordbook.model.Article a) {
        return (a.type == null ? "" : a.type.toUpperCase()) + "  ·  "
                + (a.difficulty == null ? "" : a.difficulty);
    }

    /** Render passage text (with **bold** target words) as a TextFlow. **Every** word in
     *  the passage is double-clickable: a double-click translates it and surfaces the same
     *  floating capture popup (中文释义 + 朗读) as system-wide double-click capture. Target
     *  words additionally keep their sand-gold bold highlight (a static marker, no hover). */
    /** The article word currently shown as "selected" (a simulated text-selection
     *  highlight on double-click — JavaFX TextFlow has no native selection). */
    private Label articleSelectedWord;

    private javafx.scene.text.TextFlow articleFlow(String content) {
        articleSelectedWord = null;
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        flow.setLineSpacing(7);
        flow.setMaxWidth(Double.MAX_VALUE);
        Font body = Font.font(Theme.SERIF, 15);
        Font bold = Font.font(Theme.SERIF, FontWeight.BOLD, 15);
        String[] parts = (content == null ? "" : content).split("\\*\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            appendArticleTokens(flow, parts[i], i % 2 == 1, body, bold);
        }
        return flow;
    }

    /** Split a passage segment into word / non-word tokens and add them to {@code flow}.
     *  Each English word becomes its own double-clickable node; the gaps (spaces,
     *  punctuation) stay plain. {@code target} = the highlighted bold style. */
    private void appendArticleTokens(javafx.scene.text.TextFlow flow, String segment,
                                     boolean target, Font body, Font bold) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("[A-Za-z]+(?:[-'’][A-Za-z]+)*").matcher(segment);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) flow.getChildren().add(plainArticleText(segment.substring(last, m.start()), target, body, bold));
            String word = m.group();
            // Every word is its own Label so we can paint a selection background on it.
            String base = target
                    ? "-fx-background-color: " + Theme.ACCENT_LIGHT + "; -fx-background-radius: 4;"
                    : "-fx-background-color: transparent;";
            Label w = new Label(word);
            w.setFont(target ? bold : body);
            w.setTextFill(Color.web(target ? Theme.ACCENT_TEXT : Theme.TEXT_MAIN));
            w.setPadding(target ? new Insets(0, 3, 0, 3) : Insets.EMPTY);
            w.setStyle(base);
            w.getProperties().put("baseStyle", base);
            w.getProperties().put("baseFill", target ? Theme.ACCENT_TEXT : Theme.TEXT_MAIN);
            w.setCursor(Cursor.HAND);
            w.setOnMouseClicked(e -> {
                if (e.getClickCount() != 2) return;
                selectArticleWord(w);
                captureWord(word, e.getScreenX(), e.getScreenY());
                e.consume();   // don't let it bubble to the page's clear-selection handler
            });
            flow.getChildren().add(w);
            last = m.end();
        }
        if (last < segment.length()) flow.getChildren().add(plainArticleText(segment.substring(last), target, body, bold));
    }

    /** Move the simulated selection highlight to {@code w}, restoring the previous word. */
    private void selectArticleWord(Label w) {
        if (articleSelectedWord == w) return;
        clearArticleSelection();
        // The selection ground (ACCENT sand-gold) is the SAME hex in light & dark, so the
        // text on it must be a fixed deep-warm brown to stay legible in both modes — using
        // the theme's CARD/TEXT_MAIN would flip to a light tone in one mode and wash out.
        w.setStyle("-fx-background-color: " + Theme.ACCENT + "; -fx-background-radius: 3;");
        w.setTextFill(Color.web("#3D3320"));
        articleSelectedWord = w;
    }

    /** Drop the simulated selection (e.g. user clicked blank space), restoring the word. */
    private void clearArticleSelection() {
        Label w = articleSelectedWord;
        if (w == null) return;
        w.setStyle((String) w.getProperties().get("baseStyle"));
        w.setTextFill(Color.web((String) w.getProperties().get("baseFill")));
        articleSelectedWord = null;
    }

    /** A non-interactive run of text (gap between words) for the article flow. */
    private javafx.scene.text.Text plainArticleText(String s, boolean target, Font body, Font bold) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(target ? bold : body);
        t.setFill(Color.web(target ? Theme.ACCENT_TEXT : Theme.TEXT_MAIN));
        return t;
    }

    private static final int MAX_ARTICLE_WORDS = 40;

    /** Today's words for the article: ALL words added today (mastered or not), with
     *  un-mastered first, capped so a huge day doesn't produce a giant article. */
    private List<String> todayArticleWords() {
        return Database.getTodaySessionWords().stream()
                .map(w -> w.word)
                .limit(MAX_ARTICLE_WORDS)
                .collect(Collectors.toList());
    }

    private void openArticleGenDialog() {
        List<String> words = todayArticleWords();
        if (words.isEmpty()) { info(I18n.t("today.tab"), I18n.t("today.empty")); return; }
        new com.wordbook.ui.modals.ArticleGenDialog(stage, words,
                (domain, difficulty, type) -> generateTodayArticle(words, domain, difficulty, type)).show();
    }

    private void generateTodayArticle(List<String> words, String domain, String difficulty, String type) {
        articleGenerating = true;
        readingTodayMode = true;
        refreshView();
        Thread t = new Thread(() -> {
            String content;
            try {
                content = com.wordbook.service.DeepSeekService.generateArticle(words, domain, difficulty, type);
            } catch (Exception ex) {
                content = null;
                System.err.println("[today] generate failed: " + ex.getMessage());
            }
            final String result = content;
            Platform.runLater(() -> {
                articleGenerating = false;
                if (result == null || result.isBlank()) {
                    info(I18n.t("today.tab"), I18n.t("today.error"));
                } else {
                    Database.saveArticle(java.time.LocalDate.now().toString(), domain, difficulty, type, result);
                }
                refreshView();
            });
        }, "article-gen");
        t.setDaemon(true);
        t.start();
    }

    private void openAchievement(com.wordbook.model.Article a) {
        new com.wordbook.ui.modals.AchievementCard(stage, a).show();
    }

    private void buildArticle(Card parent, Articles.Article article) {
        Pill cat = new Pill(article.category().toUpperCase(),
                Theme.POS_NOUN_BG, Theme.POS_NOUN_FG, Theme.MONO_LABEL, 8, 3, 6);
        cat.setMaxWidth(Region.USE_PREF_SIZE);

        Label title = new Label(article.title());
        title.setFont(Theme.SERIF_H2);
        title.setTextFill(Color.web(Theme.TEXT_MAIN));
        title.setWrapText(true);
        title.setMaxWidth(620);
        VBox.setMargin(title, new Insets(10, 0, 12, 0));

        Card summary = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(12);
        summary.setFillWidth(true);
        Label sumLbl = new Label("中文导读   " + article.chineseSummary());
        sumLbl.setFont(Theme.SERIF_ITALIC);
        sumLbl.setTextFill(Color.web(Theme.TEXT_SUB));
        sumLbl.setWrapText(true);
        sumLbl.setMaxWidth(600);
        summary.getChildren().add(sumLbl);
        VBox.setMargin(summary, new Insets(0, 0, 16, 0));

        parent.getChildren().addAll(cat, title, summary);
        for (String para : article.englishText().split("\n\n")) {
            String text = para.strip();
            if (text.isEmpty()) continue;
            Region surface = readingSurface(text);
            VBox.setMargin(surface, new Insets(6, 0, 6, 0));
            parent.getChildren().add(surface);
        }
    }

    private void buildCustomSpace(Card parent) {
        parent.getChildren().add(monoLabel(I18n.t("reading.customWorkspace"), Theme.ACCENT_DARK));

        Label instr = new Label(I18n.t("reading.customInstr"));
        instr.setFont(Theme.SERIF_ITALIC);
        instr.setTextFill(Color.web(Theme.TEXT_SUB));
        instr.setWrapText(true);
        instr.setMaxWidth(620);
        VBox.setMargin(instr, new Insets(4, 0, 12, 0));
        parent.getChildren().add(instr);

        TextArea input = new TextArea(readingCustomText);
        input.setWrapText(true);
        input.setPrefRowCount(10);
        input.setFont(Font.font(Theme.SERIF, 12));
        input.textProperty().addListener((o, a, b) -> readingCustomText = b);
        // The non-editable reading surface re-materialises when focus leaves the
        // editor (mirrors main.py, which rebuilt it on the next view refresh).
        input.focusedProperty().addListener((o, was, isNow) -> { if (!isNow) refreshView(); });
        parent.getChildren().add(input);

        if (!readingCustomText.strip().isEmpty()) {
            Label rsLbl = monoLabel(I18n.t("reading.surface"), Theme.ACCENT_DARK);
            VBox.setMargin(rsLbl, new Insets(16, 0, 4, 0));
            Region surface = readingSurface(readingCustomText.strip());
            VBox.setMargin(surface, new Insets(0, 0, 8, 0));
            parent.getChildren().addAll(rsLbl, surface);
        } else {
            Label hint = new Label(I18n.t("reading.customHint"));
            hint.setFont(Theme.SERIF_ITALIC);
            hint.setTextFill(Color.web(Theme.TEXT_HINT));
            VBox.setMargin(hint, new Insets(12, 0, 0, 0));
            parent.getChildren().add(hint);
        }
    }

    /** Non-editable, selectable text surface; double-click or drag-select captures. */
    private Region readingSurface(String content) {
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setFocusTraversable(false);
        Font f = Font.font(Theme.SERIF, 14);
        ta.setFont(f);
        ta.getStyleClass().add("reading-surface");

        // Size the surface to its full content height (generously, so the inner text
        // never needs to scroll — internal scrolling was what clipped/"shrank" the
        // text when the wheel moved over it).
        Text helper = new Text(content);
        helper.setFont(f);
        ta.widthProperty().addListener((o, a, b) -> {
            double w = b.doubleValue();
            if (w <= 8) return;
            helper.setWrappingWidth(w - 6);
            double h = Math.ceil(helper.getLayoutBounds().getHeight()) + 28;
            ta.setMinHeight(h);
            ta.setPrefHeight(h);
            ta.setMaxHeight(h);
        });

        // Wheel-scrolling while the pointer is over the surface should move the PAGE,
        // not scroll (and clip) the text inside this box. Forward it to the outer pane.
        ta.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (scroll != null) {
                double contentH = scroll.getContent().getBoundsInLocal().getHeight();
                double viewH = scroll.getViewportBounds().getHeight();
                if (contentH > viewH)
                    scroll.setVvalue(scroll.getVvalue() - e.getDeltaY() / (contentH - viewH));
            }
            e.consume();
        });

        ta.setOnMouseClicked(e -> { if (e.getClickCount() == 2) captureSelection(ta, e); });
        ta.setOnMouseReleased(e -> captureSelection(ta, e));
        return ta;
    }

    private void captureSelection(TextArea ta, MouseEvent e) {
        String sel = ta.getSelectedText();
        if (sel == null) return;
        sel = sel.strip();
        if (sel.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (char ch : sel.toCharArray())
            sb.append((Character.isLetter(ch) || ch == ' ' || ch == '-') ? ch : ' ');
        String cleaned = sb.toString().strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty() || cleaned.split(" ").length > 4) return;
        captureWord(cleaned, e.getScreenX(), e.getScreenY());
    }

    /** Translate on a background thread, then surface the floating popup (task 8). */
    void captureWord(String text, double screenX, double screenY) {
        new Thread(() -> {
            Translator.ProcessedWord pw = Translator.processWord(text);
            if (pw == null) return;
            Platform.runLater(() -> showCapturePopup(pw, screenX, screenY, null));
        }, "capture-worker").start();
    }

    private Node buildInstructions() {
        Card instr = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(16).pad(18);
        instr.setFillWidth(true);
        instr.getChildren().add(monoLabel(I18n.t("reading.instructions"), Theme.ACCENT_DARK));
        Region sep = sepLine();
        VBox.setMargin(sep, new Insets(8, 0, 10, 0));
        instr.getChildren().add(sep);

        String[] bullets = {
            I18n.t("reading.instr.1"),
            I18n.t("reading.instr.2"),
            I18n.t("reading.instr.3"),
            I18n.t("reading.instr.4"),
            I18n.t("reading.instr.5"),
        };
        for (String b : bullets) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.TOP_LEFT);
            Label mk = new Label("›");
            mk.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 12));
            mk.setTextFill(Color.web(Theme.ACCENT));
            Label txt = new Label(b);
            txt.setFont(Theme.SERIF_SMALL);
            txt.setTextFill(Color.web(Theme.TEXT_SUB));
            txt.setWrapText(true);
            txt.setMaxWidth(240);
            HBox.setHgrow(txt, Priority.ALWAYS);
            row.getChildren().addAll(mk, txt);
            VBox.setMargin(row, new Insets(4, 0, 4, 0));
            instr.getChildren().add(row);
        }
        return instr;
    }

    private Node buildTheory() {
        Card theory = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(14).pad(16);
        theory.setFillWidth(true);
        Label body = new Label(I18n.t("reading.theoryBody"));
        body.setFont(Theme.SERIF_ITALIC);
        body.setTextFill(Color.web(Theme.TEXT_MAIN));
        body.setWrapText(true);
        body.setMaxWidth(240);
        VBox.setMargin(body, new Insets(6, 0, 0, 0));
        theory.getChildren().addAll(monoLabel(I18n.t("reading.theory"), Theme.ACCENT_DARK), body);
        return theory;
    }

    // ── Calendar ─────────────────────────────────────────────────────
    private void renderCalendar() {
        Map<String, Integer> spellCounts = Database.getSpellCounts();
        int streak = Database.getStreak();
        int longest = Database.getLongestStreak();
        int thisMonth = Database.getMonthActivity()[0];
        int lifetime = spellCounts.size();

        // 1. Quote card
        Articles.Quote q = pickQuote(streak, thisMonth, lifetime);
        Card qcard = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(18).pad(28);
        qcard.setFillWidth(true);
        qcard.setAlignment(Pos.CENTER);
        VBox.setMargin(qcard, new Insets(0, 0, 16, 0));
        Label qk = monoLabel(I18n.t("cal.insight"), Theme.ACCENT_DARK);
        Label qt = new Label("“" + q.text() + "”");
        qt.setFont(Font.font(Theme.SERIF, FontPosture.ITALIC, 17));
        qt.setTextFill(Color.web(Theme.TEXT_MAIN));
        qt.setWrapText(true);
        qt.setMaxWidth(720);
        qt.setAlignment(Pos.CENTER);                 // center the line(s) within the label
        qt.setTextAlignment(TextAlignment.CENTER);
        VBox.setMargin(qt, new Insets(10, 0, 6, 0));
        Label qa = new Label("— " + q.author());
        qa.setFont(Theme.MONO_SMALL);
        qa.setTextFill(Color.web(Theme.TEXT_SUB));
        qa.setMaxWidth(Double.MAX_VALUE);
        qa.setAlignment(Pos.CENTER);
        qcard.getChildren().addAll(qk, qt, qa);

        // 2. Heatmap card
        Card hcard = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(18).pad(22);
        hcard.setFillWidth(true);
        VBox.setMargin(hcard, new Insets(0, 0, 16, 0));

        HBox hhdr = new HBox();
        hhdr.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(hhdr, new Insets(0, 0, 12, 0));
        VBox hleft = new VBox(2);
        Label ht = new Label(I18n.t("cal.heatmap"));
        ht.setFont(Theme.SERIF_H3);
        ht.setTextFill(Color.web(Theme.TEXT_MAIN));
        Label hs = new Label(I18n.t("cal.heatmapSub"));
        hs.setFont(Theme.SERIF_SMALL);
        hs.setTextFill(Color.web(Theme.TEXT_SUB));
        hleft.getChildren().addAll(ht, hs);
        Region hgrow = new Region();
        HBox.setHgrow(hgrow, Priority.ALWAYS);

        HBox legend = new HBox(2);
        legend.setAlignment(Pos.CENTER);
        Label less = new Label(I18n.t("cal.less"));
        less.setFont(Theme.MONO_TINY);
        less.setTextFill(Color.web(Theme.TEXT_SUB));
        HBox.setMargin(less, new Insets(0, 4, 0, 0));
        legend.getChildren().add(less);
        for (String c : Theme.HEAT) {
            Region sw = new Region();
            sw.setMinSize(12, 12);
            sw.setPrefSize(12, 12);
            sw.setMaxSize(12, 12);
            sw.setStyle("-fx-background-color: " + c + ";");
            HBox.setMargin(sw, new Insets(0, 2, 0, 2));
            legend.getChildren().add(sw);
        }
        Label more = new Label(I18n.t("cal.more"));
        more.setFont(Theme.MONO_TINY);
        more.setTextFill(Color.web(Theme.TEXT_SUB));
        HBox.setMargin(more, new Insets(0, 0, 0, 4));
        legend.getChildren().add(more);
        hhdr.getChildren().addAll(hleft, hgrow, legend);

        Canvas heat = buildHeatmap(spellCounts);
        HBox heatWrap = new HBox(heat);
        heatWrap.setAlignment(Pos.CENTER_LEFT);
        hcard.getChildren().addAll(hhdr, heatWrap);

        // 3. Stats row (4 cards)
        HBox stats = new HBox(8);
        stats.setFillHeight(true);
        Node s0 = statBox("🔥", Theme.ACCENT_LIGHT, Theme.ACCENT_DARK,
                String.valueOf(streak), I18n.t("cal.currentStreak"));
        Node s1 = statBox("🏆", Theme.POS_NOUN_BG, Theme.POS_NOUN_FG,
                String.valueOf(Math.max(streak, longest)), I18n.t("cal.longestStreak"));
        Node s2 = statBox("📅", Theme.POS_VERB_BG, Theme.POS_VERB_FG,
                thisMonth + " " + I18n.t("unit.days"), I18n.t("cal.activeMonth"));
        Node s3 = statBox("🎖", Theme.BG_ALT, Theme.TEXT_SUB,
                lifetime + " " + I18n.t("unit.days"), I18n.t("cal.lifetime"));
        for (Node n : List.of(s0, s1, s2, s3)) HBox.setHgrow(n, Priority.ALWAYS);
        stats.getChildren().addAll(s0, s1, s2, s3);

        contentBox.getChildren().addAll(qcard, hcard, stats);
    }

    private Node statBox(String glyph, String glyphBg, String glyphFg,
                         String value, String label) {
        Card box = new Card().fill(Theme.CARD).border(Theme.BORDER).radius(14).pad(14);
        box.setFillWidth(true);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox inner = new HBox(14);
        inner.setAlignment(Pos.CENTER_LEFT);

        StackPane gp = new StackPane();
        gp.setStyle("-fx-background-color: " + glyphBg + "; -fx-background-radius: 10;");
        gp.setPadding(new Insets(6, 10, 6, 10));
        Label g = new Label(glyph);
        g.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 16));
        g.setTextFill(Color.web(glyphFg));
        gp.getChildren().add(g);

        VBox txt = new VBox(0);
        Label val = new Label(value);
        val.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 22));
        val.setTextFill(Color.web(Theme.TEXT_MAIN));
        Label lbl = new Label(label);
        lbl.setFont(Theme.MONO_TINY);
        lbl.setTextFill(Color.web(Theme.TEXT_SUB));
        txt.getChildren().addAll(val, lbl);

        inner.getChildren().addAll(gp, txt);
        box.getChildren().add(inner);
        return box;
    }

    /** 30-col × 7-row heatmap ending on this week's Saturday (port of draw_heatmap). */
    private Canvas buildHeatmap(Map<String, Integer> spellCounts) {
        int cell = 12, gap = 3, cols = 30, rows = 7, offsetX = 18, offsetY = 4;
        double w = 24 + cols * (cell + gap) + 10;
        double h = rows * (cell + gap) + 10;
        Canvas canvas = new Canvas(w, h);
        GraphicsContext g = canvas.getGraphicsContext2D();

        LocalDate today = LocalDate.now();
        int dowSun = today.getDayOfWeek().getValue() % 7; // Sun=0 .. Sat=6
        LocalDate saturday = today.plusDays(6 - dowSun);
        int total = cols * rows;
        LocalDate start = saturday.minusDays(total - 1L);
        String[] labels = {"S", "M", "T", "W", "T", "F", "S"};

        g.setFont(Font.font(Theme.MONO, 9));
        g.setFill(Color.web(Theme.TEXT_SUB));
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (int r = 0; r < rows; r++) {
            g.fillText(labels[r], offsetX - 10, offsetY + r * (cell + gap) + cell / 2.0);
        }

        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                LocalDate d = start.plusDays((long) c * rows + r);
                int cnt = spellCounts.getOrDefault(d.toString(), 0);
                double x = offsetX + c * (cell + gap);
                double y = offsetY + r * (cell + gap);
                String fill = !d.isAfter(today) ? Theme.heatColor(cnt) : Theme.BG;
                g.setFill(Color.web(fill));
                g.fillRect(x, y, cell, cell);
            }
        }
        return canvas;
    }

    private Articles.Quote pickQuote(int streak, int thisMonth, int lifetime) {
        List<Articles.Quote> pool;
        if (streak >= 7 || thisMonth >= 20) pool = Articles.QUOTE_POOLS.get("excellent");
        else if (streak >= 3 || thisMonth >= 5) pool = Articles.QUOTE_POOLS.get("good");
        else pool = Articles.QUOTE_POOLS.get("encouragement");
        int idx = LocalDate.now().getDayOfYear() % pool.size();
        return pool.get(idx);
    }

    // ── small shared helpers ─────────────────────────────────────────
    private Label monoLabel(String t, String color) {
        Label l = new Label(t);
        l.setFont(Theme.MONO_LABEL);
        l.setTextFill(Color.web(color));
        return l;
    }

    private Region sepLine() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setPrefHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color: " + Theme.BORDER + ";");
        return r;
    }

    // ── Modals ───────────────────────────────────────────────────────
    void openAddDialog(String initial) {
        new com.wordbook.ui.modals.AddWordModal(stage, initial,
                (w, tr, pos, ex, exTr, note) -> {
                    int id = Database.addWord(w, tr, pos, nullIfBlank(ex), nullIfBlank(note), nullIfBlank(exTr));
                    ensureSenses(id, w);   // fetch & store meanings in the background
                    refreshView();
                    updateStats();
                }).show();
    }

    void openNoteDialog(Word word) {
        new com.wordbook.ui.modals.NoteEditor(stage, word, (id, note) -> {
            Database.updateNote(id, note);
            refreshView();
        }).show();
    }

    /** Spell Check quizzes the words you've FLAGGED (⚐→⚑) within the current page's
     *  pool — i.e. the selected words of daily / unfinished / all (whichever is open). */
    private List<Word> spellPoolForCurrentView() {
        List<Word> pool = switch (currentView) {
            case "unfinished" -> Database.getUnfinishedWords();
            case "all"        -> Database.getMasteredWords();
            default           -> Database.getTodaySessionWords();   // daily / reading / calendar
        };
        return pool.stream()
                .filter(w -> "practice".equals(w.status))
                .collect(Collectors.toList());
    }

    void openSpellCheck() {
        spellActive = true;   // pause the daily→unfinished rollover while quizzing
        var modal = new com.wordbook.ui.modals.SpellCheckModal(stage, spellPoolForCurrentView(), () -> {
            refreshView();
            updateStats();
        });
        modal.setOnHidden(e -> {
            spellActive = false;
            maybeRollover();   // if a 4 AM passed during the quiz, sweep now
            refreshView();
            updateStats();
        });
        modal.show();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Fetch a word's meanings in the background and store them (so cards / spell
     *  check can show polysemy). Marks single-meaning words with "[]" so they aren't
     *  re-fetched. */
    private void ensureSenses(int id, String wordText) {
        if (id <= 0 || wordText == null || wordText.isBlank()) return;
        Thread t = new Thread(() -> {
            String sj = Translator.serializeSenses(Translator.translateRich(wordText).senses());
            Database.updateSenses(id, sj == null ? "[]" : sj);
            Platform.runLater(this::refreshView);
        }, "senses-fetch");
        t.setDaemon(true);
        t.start();
    }

    /** One-time background backfill: fetch meanings for any existing words missing them. */
    private void backfillSenses() {
        Thread t = new Thread(() -> {
            List<Word> missing = Database.getWordsMissingSenses();
            if (missing.isEmpty()) return;
            for (Word w : missing) {
                if (w.word == null || w.word.isBlank()) { Database.updateSenses(w.id, "[]"); continue; }
                String sj = Translator.serializeSenses(Translator.translateRich(w.word).senses());
                Database.updateSenses(w.id, sj == null ? "[]" : sj);
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }
            Platform.runLater(() -> { refreshView(); updateStats(); });
        }, "senses-backfill");
        t.setDaemon(true);
        t.start();
    }

    // ── Floating capture popup ──────────────────────────────────────
    private Stage capturePopup;

    void showCapturePopup(Translator.ProcessedWord pw, double screenX, double screenY,
                          String prevApp) {
        if (pw == null) return;

        // Remember who is frontmost RIGHT NOW (e.g. Chrome). Showing any JavaFX
        // window activates our whole app and macOS then surfaces the main window,
        // so after the popup appears we hand the foreground straight back.
        long prevPid = com.wordbook.platform.MacNative.frontmostPid();
        // Set true when a click is meant to enter the app (e.g. "+ Example" opens an
        // editor) — in that case we deliberately let the app stay in front.
        final boolean[] keepForeground = {false};

        TtsService.speak(pw.word());

        if (capturePopup != null) { capturePopup.close(); capturePopup = null; }

        // Deliberately NOT calling initOwner(stage): an owned window is grouped with
        // the main window on macOS and would drag it forward together with the popup.
        Stage popup = new Stage(javafx.stage.StageStyle.TRANSPARENT);
        popup.setAlwaysOnTop(true);
        capturePopup = popup;

        final double W = 340, PAD = 14;
        String pos = (pw.pos() == null || pw.pos().isBlank()) ? "noun" : pw.pos().toLowerCase();
        String[] pc = Theme.posColors(pos);
        // Extra "multiple meanings" lines (polysemy) — grow the card to fit them.
        List<String> senseLines = Translator.senseLines(pw.senses(), 3, 4);
        final double H = 152 + (senseLines.isEmpty() ? 0 : senseLines.size() * 18 + 6);

        Card card = new Card().fill(Theme.CARD).border(Theme.ACCENT, 2).radius(12);
        card.setPrefSize(W, H);
        card.setMinSize(W, H);
        card.setMaxSize(W, H);
        card.setPadding(new Insets(16, 18, 14, 18));
        card.setSpacing(0);
        DropShadow soft = new DropShadow(BlurType.GAUSSIAN, Color.web(Theme.SHADOW_DEEP), 14, 0.2, 0, 6);
        card.setEffect(soft);

        // top row: word + close
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label wordLbl = new Label(pw.word());
        wordLbl.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 18));
        wordLbl.setTextFill(Color.web(Theme.TEXT_MAIN));
        Region tg = new Region();
        HBox.setHgrow(tg, Priority.ALWAYS);
        StackPane close = new StackPane();
        close.setMinSize(20, 20);
        close.setMaxSize(20, 20);
        close.setStyle("-fx-background-color: " + Theme.BG_ALT + "; -fx-background-radius: 10;");
        close.setCursor(Cursor.HAND);
        Label cx = new Label("✕");
        cx.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 11));
        cx.setTextFill(Color.web(Theme.TEXT_SUB));
        close.getChildren().add(cx);
        close.setOnMouseEntered(e -> {
            close.setStyle("-fx-background-color: " + Theme.TIER3_BG + "; -fx-background-radius: 10;");
            cx.setTextFill(Color.web(Theme.TIER3_FG));
        });
        close.setOnMouseExited(e -> {
            close.setStyle("-fx-background-color: " + Theme.BG_ALT + "; -fx-background-radius: 10;");
            cx.setTextFill(Color.web(Theme.TEXT_SUB));
        });
        close.setOnMouseClicked(e -> popup.close());
        top.getChildren().addAll(wordLbl, tg, close);

        // pos + translation
        HBox mid = new HBox(8);
        mid.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(mid, new Insets(8, 0, 0, 0));
        Pill posPill = new Pill("[" + pos + "]", pc[0], pc[1], Theme.MONO_LABEL, 8, 2, 4);
        Label trans = new Label(pw.translation() == null || pw.translation().isBlank()
                ? "…" : pw.translation());
        trans.setFont(Font.font(Theme.SERIF, 11));
        trans.setTextFill(Color.web(Theme.TEXT_SUB));
        trans.setWrapText(true);
        trans.setMaxWidth(W - 60);
        mid.getChildren().addAll(posPill, trans);

        // Multiple meanings, grouped by part-of-speech (e.g. "n.  春天 · 弹簧 · 泉水").
        VBox sensesBox = new VBox(2);
        if (!senseLines.isEmpty()) {
            VBox.setMargin(sensesBox, new Insets(6, 0, 0, 0));
            for (String line : senseLines) {
                Label sl = new Label(line);
                sl.setFont(Font.font(Theme.SERIF, 11));
                sl.setTextFill(Color.web(Theme.TEXT_MUTE));
                sl.setWrapText(true);
                sl.setMaxWidth(W - 36);
                sensesBox.getChildren().add(sl);
            }
        }

        Region sep = sepLine();
        VBox.setMargin(sep, new Insets(14, 0, 0, 0));

        Region growMid = new Region();
        VBox.setVgrow(growMid, Priority.ALWAYS);

        // action buttons
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        VBox.setMargin(actions, new Insets(12, 0, 0, 0));
        FlatButton addDaily = new FlatButton(I18n.t("capture.add"), () -> {
            int id = Database.addWord(pw.word(), pw.translation(), pos);
            if (id > 0) {
                String sj = Translator.serializeSenses(pw.senses());
                Database.updateSenses(id, sj == null ? "[]" : sj);
            }
            popup.close();
            refreshView();
            updateStats();
        }).colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(6, 14);
        FlatButton addEx = new FlatButton(I18n.t("capture.example"), () -> {
            keepForeground[0] = true;   // entering the app to edit — let it stay in front
            popup.close();
            openAddDialog(pw.word());
        }).colors(Theme.BG_ALT, Theme.TEXT_MAIN).hover(Theme.ACCENT_LIGHT)
                .shadow(Theme.SHADOW).font(Theme.BTN).radius(8).depth(3).pad(6, 14);
        actions.getChildren().addAll(addDaily, addEx);

        card.getChildren().addAll(top, mid, sensesBox, sep, growMid, actions);

        StackPane rootPane = new StackPane(card);
        rootPane.setStyle("-fx-background-color: transparent;");
        rootPane.setPadding(new Insets(PAD));

        double sceneW = W + 2 * PAD, sceneH = H + 2 * PAD;
        Scene scene = new Scene(rootPane, sceneW, sceneH);
        scene.setFill(Color.TRANSPARENT);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if (Theme.dark) rootPane.getStyleClass().add("dark");
        scene.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) popup.close(); });
        popup.setScene(scene);

        // Position near the capture point, clamped to the screen that contains it.
        // For the hotkey path (NaN coords) centre on the primary screen instead.
        double px, py;
        if (Double.isNaN(screenX) || Double.isNaN(screenY)) {
            javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
            px = vb.getMinX() + (vb.getWidth() - sceneW) / 2;
            py = vb.getMinY() + (vb.getHeight() - sceneH) / 2;
        } else {
            javafx.geometry.Rectangle2D vb = screenBoundsFor(screenX, screenY);
            double margin = 8;
            px = screenX - sceneW / 2;
            py = screenY + 18;
            px = Math.max(vb.getMinX() + margin, Math.min(px, vb.getMaxX() - sceneW - margin));
            py = Math.max(vb.getMinY() + margin, Math.min(py, vb.getMaxY() - sceneH - margin));
        }
        popup.setX(px);
        popup.setY(py);

        popup.setOnHidden(e -> {
            if (capturePopup == popup) capturePopup = null;
            // Closing/adding from a popup click activated us → bounce the foreground
            // back so the main window doesn't linger. Skipped for "+ Example".
            if (!keepForeground[0]) yieldForeground(prevPid);
        });
        popup.show();

        // Showing the popup just activated WordBook (surfacing the main window).
        // Hand the foreground back to whoever had it — unless that was us (e.g. the
        // word was captured from inside the Reading Room). The popup stays visible
        // because it is always-on-top.
        long ourPid = ProcessHandle.current().pid();
        if (prevPid > 0 && prevPid != ourPid) {
            com.wordbook.platform.MacNative.activatePid(prevPid);
        }

        PauseTransition autoClose = new PauseTransition(javafx.util.Duration.seconds(15));
        autoClose.setOnFinished(e -> popup.close());
        autoClose.play();
    }

    /**
     * If WE are currently the frontmost app (i.e. a popup click just activated us
     * and pulled the main window forward), hand the foreground back to {@code prevPid}
     * so the main window drops behind again. No-op if the popup auto-closed while the
     * user was elsewhere (we're not frontmost), to avoid yanking another app forward.
     */
    private void yieldForeground(long prevPid) {
        long ourPid = ProcessHandle.current().pid();
        if (prevPid > 0 && prevPid != ourPid
                && com.wordbook.platform.MacNative.frontmostPid() == ourPid) {
            com.wordbook.platform.MacNative.activatePid(prevPid);
        }
    }

    private javafx.geometry.Rectangle2D screenBoundsFor(double x, double y) {
        for (javafx.stage.Screen s : javafx.stage.Screen.getScreens()) {
            if (s.getBounds().contains(x, y)) return s.getVisualBounds();
        }
        return javafx.stage.Screen.getPrimary().getVisualBounds();
    }

    public void startHotkey() {
        com.wordbook.capture.GlobalCapture.setCallback(
                (text, x, y) -> captureWord(text, x, y));
        com.wordbook.capture.GlobalCapture.start();
    }
}
