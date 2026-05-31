package com.wordbook.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny i18n layer. UI strings are looked up by key for the current language.
 * Languages: English, 简体中文, 日本語. Missing keys fall back to English, then
 * to the key itself, so partial coverage never crashes.
 */
public final class I18n {
    private I18n() {}

    public enum Lang {
        EN("English"), ZH("简体中文"), JA("日本語");
        public final String label;
        Lang(String l) { this.label = l; }
    }

    private static Lang lang = Lang.EN;
    public static Lang lang() { return lang; }
    public static void set(Lang l) { if (l != null) lang = l; }

    /** key → { EN, ZH, JA } */
    private static final Map<String, String[]> M = new HashMap<>();
    private static void put(String k, String en, String zh, String ja) { M.put(k, new String[]{en, zh, ja}); }

    public static String t(String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        String s = v[lang.ordinal()];
        return (s == null || s.isEmpty()) ? v[0] : s;
    }

    static {
        // ── Brand / nav / headers ──────────────────────────────────
        put("brand.tagline", "A SEEING-STONE FOR LANGUAGE", "语言的真知晶石", "言語のための見晶石");

        put("nav.daily",      "Daily Pool",   "每日单词", "デイリー");
        put("nav.unfinished", "Unfinished",   "未完成",   "未完了");
        put("nav.all",        "All Words",    "全部单词", "すべての単語");
        put("nav.reading",    "Reading Room", "阅读室",   "リーディング");
        put("nav.calendar",   "Calendar",     "日历",     "カレンダー");
        put("nav.settings",   "Setting",      "设置",     "設定");

        put("hdr.daily",      "Daily Pool",                "每日单词池",   "デイリープール");
        put("hdr.unfinished", "Unfinished Words",          "未完成单词",   "未完了の単語");
        put("hdr.all",        "Mastered Archive",          "已掌握归档",   "習得済みアーカイブ");
        put("hdr.reading",    "Interactive Reading Room",  "互动阅读室",   "インタラクティブ・リーディング");
        put("hdr.calendar",   "Self-Evaluation Analytics", "自我评估分析", "自己評価アナリティクス");
        put("hdr.settings",   "Settings",                  "设置",         "設定");

        // ── Stats footer ───────────────────────────────────────────
        put("stat.streak",   "Streak",   "连续",   "連続");
        put("stat.mastered", "Mastered", "已掌握", "習得");
        put("stat.pool",     "Pool",     "词池",   "プール");
        put("unit.day",      "day",      "天",     "日");
        put("unit.days",     "days",     "天",     "日");

        put("spell.button",  "SPELL CHECK", "拼写测验", "スペルチェック");

        // ── Toolbar ────────────────────────────────────────────────
        put("toolbar.search", "Search words, translations, notes…",
                "搜索单词、翻译、笔记…", "単語・訳・メモを検索…");
        put("toolbar.export",     "Export",     "导出",     "エクスポート");
        put("toolbar.select",     "SELECT",     "选择",     "選択");
        put("toolbar.select.on",  "SELECT",     "选择",     "選択");
        put("toolbar.addword",    "ADD WORD",   "添加单词", "単語追加");

        // ── Export dialog ──────────────────────────────────────────
        put("export.title",   "Export",                              "导出",                 "エクスポート");
        put("export.choose",  "Choose a format to export this list:", "选择导出此列表的格式:", "エクスポート形式を選択:");
        put("export.txt",     "Text (.txt)",                          "文本 (.txt)",          "テキスト (.txt)");
        put("export.csv",     "CSV (.csv)",                           "CSV (.csv)",           "CSV (.csv)");
        put("export.nothing", "Nothing to export in this view.",      "当前视图没有可导出的内容。", "このビューにエクスポートする項目はありません。");
        put("export.savedTitle", "Exported",                          "已导出",               "エクスポート完了");
        put("export.savedMsg",   "Saved to ",                         "已保存到 ",            "保存先: ");
        put("export.failed",  "Export failed",                        "导出失败",             "エクスポート失敗");

        // ── Empty states ───────────────────────────────────────────
        put("empty.search",    "No words matched your search.", "没有匹配的单词。", "一致する単語がありません。");
        put("empty.mastered",  "No mastered cards yet. Spell a daily word correctly on the first try in Spell Check to graduate it here.",
                "还没有已掌握的卡片。在拼写测验中第一次就拼对,即可毕业到这里。",
                "習得済みのカードはまだありません。スペルチェックで一発正解すると、ここに卒業します。");
        put("empty.unfinished", "Nothing unfinished. Words you don't master before 4 AM the next day land here.",
                "没有未完成的单词。当天没在次日凌晨 4 点前掌握的词会出现在这里。",
                "未完了の単語はありません。翌朝4時までに習得しなかった単語がここに入ります。");
        put("empty.daily", "Your Daily pool is empty. Highlight a word anywhere (double-click) or press ⌘⇧D to begin.",
                "每日词池是空的。在任意位置双击选中一个单词,或按 ⌘⇧D 开始。",
                "デイリープールは空です。どこでも単語をダブルクリック、または ⌘⇧D で追加できます。");

        // ── Delete confirm ─────────────────────────────────────────
        put("delete.title", "Delete",                       "删除",            "削除");
        put("delete.msg",   "Remove this vocabulary card?",  "删除这张单词卡?", "この単語カードを削除しますか?");

        put("card.graduated", "Graduated at: ", "掌握于: ", "習得日: ");

        // ── Capture popup ──────────────────────────────────────────
        put("capture.add",     "✓ Add daily", "✓ 加入每日", "✓ デイリーに追加");
        put("capture.example", "+ Example",   "+ 例句",     "+ 例文");

        // ── Spell Check ────────────────────────────────────────────
        put("spell.windowTitle", "Spell Check",          "拼写测验",         "スペルチェック");
        put("spell.arena",       "✦ Spell Check Arena",  "✦ 拼写竞技场",     "✦ スペルチェック・アリーナ");
        put("spell.words",       "words",                "个",               "語");
        put("spell.hear",        "Hear word",            "听发音",           "発音を聞く");
        put("spell.skip",        "→ Skip",               "→ 跳过",           "→ スキップ");
        put("spell.check",       "Check Spelling",       "检查拼写",         "スペルを確認");
        put("spell.perfect",     "✓  Perfect! Word Mastered",
                "✓  完美!已掌握这个词", "✓  完璧!習得しました");
        put("spell.correct",     "✓  Correct! Keep practicing this word",
                "✓  正确!继续练习这个词", "✓  正解!この単語を練習し続けましょう");
        put("spell.wrong",       "✗  Not quite — here's the correct spelling. Try again.",
                "✗  不对——这是正确拼写,再试一次。", "✗  惜しい——正しいスペルはこちら。もう一度。");
        put("spell.emptyTitle",  "No Words Selected on This Page", "本页未选择单词", "このページで単語が選択されていません");
        put("spell.emptyMsg",    "Tap the ⚐ flag on the cards you want to drill, then open Spell Check — it quizzes the flagged words on the current page.",
                "在想练习的卡片上点 ⚐ 旗标,再打开拼写测验——它只考当前页面被标记的词。",
                "練習したいカードの ⚐ をタップしてからスペルチェックを開くと、現在のページで選択した単語が出題されます。");
        put("spell.close",       "Close",          "关闭",       "閉じる");
        put("spell.back",        "Back to words",  "返回单词",   "単語に戻る");

        // ── Settings ───────────────────────────────────────────────
        put("settings.appearance", "✦ APPEARANCE", "✦ 外观", "✦ 外観");
        put("settings.appearanceSub", "Choose how WordBook looks. Follow System matches your macOS Light/Dark setting.",
                "选择 WordBook 的外观。跟随系统会匹配 macOS 的浅色/深色设置。",
                "WordBook の外観を選びます。システムに従うと macOS のライト/ダークに一致します。");
        put("settings.day",       "Day",   "白天", "ライト");
        put("settings.daySub",    "Warm light parchment", "温暖的浅色羊皮纸", "暖かいライトの羊皮紙");
        put("settings.night",     "Night", "夜晚", "ダーク");
        put("settings.nightSub",  "Warm black, easy on the eyes", "温暖的黑,护眼", "目に優しい暖かなブラック");
        put("settings.system",    "Follow System", "跟随系统", "システムに従う");
        put("settings.systemSub", "Match the macOS appearance", "匹配 macOS 外观", "macOS の外観に合わせる");

        put("settings.voice",    "✦ READING VOICE", "✦ 朗读语音", "✦ 読み上げ音声");
        put("settings.voiceSub",
                "Captured words are read aloud with natural online dictionary audio, "
                        + "falling back to the offline system voice when there's no network.",
                "用自然的在线词典发音朗读捕获的单词；没有网络时自动退回离线的系统语音。",
                "取り込んだ単語を自然なオンライン辞書音声で読み上げます。ネットワークがない場合はオフラインのシステム音声に切り替わります。");
        put("voice.youdao-us", "American · online", "美音 · 在线", "アメリカ · オンライン");
        put("voice.youdao-uk", "British · online",  "英音 · 在线", "イギリス · オンライン");
        put("voice.system",    "System voice · offline", "系统语音 · 离线", "システム音声 · オフライン");
        put("voice.sub.online", "Natural dictionary pronunciation",
                "自然的词典发音", "自然な辞書発音");
        put("voice.sub.system", "Robotic, but works without internet",
                "电子音，但无需联网", "機械的だがオフラインで動作");
        put("settings.language",    "✦ LANGUAGE", "✦ 语言", "✦ 言語");
        put("settings.languageSub", "Choose the interface language.", "选择界面语言。", "インターフェース言語を選びます。");

        // ── Today's generated article ──────────────────────────────
        put("today.tab",      "✦ Today's Article", "✦ 今日文章", "✦ 今日の記事");
        put("today.kicker",   "✦ TODAY'S ARTICLE", "✦ 今日生成",  "✦ 本日の生成");
        put("reading.customWorkspace.tab", "Custom Space", "自定义", "カスタム");
        put("today.noKey", "Set a DeepSeek API key (in the database) to generate today's article.",
                "请先设置 DeepSeek API key 才能生成今日文章。",
                "今日の記事を生成するには DeepSeek API キーを設定してください。");
        put("today.generating", "Generating your article…", "正在生成文章…", "記事を生成中…");
        put("today.intro", "Turn today's words into a short passage in a field you care about, then read it to make them stick.",
                "把今天收集的单词,生成一篇你关心领域的短文,读一遍让它们扎根。",
                "今日の単語を、興味ある分野の短い文章にして、読んで定着させましょう。");
        put("today.generate", "Generate today's article", "生成今日文章", "今日の記事を生成");
        put("today.regenerate", "Regenerate", "重新生成", "再生成");
        put("today.wordCount", "{n} words from today will be woven in.",
                "今天的 {n} 个词会被织入文章。", "本日の {n} 語が織り込まれます。");
        put("today.empty", "No words collected today yet — capture some first.",
                "今天还没有收集单词——先去划词吧。", "本日まだ単語がありません——まず取得してください。");
        put("today.markRead", "I've read it", "我读完了", "読み終えた");
        put("today.readBadge", "Read", "已读", "読了");
        put("today.guide",  "GUIDE",    "导读",     "ガイド");
        put("today.notes",  "MY NOTES", "我的笔记", "メモ");
        put("today.notesPrompt", "Jot down a thought about today's reading…",
                "写点今天阅读的想法…", "今日の読書について一言…");
        put("today.error", "Generation failed. Check your network / API key and try again.",
                "生成失败,请检查网络或 API key 后重试。",
                "生成に失敗しました。ネットワークや API キーを確認してください。");

        // ── Article generation dialog ──────────────────────────────
        put("gen.title",     "Generate Article", "生成文章", "記事を生成");
        put("gen.analyzing", "Analyzing today's words…", "正在分析今天的单词…", "本日の単語を分析中…");
        put("gen.direction", "DIRECTION", "方向", "方向");
        put("gen.directionSub", "Pick a field, or type your own.", "选一个领域,或自定义。", "分野を選ぶか、自分で入力。");
        put("gen.custom",    "Custom direction…", "自定义方向…", "カスタム方向…");
        put("gen.difficulty","DIFFICULTY", "难度", "難易度");
        put("gen.diff.easy",     "Simple",   "简单", "やさしい");
        put("gen.diff.medium",   "Medium",   "中等", "ふつう");
        put("gen.diff.advanced", "Advanced", "进阶", "むずかしい");
        put("gen.type",      "ARTICLE TYPE", "文章类型", "記事タイプ");
        put("gen.type.blog",      "Blog post",  "博客",     "ブログ");
        put("gen.type.story",     "Short story","短篇故事", "短編");
        put("gen.type.news",      "News report","新闻报道", "ニュース");
        put("gen.type.dialogue",  "Dialogue",   "对话",     "対話");
        put("gen.type.explainer", "Explainer",  "说明文",   "解説");
        put("gen.outlang",   "OUTPUT LANGUAGE", "输出语言", "出力言語");
        put("gen.generate",  "Generate", "生成", "生成");
        put("gen.cancel",    "Cancel", "取消", "キャンセル");

        // ── Achievement card ───────────────────────────────────────
        put("ach.completed", "ARTICLE COMPLETED", "今日文章已读完", "記事を読了");
        put("ach.thisWeek",  "THIS WEEK", "本周", "今週");

        // ── Menu-bar (background) tray ─────────────────────────────
        put("tray.open", "Open WordBook", "打开 WordBook", "WordBook を開く");
        put("tray.quit", "Quit WordBook", "退出 WordBook", "WordBook を終了");

        // ── First-run language chooser ─────────────────────────────
        put("lang.chooseTitle", "Choose your language", "选择语言", "言語を選択");
        put("lang.chooseSub",   "You can change this later in Settings.",
                "之后可在「设置」中更改。", "後で「設定」から変更できます。");

        // ── Spell progress / celebration ───────────────────────────
        put("spell.progress", "PROGRESS", "进度", "進捗");
        put("celeb.perfect.title", "Perfect!", "完美!", "完璧!");
        put("celeb.perfect.sub",   "Excellent spelling execution.", "拼写表现出色。", "素晴らしいスペルでした。");
        put("celeb.well.title",    "Well done!", "做得好!", "よくできました!");
        put("celeb.well.sub",      "Solid effort and retention.", "扎实的努力与记忆。", "しっかりした努力と定着。");
        put("celeb.good.title",    "Good effort!", "继续加油!", "その調子!");
        put("celeb.good.sub",      "Keep practicing to reinforce memory.", "继续练习以巩固记忆。", "練習を続けて記憶を強化しましょう。");
        put("celeb.accuracy",    "ACCURACY",     "正确数", "正解数");
        put("celeb.successRate", "SUCCESS RATE", "成功率", "成功率");
        put("celeb.mastered",    "MASTERED",     "已掌握", "習得");
        put("celeb.logged",     "✦  CALENDAR METRICS LOGGED", "✦  日历数据已记录", "✦  カレンダーに記録しました");
        put("celeb.loggedBody", "Spellings reported. Your streak and heatmap have been dynamically updated in the notebook.",
                "拼写已记录。你的连续天数和热力图已在笔记本中更新。",
                "スペル結果を記録しました。連続日数とヒートマップが更新されました。");

        // ── Calendar ───────────────────────────────────────────────
        put("cal.insight",     "✦ DAILY INSIGHT", "✦ 每日箴言", "✦ デイリー・インサイト");
        put("cal.heatmap",     "Spell Heatmap", "拼写热力图", "スペル・ヒートマップ");
        put("cal.heatmapSub",  "Your daily spelling victories over the past 30 weeks.",
                "过去 30 周的每日拼写战绩。", "過去30週間の毎日のスペル成果。");
        put("cal.less", "Less", "少", "少");
        put("cal.more", "More", "多", "多");
        put("cal.currentStreak", "CURRENT STREAK",    "当前连续", "現在の連続");
        put("cal.longestStreak", "LONGEST STREAK",    "最长连续", "最長連続");
        put("cal.activeMonth",   "ACTIVE THIS MONTH", "本月活跃", "今月のアクティブ");
        put("cal.lifetime",      "LIFETIME ACTIVE",   "累计活跃", "累計アクティブ");

        // ── Batch bar ──────────────────────────────────────────────
        put("batch.all",  "All",  "全选", "すべて");
        put("batch.none", "None", "全不选", "なし");
        put("batch.addPractice", "ADD TO PRACTICE", "加入拼写", "練習に追加");
        put("batch.delete",      "DELETE",          "删除",     "削除");

        // ── Reading room ───────────────────────────────────────────
        put("reading.instructions", "✦ READING INSTRUCTIONS", "✦ 阅读说明", "✦ 読み方ガイド");
        put("reading.instr.1", "Highlight capture is enabled inside the paper grid. Double-click any word to invoke the translator popup.",
                "正文区域内可取词。双击任意单词即可弹出翻译卡片。",
                "本文エリアで取得できます。任意の単語をダブルクリックすると翻訳ポップアップが出ます。");
        put("reading.instr.2", "You can also drag-select phrases of up to 4 words. The translator captures context automatically.",
                "也可拖选最多 4 个词的短语,翻译会自动捕捉上下文。",
                "最大4語までドラッグ選択も可能。文脈も自動で取得します。");
        put("reading.instr.3", "When the card appears, TTS speaks the word in English automatically.",
                "卡片出现时会自动用英语朗读该词。",
                "カードが出ると、英語で自動的に読み上げます。");
        put("reading.instr.4", "Clicking + Example routes the selected word into the rich example-sentence dialog.",
                "点击「+ 例句」会把所选词送入例句编辑窗口。",
                "「+ 例文」を押すと、選択語が例文エディタに送られます。");
        put("reading.instr.5", "The global hotkey ⌘⇧D still works from any other app — Lexantir captures wherever you read.",
                "全局快捷键 ⌘⇧D 在其他应用中同样有效——随处取词。",
                "グローバルショートカット ⌘⇧D は他のアプリでも有効——どこでも取得できます。");
        put("reading.theory", "❖ CLIL LEARNING THEORY", "❖ CLIL 学习理论", "❖ CLIL 学習理論");
        put("reading.theoryBody", "“Content and Language Integrated Learning ensures you acquire vocabulary contextually while studying topics of interest, multiplying retention rates.”",
                "「内容与语言整合学习(CLIL)让你在学习感兴趣的主题时,在语境中习得词汇,从而成倍提升记忆留存。」",
                "「内容言語統合型学習(CLIL)は、興味あるテーマを学びながら文脈で語彙を習得させ、定着率を倍増させます。」");
        put("reading.customWorkspace", "YOUR CUSTOM WORKSPACE", "自定义工作区", "カスタムワークスペース");
        put("reading.customInstr", "Paste any English text below. Double-click any word to translate and capture it.",
                "在下方粘贴任意英文文本。双击任意单词即可翻译并收录。",
                "下に任意の英文を貼り付け。単語をダブルクリックで翻訳・収録できます。");
        put("reading.surface", "READING SURFACE", "阅读区", "リーディング面");
        put("reading.customHint", "(Type or paste English text above to begin highlighting.)",
                "(在上方输入或粘贴英文文本即可开始取词。)",
                "(上に英文を入力または貼り付けると取得を開始できます。)");

        // ── Add Word modal ─────────────────────────────────────────
        put("addword.title",   "New Vocabulary Card",   "新建单词卡",   "新しい単語カード");
        put("addword.header",  "✦  New Vocabulary Card", "✦  新建单词卡", "✦  新しい単語カード");
        put("addword.word",    "ENGLISH WORD",  "英文单词", "英単語");
        put("addword.wordPrompt", "e.g. ubiquitous", "例:ubiquitous", "例:ubiquitous");
        put("addword.pos",     "POS", "词性", "品詞");
        put("addword.translation", "TRANSLATION", "翻译", "翻訳");
        put("addword.transPrompt", "e.g. the meaning", "例:无处不在的", "例:遍在する");
        put("addword.example", "EXAMPLE SENTENCE", "例句", "例文");
        put("addword.exTrans", "EXAMPLE TRANSLATION", "例句翻译", "例文の訳");
        put("addword.exTransPrompt", "e.g. a translated example sentence", "例:智能手机在现代社会中无处不在。", "例:スマホは現代社会に遍在している。");
        put("addword.note",    "MNEMONIC OR STUDY NOTES", "助记或学习笔记", "覚え方・メモ");
        put("addword.notePrompt", "e.g. Root 'ubique' means everywhere.", "例:词根 'ubique' 意为「到处」。", "例:語根 'ubique' は「どこでも」の意。");
        put("addword.cancel",  "Cancel", "取消", "キャンセル");
        put("addword.add",     "✓  Add Card", "✓  添加卡片", "✓  カードを追加");
        put("addword.translating", "translating…", "翻译中…", "翻訳中…");
        put("addword.errTitle", "Add Word", "添加单词", "単語を追加");
        put("addword.errMsg",   "Both word and translation are required.", "单词和翻译都必须填写。", "単語と訳の両方が必要です。");

        // ── Note editor ────────────────────────────────────────────
        put("note.title",  "Note", "笔记", "メモ");
        put("note.header", "✎  Note · ", "✎  笔记 · ", "✎  メモ · ");
        put("note.label",  "STUDY NOTE", "学习笔记", "スタディノート");
        put("note.cancel", "Cancel", "取消", "キャンセル");
        put("note.save",   "✓  Save", "✓  保存", "✓  保存");
    }
}
