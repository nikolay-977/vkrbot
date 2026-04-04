package ru.skillfactory.vkrbot.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskDto {
    private Long id;
    private String title;
    private String description;
    private String completionCriteria;
    private LocalDateTime deadline;
    private Long studentId;
}