package ru.skillfactory.vkrbot.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskCreationState implements Serializable {
    private static final long serialVersionUID = 1L;
    private int step = 0;
    private String title;
    private String description;
    private List<String> completionCriteriaList;
    private Long deadlineId;

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getCompletionCriteriaList() {
        return completionCriteriaList;
    }

    public void setCompletionCriteriaList(List<String> list) {
        this.completionCriteriaList = list;
    }

    public Long getDeadlineId() {
        return deadlineId;
    }

    public void setDeadlineId(Long deadlineId) {
        this.deadlineId = deadlineId;
    }
}