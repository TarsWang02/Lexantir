package com.wordbook.model;

/** One row of the {@code words} table (mirrors db.py schema). */
public class Word {
    public int id;
    public String word;
    public String translation;
    public String pos;
    public String example;
    public String exampleTranslation;
    public String note;
    public String status = "default";   // 'default' | 'practice'
    public int mastered = 0;
    public int unfinished = 0;           // rolled over from a past daily session
    public int wrongCount = 0;
    public String masteredAt;
    public String dateAdded;
    public String createdAt;
    public String senses;                // JSON list of {pos, terms[]} — multiple meanings

    public Word() {}

    public boolean hasExample() {
        return example != null && !example.isBlank();
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }
}
