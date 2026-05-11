package ru.skillfactory.vkrbot.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DeadlineCreationState implements Serializable {
    private static final long serialVersionUID = 1L;
    private int step = 0;
    private String title;
    private String description;
    private LocalDateTime deadlineDate;
    private User student;

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(LocalDateTime deadlineDate) { this.deadlineDate = deadlineDate; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
}