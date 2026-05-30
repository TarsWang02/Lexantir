package com.wordbook.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Google Translate (en→zh-CN) via the free keyless endpoint. A single request
 * with {@code dt=t&dt=bd} returns BOTH the main translation and a bilingual
 * dictionary block (multiple meanings grouped by part-of-speech) — so polysemy
 * is shown at no extra request/cost.
 */
public final class Translator {
    private Translator() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern VALID = Pattern.compile("^[a-zA-Z\\s\\-]+$");

    /** One dictionary sense: a part-of-speech and its candidate translations. */
    public record Sense(String pos, List<String> terms) {}

    /** Full lookup result: primary translation, best POS, and grouped senses. */
    public record Rich(String translation, String pos, List<Sense> senses) {}

    public record ProcessedWord(String word, String translation, String pos, List<Sense> senses) {}

    /** Rich lookup — one network call returns translation + senses + accurate POS. */
    public static Rich translateRich(String word) {
        word = word == null ? "" : word.strip();
        if (word.isEmpty()) return new Rich(null, "noun", List.of());
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx"
                    + "&sl=en&tl=zh-CN&dt=t&dt=bd&q="
                    + URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return new Rich(null, detectPos(word), List.of());

            JsonArray root = JsonParser.parseString(resp.body()).getAsJsonArray();

            // [0] = translation segments
            StringBuilder sb = new StringBuilder();
            if (root.size() > 0 && root.get(0).isJsonArray()) {
                JsonArray segs = root.get(0).getAsJsonArray();
                for (JsonElement se : segs) {
                    JsonArray seg = se.getAsJsonArray();
                    if (!seg.get(0).isJsonNull()) sb.append(seg.get(0).getAsString());
                }
            }
            String primary = sb.toString();

            // [1] = bilingual dictionary: [ [pos, [terms...], ...], ... ]
            List<Sense> senses = new ArrayList<>();
            if (root.size() > 1 && root.get(1).isJsonArray()) {
                for (JsonElement de : root.get(1).getAsJsonArray()) {
                    JsonArray e = de.getAsJsonArray();
                    String p = (e.size() > 0 && !e.get(0).isJsonNull()) ? e.get(0).getAsString() : "";
                    List<String> terms = new ArrayList<>();
                    if (e.size() > 1 && e.get(1).isJsonArray()) {
                        for (JsonElement te : e.get(1).getAsJsonArray()) {
                            if (!te.isJsonNull()) terms.add(te.getAsString());
                        }
                    }
                    if (!terms.isEmpty()) senses.add(new Sense(p, terms));
                }
            }

            String pos = senses.isEmpty() ? detectPos(word) : mapDictPos(senses.get(0).pos());
            return new Rich(primary, pos, senses);
        } catch (Exception e) {
            System.err.println("Translation error: " + e.getMessage());
            return new Rich(null, detectPos(word), List.of());
        }
    }

    /** Primary translation only (kept for callers that just need one string). */
    public static String translateWord(String word) {
        return translateRich(word).translation();
    }

    public static ProcessedWord processWord(String word) {
        word = word == null ? "" : word.strip();
        if (word.isEmpty() || !VALID.matcher(word).matches()) return null;
        Rich r = translateRich(word);
        return new ProcessedWord(word, r.translation(), r.pos(), r.senses());
    }

    // ── Sense (de)serialisation for storing meanings in the DB ──────
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    public static String serializeSenses(List<Sense> senses) {
        return (senses == null || senses.isEmpty()) ? null : GSON.toJson(senses);
    }

    public static List<Sense> parseSenses(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var type = new com.google.gson.reflect.TypeToken<List<Sense>>() {}.getType();
            List<Sense> r = GSON.fromJson(json, type);
            return r == null ? List.of() : r;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Compact display lines for multiple meanings, e.g. "n.  弹簧 · 春天 · 春季".
     *  Returns empty when there is only a single distinct meaning. */
    public static List<String> senseLines(List<Sense> senses, int maxSenses, int maxTerms) {
        List<String> lines = new ArrayList<>();
        if (senses == null || senses.isEmpty()) return lines;
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
        for (Sense s : senses) distinct.addAll(s.terms());
        if (distinct.size() <= 1) return lines;
        int shown = 0;
        for (Sense s : senses) {
            if (shown >= maxSenses) break;
            List<String> terms = s.terms().size() > maxTerms ? s.terms().subList(0, maxTerms) : s.terms();
            lines.add(posAbbr(s.pos()) + "  " + String.join(" · ", terms));
            shown++;
        }
        return lines;
    }

    public static String posAbbr(String pos) {
        if (pos == null) return "";
        return switch (pos.toLowerCase()) {
            case "noun" -> "n.";
            case "verb" -> "v.";
            case "adjective" -> "adj.";
            case "adverb" -> "adv.";
            case "pronoun" -> "pron.";
            case "preposition" -> "prep.";
            case "conjunction" -> "conj.";
            default -> pos.length() > 4 ? pos.substring(0, 3) + "." : pos;
        };
    }

    /** Map a dictionary POS word (noun/verb/adjective/…) to our 4 buckets. */
    private static String mapDictPos(String p) {
        if (p == null) return "noun";
        return switch (p.toLowerCase()) {
            case "verb" -> "verb";
            case "adjective" -> "adj";
            case "adverb" -> "adv";
            case "noun" -> "noun";
            default -> "noun";
        };
    }

    public static String detectPos(String word) {
        word = word.toLowerCase().strip();
        if (endsWithAny(word, "tion", "ness", "ment", "ity", "ism", "er", "or")) return "noun";
        if (endsWithAny(word, "ify", "ize", "ise", "ate", "en")) return "verb";
        if (endsWithAny(word, "ful", "less", "ous", "ive", "al", "ic", "ble", "ant", "ent")) return "adj";
        if (endsWithAny(word, "ly")) return "adv";
        return "noun";
    }

    private static boolean endsWithAny(String s, String... suffixes) {
        for (String suf : suffixes) if (s.endsWith(suf)) return true;
        return false;
    }
}
