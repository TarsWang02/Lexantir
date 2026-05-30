package com.wordbook.ui.modals;

import com.wordbook.service.Translator;
import com.wordbook.ui.I18n;
import com.wordbook.ui.Theme;
import com.wordbook.ui.components.Card;
import com.wordbook.ui.components.FlatButton;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Port of AddWordModal: new vocabulary card with debounced auto-translation. */
public class AddWordModal extends Stage {

    /** key, label */
    private static final String[][] POS_OPTIONS = {
        {"noun", "noun"}, {"verb", "verb"},
        {"adj", "adj"}, {"adv", "adv"}
    };

    public interface OnAdd {
        void add(String word, String translation, String pos,
                 String example, String exampleTranslation, String note);
    }

    private final TextField wordField = new TextField();
    private final ComboBox<String> posMenu = new ComboBox<>();
    private final TextField transField = new TextField();
    private final Label transStatus = new Label("");
    private final TextArea exampleArea = new TextArea();
    private final TextField exTransField = new TextField();
    private final TextField noteField = new TextField();

    private final OnAdd onAdd;
    private boolean hasUserEdited = false;
    private boolean programmaticSet = false;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(600));

    public AddWordModal(Stage owner, String initialWord, OnAdd onAdd) {
        this.onAdd = onAdd;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.t("addword.title"));
        setResizable(false);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + Theme.BG + ";");
        root.setPadding(new Insets(16, 16, 0, 16));

        // Header
        Card hdr = new Card().fill(Theme.BG_ALT).border(Theme.BORDER).radius(12).pad(14);
        hdr.setFillWidth(true);
        Label hl = new Label(I18n.t("addword.header"));
        hl.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 14));
        hl.setTextFill(Color.web(Theme.TEXT_MAIN));
        hdr.getChildren().add(hl);

        VBox body = new VBox();
        body.setPadding(new Insets(18, 22, 18, 22));

        // English word
        body.getChildren().add(monoLabel(I18n.t("addword.word"), 0));
        wordField.setPromptText(I18n.t("addword.wordPrompt"));
        wordField.setFont(Font.font(Theme.SERIF, 12));
        body.getChildren().add(wordField);
        wordField.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        debounce.setOnFinished(e -> doTranslate());

        // POS + Translation row
        HBox row = new HBox(14);
        VBox.setMargin(row, new Insets(12, 0, 0, 0));

        VBox posCol = new VBox(4);
        posCol.getChildren().add(monoLabel(I18n.t("addword.pos"), 0));
        for (String[] p : POS_OPTIONS) posMenu.getItems().add(p[1]);
        posMenu.getSelectionModel().select(0);
        posMenu.setPrefWidth(130);
        posMenu.setStyle("-fx-font-family: '" + Theme.SERIF + "'; -fx-font-size: 11;");
        posCol.getChildren().add(posMenu);

        VBox transCol = new VBox(2);
        HBox.setHgrow(transCol, Priority.ALWAYS);
        HBox thd = new HBox(8);
        thd.setAlignment(Pos.CENTER_LEFT);
        Label tlbl = new Label(I18n.t("addword.translation"));
        tlbl.setFont(Theme.MONO_LABEL);
        tlbl.setTextFill(Color.web(Theme.TEXT_SUB));
        transStatus.setFont(Font.font(Theme.SERIF, FontPosture.ITALIC, 9));
        transStatus.setTextFill(Color.web(Theme.ACCENT));
        thd.getChildren().addAll(tlbl, transStatus);
        transField.setPromptText(I18n.t("addword.transPrompt"));
        transField.setFont(Font.font(Theme.SERIF, 12));
        VBox.setMargin(transField, new Insets(2, 0, 0, 0));
        transField.textProperty().addListener((o, a, b) -> { if (!programmaticSet) hasUserEdited = true; });
        transCol.getChildren().addAll(thd, transField);

        row.getChildren().addAll(posCol, transCol);
        body.getChildren().add(row);

        // Example
        body.getChildren().add(monoLabel(I18n.t("addword.example"), 12));
        exampleArea.setWrapText(true);
        exampleArea.setPrefRowCount(3);
        exampleArea.setFont(Font.font(Theme.SERIF, 11));
        body.getChildren().add(exampleArea);

        body.getChildren().add(monoLabel(I18n.t("addword.exTrans"), 10));
        exTransField.setPromptText(I18n.t("addword.exTransPrompt"));
        exTransField.setFont(Font.font(Theme.SERIF, 12));
        body.getChildren().add(exTransField);

        body.getChildren().add(monoLabel(I18n.t("addword.note"), 10));
        noteField.setPromptText(I18n.t("addword.notePrompt"));
        noteField.setFont(Font.font(Theme.SERIF, 12));
        body.getChildren().add(noteField);

        // Buttons
        HBox btnRow = new HBox();
        VBox.setMargin(btnRow, new Insets(16, 0, 0, 0));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox right = new HBox(8);
        FlatButton cancel = new FlatButton(I18n.t("addword.cancel"), this::close)
                .colors(Theme.BG_ALT, Theme.TEXT_SUB).shadow(Theme.SHADOW)
                .font(Theme.BTN).radius(8).depth(2).pad(6, 14).outlined(true, Theme.BORDER);
        FlatButton add = new FlatButton(I18n.t("addword.add"), this::submit)
                .colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(6, 18);
        right.getChildren().addAll(cancel, add);
        btnRow.getChildren().addAll(grow, right);
        body.getChildren().add(btnRow);

        root.getChildren().addAll(hdr, body);

        Scene scene = new Scene(root, 500, 540);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if (Theme.dark) root.getStyleClass().add("dark");
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close(); });
        setScene(scene);

        if (initialWord != null && !initialWord.isBlank()) {
            wordField.setText(initialWord);
            doTranslate();
        }
        Platform.runLater(wordField::requestFocus);
    }

    private Label monoLabel(String text, int padTop) {
        Label l = new Label(text);
        l.setFont(Theme.MONO_LABEL);
        l.setTextFill(Color.web(Theme.TEXT_SUB));
        VBox.setMargin(l, new Insets(padTop, 0, 4, 0));
        return l;
    }

    private void doTranslate() {
        String word = wordField.getText().strip();
        if (word.isEmpty() || word.chars().anyMatch(Character::isDigit)) return;
        transStatus.setText(I18n.t("addword.translating"));
        new Thread(() -> {
            String tr = Translator.translateWord(word);
            String pos = Translator.detectPos(word);
            Platform.runLater(() -> {
                if (!isShowing()) return;
                if (!hasUserEdited && tr != null && !tr.isBlank()) {
                    programmaticSet = true;
                    transField.setText(tr);
                    programmaticSet = false;
                }
                for (int i = 0; i < POS_OPTIONS.length; i++) {
                    if (POS_OPTIONS[i][0].equals(pos)) { posMenu.getSelectionModel().select(i); break; }
                }
                transStatus.setText("");
            });
        }, "addword-translate").start();
    }

    private void submit() {
        String word = wordField.getText().strip();
        String translation = transField.getText().strip();
        if (word.isEmpty() || translation.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.ERROR,
                    I18n.t("addword.errMsg"), ButtonType.OK);
            a.setTitle(I18n.t("addword.errTitle"));
            a.setHeaderText(null);
            a.initOwner(this);
            a.showAndWait();
            return;
        }
        int sel = Math.max(0, posMenu.getSelectionModel().getSelectedIndex());
        String posKey = POS_OPTIONS[sel][0];
        String example = exampleArea.getText().strip();
        String exTrans = exTransField.getText().strip();
        String note = noteField.getText().strip();
        onAdd.add(word, translation, posKey, example, exTrans, note);
        close();
    }
}
