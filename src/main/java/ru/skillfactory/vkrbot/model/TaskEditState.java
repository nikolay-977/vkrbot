package ru.skillfactory.vkrbot.model;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class TaskEditState implements Serializable {
    private static final long serialVersionUID = 1L;
    private int step = 0;
    private String title;
    private String description;
    private List<String> criteriaList;
    private Long taskId;
}