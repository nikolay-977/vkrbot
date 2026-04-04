package ru.skillfactory.vkrbot.model;

public enum Status {
    CREATED("Создана"),
    IN_PROGRESS("В работе"),
    WAITING_FOR_REVIEW("Ожидает проверки"),
    REVIEW("На проверке"),
    NEED_IMPROVEMENT("Требует доработки"),
    DONE("Принята"),
    CANCELED("Отменена");

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}