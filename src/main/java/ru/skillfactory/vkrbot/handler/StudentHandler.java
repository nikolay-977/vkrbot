package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.DeadlineRepository;
import ru.skillfactory.vkrbot.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StudentHandler extends BaseHandler {

    private final DeadlineRepository deadlineRepository;
    private final TaskRepository taskRepository;
    private final TaskHandler taskHandler;

    private final Map<Long, List<Deadline>> studentDeadlinesState = new HashMap<>();
    private final Map<Long, List<Task>> studentTasksState = new HashMap<>();
    private final Map<Long, Deadline> currentDeadlineCache = new HashMap<>();

    public StudentHandler(DeadlineRepository deadlineRepository,
                          TaskRepository taskRepository,
                          TaskHandler taskHandler) {
        this.deadlineRepository = deadlineRepository;
        this.taskRepository = taskRepository;
        this.taskHandler = taskHandler;
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
        List<Deadline> deadlines = deadlineRepository.findByStudent(student);
        studentDeadlinesState.put(chatId, deadlines);
        studentTasksState.remove(chatId);
        currentDeadlineCache.remove(chatId);

        if (deadlines.isEmpty()) {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId,
                    "📋 Нет дедлайнов.",
                    botService.getNavigationHandler().getStudentDeadlinesKeyboard());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📋 ДЕДЛАЙНЫ\n\n");
        for (int i = 0; i < deadlines.size(); i++) {
            Deadline deadline = deadlines.get(i);
            message.append(String.format("%d. %s\n", i + 1, deadline.getTitle()));
            message.append(String.format("   ⏰ Дата: %s\n",
                    deadline.getDeadlineDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
            message.append("\n");
        }
        message.append("Введите номер дедлайна для просмотра задач.");
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getStudentDeadlinesKeyboard());
    }

    public void showStudentTasksForDeadline(long chatId, Deadline deadline, User student) {
        List<Task> tasks = taskRepository.findByDeadline(deadline);
        studentTasksState.put(chatId, tasks);
        studentDeadlinesState.remove(chatId);
        currentDeadlineCache.put(chatId, deadline);

        if (tasks.isEmpty()) {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId,
                    String.format("📌 %s\n\nНет задач.", deadline.getTitle()),
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
        if (!studentDeadlinesState.containsKey(chatId)) {
            return false;
        }
        try {
            int deadlineNumber = Integer.parseInt(messageText) - 1;
            List<Deadline> deadlines = studentDeadlinesState.get(chatId);
            if (deadlineNumber >= 0 && deadlineNumber < deadlines.size()) {
                showStudentTasksForDeadline(chatId, deadlines.get(deadlineNumber), student);
                return true;
            }
        } catch (NumberFormatException e) {
        }
        return false;
    }

    public boolean handleStudentTaskSelection(long chatId, String messageText, User student) {
        if (!studentTasksState.containsKey(chatId)) {
            return false;
        }
        try {
            int taskNumber = Integer.parseInt(messageText) - 1;
            List<Task> tasks = studentTasksState.get(chatId);
            if (taskNumber >= 0 && taskNumber < tasks.size()) {
                taskHandler.showForAction(chatId, tasks.get(taskNumber), student);
                return true;
            }
        } catch (NumberFormatException e) {
        }
        return false;
    }

    public Deadline getCurrentDeadline(long chatId) {
        return currentDeadlineCache.get(chatId);
    }
}