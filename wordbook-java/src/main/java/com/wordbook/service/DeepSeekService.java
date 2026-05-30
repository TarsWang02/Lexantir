package com.wordbook.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wordbook.db.Database;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek (deepseek-v4-flash) client for the "learn a skill in the language"
 * feature: cluster today's words into domains, then generate a CLIL reading
 * passage that re-uses those words. OpenAI-compatible endpoint; the API key is
 * read from the local DB (never hard-coded). v4-flash is a reasoning model, so
 * we only read {@code message.content} (ignoring {@code reasoning_content}) and
 * allow generous output budget.
 */
public final class DeepSeekService {
    private DeepSeekService() {}

    private static final String URL = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-v4-flash";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static String apiKey() { return Database.getMeta("deepseek_api_key", ""); }
    public static boolean hasKey() { return !apiKey().isBlank(); }

    // ── Public API ──────────────────────────────────────────────────

    /** Group today's words into 2–4 thematic domain labels (most relevant first). */
    public static List<String> suggestDomains(List<String> words) {
        String sys = "You group vocabulary into thematic domains for a learner. "
                + "Output ONLY a compact JSON array of 2 to 4 short domain labels "
                + "(each 1-4 words, Title Case), most relevant first. No prose, no markdown fences.";
        try {
            String content = chat(sys, "WORDS: " + String.join(", ", words), 1500, 0.3);
            List<String> domains = parseStringArray(content);
            return domains.isEmpty() ? List.of("General English") : domains;
        } catch (Exception e) {
            System.err.println("[deepseek] suggestDomains failed: " + e.getMessage());
            return List.of("General English");
        }
    }

    /** Generate a passage in {@code domain} that weaves in the target words,
     *  each used word wrapped in **double asterisks** for highlighting. */
    public static String generateArticle(List<String> words, String domain,
                                         String difficulty, String type) {
        String level = switch (difficulty == null ? "" : difficulty) {
            case "easy"     -> "simple (CEFR A2), short and clear sentences";
            case "advanced" -> "advanced (CEFR B2-C1), rich and nuanced";
            default         -> "medium (CEFR B1)";
        };
        String sys = "You are a writing tutor creating reading material for an English learner "
                + "using Content-and-Language-Integrated-Learning. Write a coherent, engaging passage "
                + "in the requested DOMAIN and at the requested LEVEL. Include EVERY one of the TARGET "
                + "WORDS at least once. Most will fit the domain naturally; for the few that do not fit, "
                + "bring them in briefly with a metaphor, analogy, comparison or short aside — but do NOT "
                + "omit any. Keep it coherent; do not just list the words. Wrap every target word you use "
                + "in **double asterisks**.\n"
                + "OUTPUT FORMAT, exactly:\n"
                + "First line: a one-sentence Simplified-Chinese reading guide that previews what the "
                + "passage is about, prefixed exactly with \"导读：\".\n"
                + "Then a blank line.\n"
                + "Then the English passage. No title, no other notes.";
        String user = "DOMAIN: " + domain
                + "\nLEVEL: " + level
                + "\nTYPE: " + type
                + "\nLENGTH: about 150-220 words"
                + "\nTARGET WORDS: " + String.join(", ", words);
        return chat(sys, user, 3000, 0.8);
    }

    /** A short, real-ish quotation related to the domain (for the achievement card). */
    public static String domainQuote(String domain) {
        String sys = "You provide one well-known, real quotation. Output exactly: "
                + "\"<quote>\" — <author>. No extra text, no markdown.";
        try {
            return chat(sys, "Give one famous quotation related to: " + domain, 800, 0.5).strip();
        } catch (Exception e) {
            return "";
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────
    private static String chat(String system, String user, int maxTokens, double temperature) {
        String key = apiKey();
        if (key.isBlank()) throw new RuntimeException("No DeepSeek API key is set.");

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("stream", false);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", temperature);
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        body.add("messages", messages);

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("DeepSeek HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return content == null ? "" : content.strip();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    // ── Parsing helpers ─────────────────────────────────────────────
    private static String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }

    private static List<String> parseStringArray(String content) {
        List<String> out = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(stripFences(content)).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                String s = arr.get(i).getAsString().strip();
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
