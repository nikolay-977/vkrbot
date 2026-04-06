package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SupervisorHandler extends BaseHandler {

    private final Map<Long, List<User>> studentListCache = new HashMap<>();
    private final Map<Long, User> selectedStudents = new HashMap<>();
    private final DeadlineHandler deadlineHandler;

    public SupervisorHandler(DeadlineHandler deadlineHandler) {
        this.deadlineHandler = deadlineHandler;
    }

    public void showStudentsList(long chatId, User supervisor) {
        log.info("=== SHOW STUDENTS LIST ===");
        List<User> students = botService.getUserRepository().findBySupervisor(supervisor);

        if (students.isEmpty()) {
            botService.getNavigationHandler().sendMainMenu(chatId, supervisor);
            return;
        }

        StringBuilder message = new StringBuilder("📋 Ваши студенты:\n\n");
        for (int i = 0; i < students.size(); i++) {
            User student = students.get(i);
            List<Deadline> deadlines = botService.getDeadlineRepository()
                    .findByStudentAndSupervisorWithTasks(student, supervisor);
            int totalTasks = deadlines.stream().mapToInt(d -> d.getTasks().size()).sum();
            message.append(String.format("%d. %s (задач: %d)\n", i + 1, student.getFullName(), totalTasks));
        }
        message.append("\nВведите номер студента для просмотра его задач:");

        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getSupervisorMainKeyboard());

        studentListCache.put(chatId, students);
    }

    public boolean handleStudentSelection(long chatId, String messageText, User user) {
        if (studentListCache.containsKey(chatId)) {
            try {
                int studentNumber = Integer.parseInt(messageText) - 1;
                List<User> students = studentListCache.get(chatId);
                if (studentNumber >= 0 && studentNumber < students.size()) {
                    User selectedStudent = students.get(studentNumber);
                    studentListCache.remove(chatId);
                    showStudentTasks(chatId, selectedStudent, user);
                    return true;
                } else {
                    botService.getNavigationHandler().sendTextMessage(chatId, "❌ Неверный номер студента.");
                    return true;
                }
            } catch (NumberFormatException e) {
                studentListCache.remove(chatId);
                botService.getNavigationHandler().sendMainMenu(chatId, user);
                return true;
            }
        }
        return false;
    }

    public void showStudentTasks(long chatId, User student, User supervisor) {
        selectedStudents.put(chatId, student);
        sendStudentTasksMenu(chatId, student, supervisor);
    }

    public boolean handleStudentChoice(long chatId, String messageText, User user) {
        if (selectedStudents.containsKey(chatId)) {
            if (messageText.equals("🔙 Назад к студентам")) {
                selectedStudents.remove(chatId);
                deadlineHandler.clearSelectedDeadline(chatId);
                showStudentsList(chatId, user);
                return true;
            }

            if (messageText.equals("➕ Добавить дедлайн")) {
                User selectedStudent = selectedStudents.get(chatId);
                if (selectedStudent != null) {
                    deadlineHandler.startCreation(chatId, selectedStudent, user);
                    return true;
                }
            }

            if (messageText.equals("🔙 Назад к дедлайнам")) {
                deadlineHandler.clearSelectedDeadline(chatId);
                User selectedStudent = selectedStudents.get(chatId);
                if (selectedStudent != null) {
                    sendStudentTasksMenu(chatId, selectedStudent, user);
                }
                return true;
            }

            try {
                int deadlineNumber = Integer.parseInt(messageText) - 1;
                User selectedStudent = selectedStudents.get(chatId);
                List<Deadline> deadlines = botService.getDeadlineRepository()
                        .findByStudentAndSupervisorWithTasks(selectedStudent, user);
                if (deadlineNumber >= 0 && deadlineNumber < deadlines.size()) {
                    deadlineHandler.showTasksMenu(chatId, deadlines.get(deadlineNumber), user);
                    return true;
                }
            } catch (NumberFormatException e) {
            }

            sendStudentTasksMenu(chatId, selectedStudents.get(chatId), user);
            return true;
        }
        return false;
    }

    private void sendStudentTasksMenu(long chatId, User student, User supervisor) {
        List<Deadline> deadlines = botService.getDeadlineRepository()
                .findByStudentAndSupervisorWithTasks(student, supervisor);

        StringBuilder message = new StringBuilder();
        message.append("📚 Студент: ").append(student.getFullName()).append("\n\n");
        message.append("📋 Дедлайны и задачи:\n\n");

        if (deadlines.isEmpty()) {
            message.append("Нет дедлайнов.\n");
        } else {
            for (int i = 0; i < deadlines.size(); i++) {
                Deadline deadline = deadlines.get(i);
                message.append(String.format("%d. %s\n", i + 1, deadline.getTitle()));
                message.append(String.format("   ⏰ Дедлайн: %s\n",
                        deadline.getDeadlineDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
                message.append(String.format("   📝 Задач: %d\n", deadline.getTasks().size()));

                if (!deadline.getTasks().isEmpty()) {
                    message.append("   Задачи:\n");
                    for (int j = 0; j < deadline.getTasks().size(); j++) {
                        Task task = deadline.getTasks().get(j);
                        String statusEmoji = getStatusEmoji(task.getStatus());
                        message.append(String.format("      %d.%d. %s %s\n", i + 1, j + 1, statusEmoji, task.getTitle()));
                    }
                }
                message.append("\n");
            }
        }

        message.append("\nВведите номер дедлайна для управления задачами.");
        botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message.toString(),
                botService.getNavigationHandler().getSupervisorDeadlineKeyboard());
    }
}