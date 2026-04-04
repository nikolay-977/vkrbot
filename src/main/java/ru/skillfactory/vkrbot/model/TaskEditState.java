package ru.skillfactory.vkrbot.model;

import lombok.Data;

import java.util.List;

@Data
public class TaskEditState {
    private int step = 0;
    private String title;
    private String description;
    private List<String> criteriaList;
    private Task task;
}
