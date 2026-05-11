package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SupervisorHandler extends BaseHandler {

    private final DeadlineHandler deadlineHandler;
    private final StateService stateService;

    public SupervisorHandler(DeadlineHandler deadlineHandler, StateService stateService) {
        this.deadlineHandler = deadlineHandler;
        this.stateService = stateService;
    }

    private String studentListIdsKey(long chatId) {
        return "supervisorStudentListIds:" + chatId;
    }

    private String selectedStudentIdKey(long chatId) {
        return "supervisorSelectedStudentId:" + chatId;
    }

    public void showStudentsList(long chatId, User supervisor) {
        log.info("=== SHOW STUDENTS LIST ===");
        List<User> students = botService.getUserRepository().findBySupervisor(supervisor);

        if (students.isEmpty()) {
            botService.getNavigationHandler().sendMainMenu(chatId, supervisor);
            return;
        }

        List<Long> studentIds = students.stream().map(User::getId).collect(Collectors.toList());
        stateService.saveState(studentListIdsKey(chatId), studentIds);

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
    }

    public boolean handleStudentSelection(long chatId, String messageText, User user) {
        if (stateService.hasState(studentListIdsKey(chatId))) {
            try {
                int studentNumber = Integer.parseInt(messageText) - 1;
                @SuppressWarnings("unchecked")
                List<Long> studentIds = (List<Long>) stateService.getState(studentListIdsKey(chatId), List.class);
                if (studentIds != null && studentNumber >= 0 && studentNumber < studentIds.size()) {
                    Long selectedId = studentIds.get(studentNumber);
                    User selectedStudent = botService.getUserRepository().findById(selectedId).orElse(null);
                    if (selectedStudent != null) {
                        stateService.removeState(studentListIdsKey(chatId));
                        showStudentTasks(chatId, selectedStudent, user);
                    }
                    return true;
                } else {
                    botService.getNavigationHandler().sendTextMessage(chatId, "❌ Неверный номер студента.");
                    return true;
                }
            } catch (NumberFormatException e) {
                stateService.removeState(studentListIdsKey(chatId));
                botService.getNavigationHandler().sendMainMenu(chatId, user);
                return true;
            }
        }
        return false;
    }

    public void showStudentTasks(long chatId, User student, User supervisor) {
        stateService.saveState(selectedStudentIdKey(chatId), student.getId());
        sendStudentTasksMenu(chatId, student, supervisor);
    }

    public boolean handleStudentChoice(long chatId, String messageText, User user) {
        if (stateService.hasState(selectedStudentIdKey(chatId))) {
            if (messageText.equals("🔙 Назад к студентам")) {
                stateService.removeState(selectedStudentIdKey(chatId));
                deadlineHandler.clearSelectedDeadline(chatId);
                showStudentsList(chatId, user);
                return true;
            }

            if (messageText.equals("➕ Добавить дедлайн")) {
                Long studentId = stateService.getState(selectedStudentIdKey(chatId), Long.class);
                if (studentId != null) {
                    User selectedStudent = botService.getUserRepository().findById(studentId).orElse(null);
                    if (selectedStudent != null) {
                        deadlineHandler.startCreation(chatId, selectedStudent, user);
                    }
                }
                return true;
            }

            if (messageText.equals("🔙 Назад к дедлайнам")) {
                deadlineHandler.clearSelectedDeadline(chatId);
                Long studentId = stateService.getState(selectedStudentIdKey(chatId), Long.class);
                if (studentId != null) {
                    User selectedStudent = botService.getUserRepository().findById(studentId).orElse(null);
                    if (selectedStudent != null) {
                        sendStudentTasksMenu(chatId, selectedStudent, user);
                    }
                }
                return true;
            }

            try {
                int deadlineNumber = Integer.parseInt(messageText) - 1;
                Long studentId = stateService.getState(selectedStudentIdKey(chatId), Long.class);
                if (studentId != null) {
                    User selectedStudent = botService.getUserRepository().findById(studentId).orElse(null);
                    if (selectedStudent != null) {
                        List<Deadline> deadlines = botService.getDeadlineRepository()
                                .findByStudentAndSupervisorWithTasks(selectedStudent, user);
                        if (deadlineNumber >= 0 && deadlineNumber < deadlines.size()) {
                            deadlineHandler.showTasksMenu(chatId, deadlines.get(deadlineNumber), user);
                        } else {
                            botService.getNavigationHandler().sendTextMessage(chatId, "❌ Неверный номер дедлайна.");
                        }
                        return true;
                    }
                }
            } catch (NumberFormatException e) {

            }

            Long studentId = stateService.getState(selectedStudentIdKey(chatId), Long.class);
            if (studentId != null) {
                User selectedStudent = botService.getUserRepository().findById(studentId).orElse(null);
                if (selectedStudent != null) {
                    sendStudentTasksMenu(chatId, selectedStudent, user);
                }
            }
            return true;
        }
        return false;
    }

    private void sendStudentTasksMenu(long chatId, User student, User supervisor) {
        // Очищаем предыдущий выбранный дедлайн
        deadlineHandler.clearSelectedDeadline(chatId);

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