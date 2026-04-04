package ru.skillfactory.vkrbot.model;

import lombok.Data;

import java.util.List;

@Data
public class TaskCreationState {
    private int step = 0;
    private String title;
    private String description;
    private List<String> completionCriteriaList;
    private Deadline deadline;
}
