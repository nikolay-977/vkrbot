package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

@Component
@Slf4j
public class DeadlineHandler extends BaseHandler {

    private final StateService stateService;
    private TaskHandler taskHandler;

    public DeadlineHandler(StateService stateService) {
        this.stateService = stateService;
    }

    public void setTaskHandler(TaskHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    private String creationKey(long chatId) {
        return "deadlineCreation:" + chatId;
    }

    private String selectedDeadlineIdKey(long chatId) {
        return "selectedDeadlineId:" + chatId;
    }

    public boolean isInCreationState(long chatId) {
        return stateService.hasState(creationKey(chatId));
    }

    public void clearSelectedDeadline(long chatId) {
        stateService.removeState(selectedDeadlineIdKey(chatId));
    }

    public boolean handleDeadlineAction(long chatId, String messageText, User user) {
        Long deadlineId = stateService.getState(selectedDeadlineIdKey(chatId), Long.class);
        if (deadlineId == null) return false;

        Deadline deadline = botService.getDeadlineRepository()
                .findByIdWithTasks(deadlineId).orElse(null);
        if (deadline == null) {
            stateService.removeState(selectedDeadlineIdKey(chatId));
            return false;
        }

        if (messageText.equals("➕ Добавить задачу")) {
            taskHandler.startCreation(chatId, deadline, user);
            return true;
        }

        if (deadline.getTasks().isEmpty()) {
            sendTextMessage(chatId, "❌ В этом дедлайне пока нет задач. Добавьте задачу с помощью кнопки выше.");
            return true;
        }

        try {
            int taskNumber = Integer.parseInt(messageText) - 1;
            if (taskNumber >= 0 && taskNumber < deadline.getTasks().size()) {
                Task selectedTask = deadline.getTasks().get(taskNumber);
                // Не удаляем selectedDeadlineIdKey, чтобы можно было вернуться
                taskHandler.showForAction(chatId, selectedTask, user);
                return true;
            } else {
                sendTextMessage(chatId, "❌ Неверный номер задачи. Введите число от 1 до " + deadline.getTasks().size());
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void handleCreation(long chatId, String messageText) {
        DeadlineCreationState state = stateService.getState(creationKey(chatId), DeadlineCreationState.class);
        if (state == null) {
            sendTextMessage(chatId, "❌ Сессия создания дедлайна истекла. Начните заново.");
            return;
        }

        if (messageText.equals("🏠Главное меню") || messageText.equals("🔙 Назад к студентам")) {
            stateService.removeState(creationKey(chatId));
            botService.getNavigationHandler().sendMainMenu(chatId,
                    botService.getTelegramService().getUserByChatId(chatId).orElse(null));
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                stateService.saveState(creationKey(chatId), state);
                sendTextMessage(chatId, "Введите описание дедлайна:");
                break;
            case 1:
                state.setDescription(messageText);
                state.setStep(2);
                stateService.saveState(creationKey(chatId), state);
                sendTextMessage(chatId, "Введите дату дедлайна (формат: ГГГГ-ММ-ДД ЧЧ:ММ):");
                break;
            case 2:
                try {
                    String trimmed = messageText.trim();
                    String normalized = trimmed.replaceAll("\\s", " ");
                    LocalDateTime deadlineDate = LocalDateTime.parse(normalized,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    state.setDeadlineDate(deadlineDate);
                    Deadline deadline = new Deadline();
                    deadline.setTitle(state.getTitle());
                    deadline.setDescription(state.getDescription());
                    deadline.setDeadlineDate(state.getDeadlineDate());
                    deadline.setStudent(state.getStudent());
                    Optional<User> supervisorOpt = botService.getTelegramService().getUserByChatId(chatId);
                    supervisorOpt.ifPresent(deadline::setSupervisor);
                    Deadline savedDeadline = botService.getDeadlineRepository().save(deadline);
                    // Сохраняем ID, а не объект
                    stateService.saveState(selectedDeadlineIdKey(chatId), savedDeadline.getId());
                    sendTextMessage(chatId, "✅ Дедлайн успешно создан!");
                    stateService.removeState(creationKey(chatId));
                    showTasksMenu(chatId, savedDeadline, supervisorOpt.orElse(null));
                } catch (DateTimeParseException e) {
                    log.warn("Failed to parse date: '{}'", messageText);
                    sendTextMessage(chatId, "❌ Неверный формат даты. Используйте ГГГГ-ММ-ДД ЧЧ:ММ (например, 2026-05-11 13:00)");
                } catch (Exception e) {
                    log.error("Unexpected error during deadline creation", e);
                    sendTextMessage(chatId, "❌ Ошибка при создании дедлайна: " + e.getMessage());
                }
                break;
        }
    }

    public void startCreation(long chatId, User student, User supervisor) {
        DeadlineCreationState state = new DeadlineCreationState();
        state.setStudent(student);
        state.setStep(0);
        stateService.saveState(creationKey(chatId), state);
        sendTextMessage(chatId, "Введите название дедлайна:");
    }

    public void showTasksMenu(long chatId, Deadline deadline, User supervisor) {
        Deadline loadedDeadline = botService.getDeadlineRepository()
                .findByIdWithTasks(deadline.getId()).orElse(deadline);
        stateService.saveState(selectedDeadlineIdKey(chatId), loadedDeadline.getId());
        StringBuilder message = new StringBuilder();
        message.append("📌 Дедлайн: ").append(loadedDeadline.getTitle()).append("\n");
        message.append("⏰ Дата: ").append(loadedDeadline.getDeadlineDate()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        message.append("📋 Задачи:\n\n");
        if (loadedDeadline.getTasks().isEmpty()) {
            message.append("Нет задач.\n");
        } else {
            for (int i = 0; i < loadedDeadline.getTasks().size(); i++) {
                Task task = loadedDeadline.getTasks().get(i);
                String statusEmoji = getStatusEmoji(task.getStatus());
                message.append(String.format("%d. %s %s\n", i + 1, statusEmoji, task.getTitle()));
            }
        }
        message.append("\nВведите номер задачи для просмотра деталей.");
        sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getSupervisorTaskKeyboard());
    }
}