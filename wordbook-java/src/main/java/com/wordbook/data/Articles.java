package com.wordbook.data;

import java.util.List;
import java.util.Map;

/** Sample CLIL articles + motivational quote pools (port of articles.py). */
public final class Articles {
    private Articles() {}

    public record Article(String id, String title, String category,
                          String chineseSummary, String englishText) {}

    public record Quote(String text, String author) {}

    public static final List<Article> SAMPLE_ARTICLES = List.of(
        new Article(
            "art-1",
            "The Ephemeral Whispers of Nature",
            "Linguistics & Aesthetics",
            "探索日本美学中的『物哀』（Mono no aware），解释事物因其短暂性"
            + "（ephemeral）而显得尤为珍贵的深刻含义。",
            "We live in an era where speed is ubiquitous. We hurry through "
            + "public streets, parse digital text in milliseconds, and barely "
            + "recognize the gradual changing of seasons. Yet, history whispers "
            + "that true value is found in the fleeting.\n\n"
            + "In traditional aesthetics, the concept of cherry blossoms "
            + "encapsulates this perfectly. Their beauty is inherently "
            + "ephemeral. They bloom for less than a week, rendering their "
            + "exquisite arrival a delicate, temporary miracle. If they were "
            + "permanent, we would foster no deep gratitude for their presence. "
            + "This realization helps mitigate our distress over time's rapid "
            + "passage. By recognizing that moments are transient, we learn to "
            + "live with richer awareness."
        ),
        new Article(
            "art-2",
            "Building Mind Resilience in Stressful Times",
            "Psychology",
            "阐述在快节奏现代生活中，如何通过日常的微小挑战来培养意志力与复原力"
            + "（resilience），从而笑对逆境。",
            "Modern life demands constant mental gymnastics. Every "
            + "professional encounters unforeseen obstacles that test their "
            + "focus. When goals fail or code crashes, what separates those who "
            + "collapse from those who succeed is mind resilience.\n\n"
            + "Psychologists argue that resilience is not a fixed genetic "
            + "trait; rather, it is a psychological muscle. To foster this "
            + "muscle, one must deliberately face uncomfortable tasks with a "
            + "sagacious mindset. We must analyze mistakes with meticulous "
            + "precision instead of hiding from them, gradually converting "
            + "friction into power. As we survive each micro-crisis, we "
            + "discover that our capability to endure and adapt is far "
            + "stronger than we once assumed."
        ),
        new Article(
            "art-3",
            "Meticulous Details: The Morandi Aesthetic Palette",
            "Art & Design",
            "讲述意大利大师乔治·莫兰迪（Giorgio Morandi）的一生与艺术理念。"
            + "他通过低饱和度、温暖、带灰度的色调创造出雄辩（eloquent）而又"
            + "沉静的空间魅力。",
            "Art does not need to shout to be eloquent. In the quiet studios "
            + "of Bologna, Giorgio Morandi spent his life painting simple "
            + "objects—dusty bottles, clay cups, and worn tins. He was "
            + "meticulous in his arrangements, sometimes spending hours "
            + "adjusting a single bottle's position by a fraction of an inch.\n\n"
            + "His legacy is a beautiful, desaturated palette that feels "
            + "incredibly calm and grounded. Instead of vivid colors, Morandi "
            + "used warm greys, dusty ambers, sage greens, and muted earth "
            + "tones. The resulting canvases feel lucid and thoughtful. This "
            + "neutral simplicity reminds us that restraining our expressive "
            + "colors can evoke a deeper, more permanent sense of harmony than "
            + "flamboyant neon."
        )
    );

    public static final Map<String, List<Quote>> QUOTE_POOLS = Map.of(
        "excellent", List.of(
            new Quote("You do not rise to the level of your goals. You fall to the level "
                    + "of your systems.", "James Clear"),
            new Quote("We are what we repeatedly do. Excellence, then, is not an act, but "
                    + "a habit.", "Aristotle"),
            new Quote("Success is the sum of small efforts, repeated day in and day out.",
                    "Robert Collier")
        ),
        "good", List.of(
            new Quote("Consistency is the mother of mastery.", "Robin Sharma"),
            new Quote("The secret of getting ahead is getting started.", "Mark Twain"),
            new Quote("Don't watch the clock; do what it does. Keep going.", "Sam Levenson")
        ),
        "encouragement", List.of(
            new Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"),
            new Quote("It does not matter how slowly you go as long as you do not stop.",
                    "Confucius"),
            new Quote("You don't have to be great to start, but you have to start to be "
                    + "great.", "Zig Ziglar"),
            new Quote("千里之行，始于足下。", "Chinese Proverb")
        )
    );
}
