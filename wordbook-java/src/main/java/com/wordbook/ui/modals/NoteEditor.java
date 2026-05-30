package com.wordbook.ui.modals;

import com.wordbook.model.Word;
import com.wordbook.ui.I18n;
import com.wordbook.ui.Theme;
import com.wordbook.ui.components.Card;
import com.wordbook.ui.components.FlatButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.BiConsumer;

/** Port of NoteEditor: small modal to edit a card's study note. */
public class NoteEditor extends Stage {

    public NoteEditor(Stage owner, Word word, BiConsumer<Integer, String> onSave) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.t("note.title"));
        setResizable(false);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + Theme.BG + ";");
        root.setPadding(new Insets(16));

        // Header — rounded sand band
        Card hdr = new Card().fill(Theme.ACCENT_LIGHT).radius(12).pad(14);
        hdr.setFillWidth(true);
        Label hl = new Label(I18n.t("note.header") + word.word + " [" + nz(word.pos) + "] " + nz(word.translation));
        hl.setFont(Font.font(Theme.SERIF, FontWeight.BOLD, 12));
        hl.setTextFill(Color.web(Theme.ACCENT_DARK));
        hl.setWrapText(true);
        hl.setMaxWidth(400);
        hdr.getChildren().add(hl);

        Label cap = new Label(I18n.t("note.label"));
        cap.setFont(Theme.MONO_LABEL);
        cap.setTextFill(Color.web(Theme.ACCENT_DARK));
        VBox.setMargin(cap, new Insets(16, 8, 4, 8));

        TextArea ta = new TextArea(nz(word.note));
        ta.setWrapText(true);
        ta.setPrefRowCount(5);
        ta.setFont(Font.font(Theme.SERIF, 12));
        VBox.setMargin(ta, new Insets(0, 8, 0, 8));

        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER);
        VBox.setMargin(btns, new Insets(14, 0, 0, 0));
        FlatButton cancel = new FlatButton(I18n.t("note.cancel"), this::close)
                .colors(Theme.BG_ALT, Theme.TEXT_SUB).shadow(Theme.SHADOW)
                .font(Theme.BTN).radius(8).depth(2).pad(6, 14).outlined(true, Theme.BORDER);
        FlatButton save = new FlatButton(I18n.t("note.save"), () -> {
            String note = ta.getText().strip();
            onSave.accept(word.id, note.isEmpty() ? null : note);
            close();
        }).colors(Theme.ACCENT, Theme.CARD).hover(Theme.ACCENT_HOVER)
                .shadow(Theme.ACCENT_DARK).font(Theme.BTN).radius(8).depth(3).pad(6, 18);
        btns.getChildren().addAll(cancel, save);

        root.getChildren().addAll(hdr, cap, ta, btns);

        Scene scene = new Scene(root, 460, 300);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if (Theme.dark) root.getStyleClass().add("dark");
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close(); });
        setScene(scene);
        ta.requestFocus();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
