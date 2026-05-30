package com.wordbook.db;

import com.wordbook.model.Word;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * SQLite layer — faithful port of db.py.
 * Uses the SAME on-disk database as the Python app so existing words carry over.
 */
public final class Database {

    private static final String DB_PATH = resolveDbPath();
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    private static String resolveDbPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path dir;
        if (os.contains("mac")) {
            dir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "WordBook");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            dir = Paths.get(appData == null ? System.getProperty("user.home") : appData, "WordBook");
        } else {
            dir = Paths.get(System.getProperty("user.home"), ".local", "share", "WordBook");
        }
        try { Files.createDirectories(dir); } catch (IOException ignored) {}

        Path db = dir.resolve("wordbook.db");
        // One-time migration from the old Python project, if present.
        if (!Files.exists(db)) {
            Path legacy = Paths.get(System.getProperty("user.home"), "wordbook", "wordbook.db");
            if (Files.exists(legacy)) {
                try { Files.copy(legacy, db, StandardCopyOption.COPY_ATTRIBUTES); } catch (IOException ignored) {}
            }
        }
        return db.toString();
    }

    private static Connection conn() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static boolean columnExists(Connection c, String table, String col) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (col.equals(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    public static void initDb() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS words (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    word         TEXT NOT NULL,
                    translation  TEXT,
                    pos          TEXT,
                    example      TEXT,
                    note         TEXT,
                    status       TEXT DEFAULT 'default',
                    mastered     INTEGER DEFAULT 0,
                    wrong_count  INTEGER DEFAULT 0,
                    mastered_at  TEXT,
                    date_added   TEXT DEFAULT (date('now', 'localtime')),
                    created_at   TEXT DEFAULT (datetime('now', 'localtime'))
                )
            """);
            String[][] cols = {
                {"example",             "ALTER TABLE words ADD COLUMN example TEXT"},
                {"example_translation", "ALTER TABLE words ADD COLUMN example_translation TEXT"},
                {"note",                "ALTER TABLE words ADD COLUMN note TEXT"},
                {"mastered",            "ALTER TABLE words ADD COLUMN mastered INTEGER DEFAULT 0"},
                {"wrong_count",         "ALTER TABLE words ADD COLUMN wrong_count INTEGER DEFAULT 0"},
                {"mastered_at",         "ALTER TABLE words ADD COLUMN mastered_at TEXT"},
                {"unfinished",          "ALTER TABLE words ADD COLUMN unfinished INTEGER DEFAULT 0"},
                {"senses",              "ALTER TABLE words ADD COLUMN senses TEXT"},
            };
            for (String[] col : cols) {
                if (!columnExists(c, "words", col[0])) s.execute(col[1]);
            }
            s.execute("""
                CREATE TABLE IF NOT EXISTS spell_log (
                    date  TEXT PRIMARY KEY,
                    count INTEGER DEFAULT 1
                )
            """);
            if (!columnExists(c, "spell_log", "count")) {
                s.execute("ALTER TABLE spell_log ADD COLUMN count INTEGER DEFAULT 1");
            }
            s.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");
            s.execute("""
                CREATE TABLE IF NOT EXISTS articles (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    date        TEXT,
                    domain      TEXT,
                    difficulty  TEXT,
                    type        TEXT,
                    content     TEXT,
                    created_at  TEXT DEFAULT (datetime('now', 'localtime')),
                    read_at     TEXT,
                    user_note   TEXT
                )
            """);
            if (!columnExists(c, "articles", "user_note")) {
                s.execute("ALTER TABLE articles ADD COLUMN user_note TEXT");
            }
        } catch (SQLException e) {
            throw new RuntimeException("initDb failed", e);
        }
    }

    // ── Generated articles (daily reading library) ──────────────────
    public static int saveArticle(String date, String domain, String difficulty,
                                  String type, String content) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO articles (date, domain, difficulty, type, content) VALUES (?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, date);
            ps.setString(2, domain);
            ps.setString(3, difficulty);
            ps.setString(4, type);
            ps.setString(5, content);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("[db] saveArticle failed: " + e.getMessage());
            return -1;
        }
    }

    public static com.wordbook.model.Article getLatestArticleForToday() {
        String today = java.time.LocalDate.now().toString();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM articles WHERE date = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapArticle(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public static void markArticleRead(int id) {
        exec("UPDATE articles SET read_at = datetime('now','localtime') WHERE id = ? AND read_at IS NULL",
                ps -> ps.setInt(1, id));
    }

    public static void updateArticleNote(int id, String note) {
        exec("UPDATE articles SET user_note = ? WHERE id = ?",
                ps -> { ps.setString(1, note); ps.setInt(2, id); });
    }

    private static com.wordbook.model.Article mapArticle(ResultSet rs) throws SQLException {
        com.wordbook.model.Article a = new com.wordbook.model.Article();
        a.id = rs.getInt("id");
        a.date = rs.getString("date");
        a.domain = rs.getString("domain");
        a.difficulty = rs.getString("difficulty");
        a.type = rs.getString("type");
        a.content = rs.getString("content");
        a.createdAt = rs.getString("created_at");
        a.readAt = rs.getString("read_at");
        a.userNote = hasCol(rs, "user_note") ? rs.getString("user_note") : null;
        return a;
    }

    /** Small key/value store for app settings (e.g. the theme choice). */
    public static String getMeta(String key, String def) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("value") != null ? rs.getString("value") : def;
            }
        } catch (SQLException e) {
            return def;
        }
    }

    public static void setMeta(String key, String value) {
        exec("INSERT INTO meta (key, value) VALUES (?, ?) " +
             "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
             ps -> { ps.setString(1, key); ps.setString(2, value); });
    }

    private static Word map(ResultSet rs) throws SQLException {
        Word w = new Word();
        w.id = rs.getInt("id");
        w.word = rs.getString("word");
        w.translation = rs.getString("translation");
        w.pos = rs.getString("pos");
        w.example = rs.getString("example");
        w.exampleTranslation = hasCol(rs, "example_translation") ? rs.getString("example_translation") : null;
        w.note = rs.getString("note");
        w.status = rs.getString("status");
        w.mastered = rs.getInt("mastered");
        w.unfinished = hasCol(rs, "unfinished") ? rs.getInt("unfinished") : 0;
        w.wrongCount = rs.getInt("wrong_count");
        w.masteredAt = rs.getString("mastered_at");
        w.dateAdded = rs.getString("date_added");
        w.createdAt = rs.getString("created_at");
        w.senses = hasCol(rs, "senses") ? rs.getString("senses") : null;
        return w;
    }

    private static boolean hasCol(ResultSet rs, String name) {
        try {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (name.equalsIgnoreCase(md.getColumnLabel(i))) return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    /** Idempotent insert for un-mastered words (matches add_word). Returns row id or -1. */
    public static int addWord(String word, String translation, String pos,
                              String example, String note, String exampleTranslation) {
        word = word == null ? "" : word.strip();
        if (word.isEmpty()) return -1;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM words WHERE LOWER(word) = LOWER(?) AND mastered = 0")) {
                ps.setString(1, word);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        if (example != null || note != null || exampleTranslation != null) {
                            try (PreparedStatement up = c.prepareStatement("""
                                    UPDATE words
                                    SET example             = COALESCE(?, example),
                                        example_translation = COALESCE(?, example_translation),
                                        note                = COALESCE(?, note)
                                    WHERE id = ?
                                """)) {
                                up.setString(1, example);
                                up.setString(2, exampleTranslation);
                                up.setString(3, note);
                                up.setInt(4, id);
                                up.executeUpdate();
                            }
                        }
                        return id;
                    }
                }
            }
            try (PreparedStatement ins = c.prepareStatement("""
                    INSERT INTO words (word, translation, pos, example, example_translation, note)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, word);
                ins.setString(2, translation);
                ins.setString(3, pos);
                ins.setString(4, example);
                ins.setString(5, exampleTranslation);
                ins.setString(6, note);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        } catch (SQLException e) {
            System.err.println("[db] addWord failed: " + e.getMessage());
            return -1;
        }
    }

    public static int addWord(String word, String translation, String pos) {
        return addWord(word, translation, pos, null, null, null);
    }

    public static void updateExample(int wordId, String example, String exampleTranslation) {
        exec("UPDATE words SET example = ?, example_translation = ? WHERE id = ?",
                ps -> { ps.setString(1, example); ps.setString(2, exampleTranslation); ps.setInt(3, wordId); });
    }

    public static void updateNote(int wordId, String note) {
        exec("UPDATE words SET note = ? WHERE id = ?",
                ps -> { ps.setString(1, note); ps.setInt(2, wordId); });
    }

    public static void updateSenses(int wordId, String sensesJson) {
        exec("UPDATE words SET senses = ? WHERE id = ?",
                ps -> { ps.setString(1, sensesJson); ps.setInt(2, wordId); });
    }

    /** Words that have no stored meanings yet (for one-time background backfill). */
    public static List<Word> getWordsMissingSenses() {
        return query("SELECT * FROM words WHERE senses IS NULL OR senses = ''");
    }

    public static List<Word> getDailyWords() {
        return query("SELECT * FROM words WHERE mastered = 0 AND unfinished = 0 ORDER BY created_at DESC");
    }

    /** Words rolled over from a previous daily session (un-mastered, past the 4 AM cutoff). */
    public static List<Word> getUnfinishedWords() {
        return query("SELECT * FROM words WHERE mastered = 0 AND unfinished = 1 ORDER BY created_at DESC");
    }

    /**
     * Move un-mastered words belonging to a PREVIOUS daily session into the
     * "unfinished" list. A session runs 4:00 AM → next 4:00 AM, so any un-mastered
     * word created before the most recent 4 AM boundary rolls over. Returns the
     * number of words moved (0 if nothing changed). Callers must skip this while a
     * spell-check is in progress and run it once the quiz ends.
     */
    /** UTC timestamp of the most recent 4 AM (local) session boundary. created_at is
     *  stored in UTC, so we compare against a UTC cutoff. */
    public static String sessionCutoffUtc() {
        java.time.ZonedDateTime nowLocal = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime fourAm = nowLocal.toLocalDate().atTime(4, 0).atZone(nowLocal.getZone());
        java.time.ZonedDateTime sessionStart = nowLocal.isBefore(fourAm) ? fourAm.minusDays(1) : fourAm;
        return sessionStart.withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Every word added during the CURRENT daily session — including ones already
     *  mastered today — with un-mastered words first and mastered ones last. */
    public static List<Word> getTodaySessionWords() {
        List<Word> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM words WHERE created_at IS NOT NULL AND created_at >= ? " +
                "ORDER BY mastered ASC, created_at DESC")) {
            ps.setString(1, sessionCutoffUtc());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("[db] getTodaySessionWords failed: " + e.getMessage());
        }
        return out;
    }

    public static int rolloverDailyPool() {
        String cutoff = sessionCutoffUtc();
        int changed = 0;
        try (Connection c = conn()) {
            // Roll forward: un-mastered words from a previous session → Unfinished.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE words SET unfinished = 1 WHERE mastered = 0 AND unfinished = 0 " +
                    "AND (created_at IS NULL OR created_at < ?)")) {
                ps.setString(1, cutoff);
                changed += ps.executeUpdate();
            }
            // Self-heal: anything wrongly flagged that actually belongs to the CURRENT
            // session (created at/after the cutoff) goes back to the Daily Pool.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE words SET unfinished = 0 WHERE mastered = 0 AND unfinished = 1 " +
                    "AND created_at IS NOT NULL AND created_at >= ?")) {
                ps.setString(1, cutoff);
                changed += ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[db] rolloverDailyPool failed: " + e.getMessage());
        }
        return changed;
    }

    public static List<Word> getMasteredWords() {
        return query("SELECT * FROM words WHERE mastered = 1 ORDER BY mastered_at DESC, created_at DESC");
    }

    public static List<Word> getAllWords() {
        return query("SELECT * FROM words ORDER BY created_at DESC");
    }

    public static List<Word> getPracticeWords() {
        return query("SELECT * FROM words WHERE status = 'practice' AND mastered = 0 ORDER BY created_at DESC");
    }

    public static void updateStatus(int wordId, String status) {
        exec("UPDATE words SET status = ? WHERE id = ?",
                ps -> { ps.setString(1, status); ps.setInt(2, wordId); });
    }

    public static void deleteWord(int wordId) {
        exec("DELETE FROM words WHERE id = ?", ps -> ps.setInt(1, wordId));
    }

    public static void incrementWrong(int wordId) {
        exec("UPDATE words SET wrong_count = wrong_count + 1 WHERE id = ?", ps -> ps.setInt(1, wordId));
    }

    public static void markMastered(int wordId) {
        // AND mastered = 0 → re-quizzing an already-mastered word (e.g. reviewing from
        // the All Words page) is a no-op and won't reset its original graduation date.
        exec("""
            UPDATE words
            SET mastered = 1, mastered_at = datetime('now', 'localtime'), status = 'default'
            WHERE id = ? AND mastered = 0
        """, ps -> ps.setInt(1, wordId));
    }

    public static void recordSpellCorrect(int n) {
        exec("""
            INSERT INTO spell_log (date, count)
            VALUES (date('now', 'localtime'), ?)
            ON CONFLICT(date) DO UPDATE SET count = count + excluded.count
        """, ps -> ps.setInt(1, n));
    }

    public static Map<String, Integer> getSpellCounts() {
        Map<String, Integer> out = new HashMap<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT date, count FROM spell_log")) {
            while (rs.next()) out.put(rs.getString("date"), rs.getInt("count"));
        } catch (SQLException e) {
            System.err.println("[db] getSpellCounts failed: " + e.getMessage());
        }
        return out;
    }

    public static int getStreak() {
        Map<String, Integer> counts = getSpellCounts();
        if (counts.isEmpty()) return 0;
        int streak = 0;
        LocalDate d = LocalDate.now();
        while (counts.containsKey(d.toString())) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    public static int getLongestStreak() {
        Map<String, Integer> counts = getSpellCounts();
        if (counts.isEmpty()) return 0;
        List<String> dates = new ArrayList<>(counts.keySet());
        Collections.sort(dates);
        int longest = 1, current = 1;
        for (int i = 1; i < dates.size(); i++) {
            LocalDate prev = LocalDate.parse(dates.get(i - 1));
            LocalDate curr = LocalDate.parse(dates.get(i));
            if (prev.plusDays(1).equals(curr)) current++;
            else { longest = Math.max(longest, current); current = 1; }
        }
        return Math.max(longest, current);
    }

    /** Returns [thisMonthActiveDays, lastMonthActiveDays]. */
    public static int[] getMonthActivity() {
        LocalDate today = LocalDate.now();
        int lastMonth, lastYear;
        if (today.getMonthValue() == 1) { lastMonth = 12; lastYear = today.getYear() - 1; }
        else { lastMonth = today.getMonthValue() - 1; lastYear = today.getYear(); }
        int thisCount = 0, lastCount = 0;
        for (String ds : getSpellCounts().keySet()) {
            LocalDate d = LocalDate.parse(ds);
            if (d.getYear() == today.getYear() && d.getMonthValue() == today.getMonthValue()) thisCount++;
            else if (d.getYear() == lastYear && d.getMonthValue() == lastMonth) lastCount++;
        }
        return new int[]{thisCount, lastCount};
    }

    public static String exportWords(List<Word> words, String fmt) {
        if ("txt".equals(fmt)) {
            StringBuilder sb = new StringBuilder();
            for (Word w : words) {
                String ex = w.hasExample() ? "\n  e.g. " + w.example : "";
                String note = w.hasNote() ? "\n  note: " + w.note : "";
                sb.append(w.word).append("  [").append(nz(w.pos)).append("]  ")
                  .append(nz(w.translation)).append(ex).append(note).append("\n");
            }
            return sb.toString().stripTrailing();
        } else if ("csv".equals(fmt)) {
            StringBuilder sb = new StringBuilder();
            sb.append("word,translation,pos,example,note,status,mastered,wrong_count,date_added\n");
            for (Word w : words) {
                sb.append(csv(w.word)).append(',').append(csv(w.translation)).append(',')
                  .append(csv(w.pos)).append(',').append(csv(w.example)).append(',')
                  .append(csv(w.note)).append(',').append(csv(w.status)).append(',')
                  .append(w.mastered).append(',').append(w.wrongCount).append(',')
                  .append(csv(w.dateAdded)).append('\n');
            }
            return sb.toString();
        }
        return "";
    }

    // ── helpers ─────────────────────────────────────────────────────
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private static void exec(String sql, Binder b) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[db] exec failed: " + e.getMessage());
        }
    }

    private static List<Word> query(String sql) {
        List<Word> out = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            System.err.println("[db] query failed: " + e.getMessage());
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
