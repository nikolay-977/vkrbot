package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class DeadlineHandler extends BaseHandler {

    private final Map<Long, DeadlineCreationState> deadlineCreationStates = new HashMap<>();
    private final Map<Long, Deadline> selectedDeadlines = new HashMap<>();

    private TaskHandler taskHandler;

    public void setTaskHandler(TaskHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    public boolean isInCreationState(long chatId) {
        return deadlineCreationStates.containsKey(chatId);
    }

    public void clearSelectedDeadline(long chatId) {
        selectedDeadlines.remove(chatId);
    }

    public boolean handleDeadlineAction(long chatId, String messageText, User user) {
        if (!selectedDeadlines.containsKey(chatId)) {
            return false;
        }

        if (messageText.equals("➕ Добавить задачу")) {
            Deadline currentDeadline = selectedDeadlines.get(chatId);
            taskHandler.startCreation(chatId, currentDeadline, user);
            return true;
        }

        try {
            int taskNumber = Integer.parseInt(messageText) - 1;
            Deadline deadline = selectedDeadlines.get(chatId);
            if (taskNumber >= 0 && taskNumber < deadline.getTasks().size()) {
                Task selectedTask = deadline.getTasks().get(taskNumber);
                log.info("Selected task: {} for deadline: {}", selectedTask.getTitle(), deadline.getTitle());
                taskHandler.showForAction(chatId, selectedTask, user);
                return true;
            } else {
                sendTextMessage(chatId, "❌ Неверный номер задачи.");
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void handleCreation(long chatId, String messageText) {
        DeadlineCreationState state = deadlineCreationStates.get(chatId);

        if (messageText.equals("Главное меню") || messageText.equals("🔙 Назад к студентам")) {
            deadlineCreationStates.remove(chatId);
            botService.getNavigationHandler().sendMainMenu(chatId,
                    botService.getTelegramService().getUserByChatId(chatId).orElse(null));
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                sendTextMessage(chatId, "Введите описание дедлайна:");
                break;
            case 1:
                state.setDescription(messageText);
                state.setStep(2);
                sendTextMessage(chatId, "Введите дату дедлайна (формат: ГГГГ-ММ-ДД ЧЧ:ММ):");
                break;
            case 2:
                try {
                    LocalDateTime deadlineDate = LocalDateTime.parse(messageText,
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
                    selectedDeadlines.put(chatId, savedDeadline);

                    sendTextMessage(chatId, "✅ Дедлайн успешно создан!");
                    deadlineCreationStates.remove(chatId);
                    showTasksMenu(chatId, savedDeadline, supervisorOpt.get());

                } catch (Exception e) {
                    sendTextMessage(chatId, "❌ Неверный формат даты.");
                }
                break;
        }
    }

    public void startCreation(long chatId, User student, User supervisor) {
        DeadlineCreationState state = new DeadlineCreationState();
        state.setStudent(student);
        state.setStep(0);
        deadlineCreationStates.put(chatId, state);
        sendTextMessage(chatId, "Введите название дедлайна:");
    }

    public void showTasksMenu(long chatId, Deadline deadline, User supervisor) {
        Deadline loadedDeadline = botService.getDeadlineRepository()
                .findByIdWithTasks(deadline.getId()).orElse(deadline);
        selectedDeadlines.put(chatId, loadedDeadline);

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
                botService.getNavigationHandler().getTaskKeyboard());
    }
}