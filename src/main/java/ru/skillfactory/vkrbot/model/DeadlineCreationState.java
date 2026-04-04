package ru.skillfactory.vkrbot.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeadlineCreationState {
    private int step = 0;
    private String title;
    private String description;
    private LocalDateTime deadlineDate;
    private User student;
}
