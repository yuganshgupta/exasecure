package com.examsystem.models;

import java.sql.Timestamp;

/** Represents an exam definition. */
public class Exam {
    private int id;
    private String title;
    private int durationMinutes;
    private int createdBy;
    private Timestamp createdAt;

    public Exam() {}

    public Exam(int id, String title, int durationMinutes, int createdBy, Timestamp createdAt) {
        this.id = id;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getCreatedBy() { return createdBy; }
    public Timestamp getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}