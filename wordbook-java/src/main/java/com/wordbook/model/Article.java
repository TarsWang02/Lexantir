package com.wordbook.model;

/** A generated CLIL reading passage (stored in the daily article library). */
public class Article {
    public int id;
    public String date;        // local date the words were collected, YYYY-MM-DD
    public String domain;
    public String difficulty;
    public String type;
    public String content;     // passage text; target words wrapped in **double asterisks**
    public String createdAt;
    public String readAt;      // null until the user marks it read
    public String userNote;    // the user's own reflection note for this article

    public Article() {}
}
