package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.DeadlineRepository;
import ru.skillfactory.vkrbot.repository.TaskRepository;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StudentHandler extends BaseHandler {

    private final DeadlineRepository deadlineRepository;
    private final TaskRepository taskRepository;
    private final TaskHandler taskHandler;
    private final StateService stateService;

    public StudentHandler(DeadlineRepository deadlineRepository,
                          TaskRepository taskRepository,
                          TaskHandler taskHandler,
                          StateService stateService) {
        this.deadlineRepository = deadlineRepository;
        this.taskRepository = taskRepository;
        this.taskHandler = taskHandler;
        this.stateService = stateService;
    }

    private String deadlinesIdsKey(long chatId) {
        return "studentDeadlinesIds:" + chatId;
    }

    private String tasksIdsKey(long chatId) {
        return "studentTasksIds:" + chatId;
    }

    private String currentDeadlineIdKey(long chatId) {
        return "studentCurrentDeadlineId:" + chatId;
    }

    public void showDiplomaInfo(long chatId, User student) {
        StringBuilder message = new StringBuilder();
        message.append("🎓 ДИПЛОМ\n\n");
        message.append("Тема: ").append(student.getDiplomaSubject() != null ? student.getDiplomaSubject() : "не указана").append("\n\n");
        if (student.getSupervisor() != null) {
            message.append("Научный руководитель: ").append(student.getSupervisor().getFullName());
        } else {
            message.append("Научный руководитель: не назначен");
        }
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getStudentDiplomaKeyboard());
    }

    public void showSupervisorInfo(long chatId, User student) {
        if (student.getSupervisor() == null) {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId,
                    "❌ Научный руководитель не назначен.",
                    botService.getNavigationHandler().getStudentMainKeyboard());
            return;
        }
        User supervisor = student.getSupervisor();
        StringBuilder message = new StringBuilder();
        message.append("👨‍🏫 Научный руководитель:\n\n");
        message.append("ФИО: ").append(supervisor.getFullName()).append("\n");
        message.append("Email: ").append(supervisor.getEmail()).append("\n");
        if (supervisor.getPhone() != null) {
            message.append("Телефон: ").append(supervisor.getPhone()).append("\n");
        }
        if (supervisor.getTelegram() != null) {
            message.append("Telegram: ").append(supervisor.getTelegram()).append("\n");
        }
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getStudentMainKeyboard());
    }

    public void showStudentDeadlines(long chatId, User student) {
        List<Deadline> deadlines = deadlineRepository.findByStudentOrderByDeadlineDateAsc(student);
        List<Long> deadlineIds = deadlines.stream().map(Deadline::getId).collect(Collectors.toList());
        stateService.saveState(deadlinesIdsKey(chatId), deadlineIds);
        stateService.removeState(tasksIdsKey(chatId));
        stateService.removeState(currentDeadlineIdKey(chatId));

        if (deadlines.isEmpty()) {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId,
                    "📋 Нет дедлайнов.",
                    botService.getNavigationHandler().getStudentDeadlinesKeyboard());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📋 ДЕДЛАЙНЫ И ЗАДАЧИ:\n\n");

        for (int i = 0; i < deadlines.size(); i++) {
            Deadline deadline = deadlines.get(i);
            List<Task> tasks = taskRepository.findByDeadline(deadline);
            message.append(String.format("%d. %s\n", i + 1, deadline.getTitle()));
            message.append(String.format("   ⏰ Дедлайн: %s\n",
                    deadline.getDeadlineDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
            message.append(String.format("   📝 Задач: %d\n", tasks.size()));

            if (!tasks.isEmpty()) {
                message.append("   Задачи:\n");
                for (int j = 0; j < tasks.size(); j++) {
                    Task task = tasks.get(j);
                    String statusEmoji = getStatusEmoji(task.getStatus());
                    message.append(String.format("      %d.%d. %s %s\n", i + 1, j + 1, statusEmoji, task.getTitle()));
                }
            }
            message.append("\n");
        }

        message.append("\nВведите номер дедлайна для просмотра и управления задачами.");
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getStudentDeadlinesKeyboard());
    }

    public void showStudentTasksForDeadline(long chatId, Deadline deadline, User student) {
        List<Task> tasks = taskRepository.findByDeadline(deadline);
        List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
        stateService.saveState(tasksIdsKey(chatId), taskIds);
        stateService.removeState(deadlinesIdsKey(chatId));
        stateService.saveState(currentDeadlineIdKey(chatId), deadline.getId());

        if (tasks.isEmpty()) {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId,
                    String.format("📌 %s\n\n❌ Нет задач. Руководитель ещё не добавил задачи для этого дедлайна.", deadline.getTitle()),
                    botService.getNavigationHandler().getStudentTasksKeyboard());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📌 ").append(deadline.getTitle()).append("\n\n");
        message.append("📋 ЗАДАЧИ:\n\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String statusEmoji = getStatusEmoji(task.getStatus());
            message.append(String.format("%d. %s %s\n", i + 1, statusEmoji, task.getTitle()));
        }
        message.append("\nВведите номер задачи для просмотра деталей.");
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getStudentTasksKeyboard());
    }

    public boolean handleStudentDeadlineSelection(long chatId, String messageText, User student) {
        if (!stateService.hasState(deadlinesIdsKey(chatId))) {
            return false;
        }
        try {
            int deadlineNumber = Integer.parseInt(messageText) - 1;
            @SuppressWarnings("unchecked")
            List<Long> deadlineIds = (List<Long>) stateService.getState(deadlinesIdsKey(chatId), List.class);
            if (deadlineIds != null && deadlineNumber >= 0 && deadlineNumber < deadlineIds.size()) {
                Deadline deadline = deadlineRepository.findById(deadlineIds.get(deadlineNumber)).orElse(null);
                if (deadline != null) {
                    showStudentTasksForDeadline(chatId, deadline, student);
                }
                return true;
            }
        } catch (NumberFormatException e) {

        }
        return false;
    }

    public boolean handleStudentTaskSelection(long chatId, String messageText, User student) {
        if (!stateService.hasState(tasksIdsKey(chatId))) {
            return false;
        }
        try {
            int taskNumber = Integer.parseInt(messageText) - 1;
            @SuppressWarnings("unchecked")
            List<Long> taskIds = (List<Long>) stateService.getState(tasksIdsKey(chatId), List.class);
            if (taskIds != null && taskNumber >= 0 && taskNumber < taskIds.size()) {
                Task task = taskRepository.findById(taskIds.get(taskNumber)).orElse(null);
                if (task != null) {
                    taskHandler.showForAction(chatId, task, student);
                }
                return true;
            }
        } catch (NumberFormatException e) {

        }
        return false;
    }

    public Deadline getCurrentDeadline(long chatId) {
        Long deadlineId = stateService.getState(currentDeadlineIdKey(chatId), Long.class);
        if (deadlineId == null) return null;
        return deadlineRepository.findById(deadlineId).orElse(null);
    }
}