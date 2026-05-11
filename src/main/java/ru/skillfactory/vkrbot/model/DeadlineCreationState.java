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
}
