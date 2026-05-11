package ru.skillfactory.vkrbot.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.skillfactory.vkrbot.dto.AttachedFile;
import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.CommentRepository;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class TaskHandler extends BaseHandler {

    private final CommentRepository commentRepository;
    private final StateService stateService;
    private CommentMenuHandler commentMenuHandler;
    private FileMenuHandler fileMenuHandler;
    private DeadlineHandler deadlineHandler;

    public TaskHandler(CommentRepository commentRepository, StateService stateService) {
        this.commentRepository = commentRepository;
        this.stateService = stateService;
    }

    public void setDeadlineHandler(DeadlineHandler deadlineHandler) {
        this.deadlineHandler = deadlineHandler;
    }

    public void setCommentMenuHandler(CommentMenuHandler commentMenuHandler) {
        this.commentMenuHandler = commentMenuHandler;
    }

    public void setFileMenuHandler(FileMenuHandler fileMenuHandler) {
        this.fileMenuHandler = fileMenuHandler;
    }

    private String taskCreationKey(long chatId) { return "taskCreation:" + chatId; }
    private String selectedTaskIdKey(long chatId) { return "selectedTaskId:" + chatId; }
    private String taskEditKey(long chatId) { return "taskEdit:" + chatId; }
    private String reviewCommentKey(long chatId) { return "reviewComment:" + chatId; }
    private String currentDeadlineIdKey(long chatId) { return "currentDeadlineId:" + chatId; }
    private String criteriaMenuTaskIdKey(long chatId) { return "criteriaMenuTaskId:" + chatId; }
    private String criteriaActionKey(long chatId) { return "criteriaAction:" + chatId; }
    private String pendingReviewTaskIdKey(long chatId) { return "pendingReviewTaskId:" + chatId; }
    private String pendingCommentTaskIdKey(long chatId) { return "pendingCommentTaskId:" + chatId; }
    private String pendingFileTaskIdKey(long chatId) { return "pendingFileTaskId:" + chatId; }

    public boolean isInTaskView(long chatId) {
        return stateService.hasState(selectedTaskIdKey(chatId));
    }

    public boolean isInCreationState(long chatId) {
        return stateService.hasState(taskCreationKey(chatId));
    }

    public boolean isInEditState(long chatId) {
        return stateService.hasState(taskEditKey(chatId));
    }

    public boolean isInReviewCommentState(long chatId) {
        return stateService.hasState(reviewCommentKey(chatId));
    }

    public boolean isInCriteriaMenu(long chatId) {
        return stateService.hasState(criteriaMenuTaskIdKey(chatId));
    }

    public boolean isInPendingReviewState(long chatId) {
        return stateService.hasState(pendingReviewTaskIdKey(chatId));
    }

    public boolean isInPendingCommentState(long chatId) {
        return stateService.hasState(pendingCommentTaskIdKey(chatId));
    }

    public boolean isInPendingFileState(long chatId) {
        return stateService.hasState(pendingFileTaskIdKey(chatId));
    }

    public void startCreation(long chatId, Deadline deadline, User supervisor) {
        TaskCreationState state = new TaskCreationState();
        state.setDeadlineId(deadline.getId());
        state.setStep(0);
        stateService.saveState(taskCreationKey(chatId), state);
        sendTextMessage(chatId, "Введите название задачи:");
    }

    public void showForAction(long chatId, Task task, User user) {
        log.info("=== SHOW FOR ACTION ===");
        stateService.saveState(selectedTaskIdKey(chatId), task.getId());
        stateService.saveState(currentDeadlineIdKey(chatId), task.getDeadline().getId());
        showTaskDetails(chatId, task, user);
    }

    public void showTaskDetails(long chatId, Task task, User user) {
        log.info("=== SHOW TASK DETAILS ===");

        StringBuilder message = new StringBuilder();
        message.append("📌 Задача: ").append(task.getTitle()).append("\n\n");
        message.append("Описание: ").append(task.getDescription() != null ? task.getDescription() : "—").append("\n\n");

        String statusEmoji = getStatusEmoji(task.getStatus());
        message.append("📊 Статус: ").append(statusEmoji).append(" ").append(task.getStatus().getDisplayName()).append("\n\n");

        if (task.getCompletionCriteria() != null) {
            message.append("✅ Критерии:\n");
            String[] criteria = task.getCompletionCriteria().split("\n");
            Set<Integer> completedSet = new HashSet<>();
            if (task.getCriteriaStatus() != null && !task.getCriteriaStatus().isEmpty()) {
                for (String s : task.getCriteriaStatus().split(",")) {
                    try {
                        completedSet.add(Integer.parseInt(s.trim()));
                    } catch (NumberFormatException e) {}
                }
            }
            for (int i = 0; i < criteria.length; i++) {
                boolean isCompleted = completedSet.contains(i + 1);
                String checkMark = isCompleted ? "☑️" : "⬜️";
                message.append(String.format("%s %s\n", checkMark, criteria[i]));
            }
            message.append("\n");
        }

        if (task.getAttachedFiles() != null && !task.getAttachedFiles().isEmpty()) {
            message.append("📎 Прикрепленные файлы:\n");
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<AttachedFile> files = mapper.readValue(task.getAttachedFiles(),
                        new TypeReference<List<AttachedFile>>() {});
                for (int i = 0; i < files.size(); i++) {
                    AttachedFile file = files.get(i);
                    String fileSizeStr = formatFileSize(file.getFileSize());
                    message.append(String.format("   %d. %s (%s)\n", i + 1, file.getFileName(), fileSizeStr));
                }
                message.append("\n");
            } catch (Exception e) {
                log.error("Error parsing attached files: {}", e.getMessage());
            }
        }

        List<Comment> lastComments = commentRepository.findTop3ByTaskOrderByCreatedAtDesc(task);
        if (!lastComments.isEmpty()) {
            message.append("💬 Последние комментарии:\n");
            List<Comment> reversed = new ArrayList<>(lastComments);
            Collections.reverse(reversed);
            for (Comment comment : reversed) {
                String authorIcon = comment.getAuthor().getRole() == Role.SUPERVISOR ? "👨‍🏫" : "👨‍🎓";
                message.append(String.format("%s %s: %s\n", authorIcon, comment.getAuthor().getFullName(), comment.getText()));
                message.append(String.format("   📅 %s\n", comment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
            }
            message.append("\n");
        }

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message.toString());

        if (user.getRole() == Role.SUPERVISOR) {
            sendMessage.setReplyMarkup(botService.getNavigationHandler().getSupervisorTaskViewKeyboard());
        } else {
            sendMessage.setReplyMarkup(getStudentTaskKeyboard(task));
        }
        sendMessage.setParseMode("HTML");

        try {
            botService.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private ReplyKeyboardMarkup getStudentTaskKeyboard(Task task) {
        List<List<String>> buttons = new ArrayList<>();

        List<String> actionRow = new ArrayList<>();
        if (task.getStatus() == Status.CREATED) {
            actionRow.add("▶️ Взять в работу");
        } else if (task.getStatus() == Status.IN_PROGRESS) {
            actionRow.add("📤 Отправить на проверку");
        } else if (task.getStatus() == Status.NEED_IMPROVEMENT) {
            actionRow.add("🔄 Отправить на проверку");
        } else if (task.getStatus() == Status.WAITING_FOR_REVIEW) {
            actionRow.add("⏳ Ожидает проверки");
        } else if (task.getStatus() == Status.DONE) {
            actionRow.add("✅ Задача выполнена");
        } else if (task.getStatus() == Status.CANCELED) {
            actionRow.add("❌ Задача отменена");
        }

        if (!actionRow.isEmpty()) {
            buttons.add(actionRow);
        }

        if (task.getStatus() != Status.DONE && task.getStatus() != Status.CANCELED) {
            List<String> menuRow = new ArrayList<>();
            menuRow.add("💬 Комментарии");
            menuRow.add("📎 Файлы");
            buttons.add(menuRow);
        }

        List<String> backRow = new ArrayList<>();
        backRow.add("🔙 Назад к задачам");
        backRow.add("🏠Главное меню");
        buttons.add(backRow);

        return createKeyboard(buttons);
    }

    public void handleCreation(long chatId, String messageText) {
        TaskCreationState state = stateService.getState(taskCreationKey(chatId), TaskCreationState.class);
        if (state == null) {
            sendTextMessage(chatId, "❌ Сессия создания задачи истекла. Начните заново.");
            return;
        }

        if (messageText.equals("🏠Главное меню") || messageText.equals("🔙 Назад к дедлайнам")) {
            stateService.removeState(taskCreationKey(chatId));
            botService.getNavigationHandler().sendMainMenu(chatId,
                    botService.getTelegramService().getUserByChatId(chatId).orElse(null));
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                stateService.saveState(taskCreationKey(chatId), state);
                sendTextMessage(chatId, "Введите описание задачи:");
                break;
            case 1:
                state.setDescription(messageText);
                state.setStep(2);
                stateService.saveState(taskCreationKey(chatId), state);
                sendTextMessage(chatId, "Введите критерий выполнения задачи:\n\n" +
                        "Нужно добавить хотя бы один критерий.\n" +
                        "Критерий — это конкретный пункт, например:\n" +
                        "• «Провести обзор литературы»\n\n" +
                        "Вы можете добавить несколько критериев, вводя их по очереди.\n" +
                        "После добавления всех критериев введите 'готово'.");
                break;
            case 2:
                if (messageText.equals("🏠Главное меню") || messageText.equals("🔙 Назад к дедлайнам")) {
                    stateService.removeState(taskCreationKey(chatId));
                    botService.getTelegramService().getUserByChatId(chatId).ifPresent(u ->
                            botService.getNavigationHandler().sendMainMenu(chatId, u));
                    return;
                }
                if (messageText.equalsIgnoreCase("готово")) {
                    completeTaskCreation(chatId, state);
                } else {
                    if (state.getCompletionCriteriaList() == null) {
                        state.setCompletionCriteriaList(new ArrayList<>());
                    }
                    state.getCompletionCriteriaList().add(messageText);
                    stateService.saveState(taskCreationKey(chatId), state);
                    sendTextMessage(chatId, "✅ Критерий добавлен! Введите следующий (или 'готово'):");
                }
                break;
        }
    }

    public void handleEdit(long chatId, String messageText) {
        TaskEditState state = stateService.getState(taskEditKey(chatId), TaskEditState.class);
        if (state == null) {
            sendTextMessage(chatId, "❌ Сессия редактирования истекла. Начните заново.");
            return;
        }

        Task currentTask = botService.getTaskRepository().findById(state.getTaskId()).orElse(null);
        if (currentTask == null) {
            sendTextMessage(chatId, "❌ Задача не найдена.");
            stateService.removeState(taskEditKey(chatId));
            return;
        }

        if (messageText.equals("🏠Главное меню")) {
            stateService.removeState(taskEditKey(chatId));
            botService.getNavigationHandler().sendMainMenu(chatId, currentTask.getSupervisor());
            return;
        }

        if (messageText.equals("🔙 Назад") || messageText.equals("❌ Отменить")) {
            stateService.removeState(taskEditKey(chatId));
            showTaskDetails(chatId, currentTask, currentTask.getSupervisor());
            return;
        }

        if (messageText.equals("💬 Комментарии")) {
            stateService.removeState(taskEditKey(chatId));
            commentMenuHandler.showCommentMenu(chatId, currentTask, currentTask.getSupervisor());
            return;
        }

        if (messageText.equals("📎 Файлы")) {
            stateService.removeState(taskEditKey(chatId));
            fileMenuHandler.showFileMenu(chatId, currentTask, currentTask.getSupervisor());
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                stateService.saveState(taskEditKey(chatId), state);
                sendTextMessage(chatId, "Введите новое описание (или 'пропустить'):");
                break;
            case 1:
                if (messageText.equalsIgnoreCase("пропустить")) {
                    state.setDescription(null);
                } else {
                    state.setDescription(messageText);
                }
                state.setStep(2);
                if (state.getCriteriaList() == null) {
                    state.setCriteriaList(new ArrayList<>());
                }
                stateService.saveState(taskEditKey(chatId), state);
                sendTextMessage(chatId, "Введите новые критерии (по одному, 'готово' для завершения):");
                break;
            case 2:
                if (messageText.equalsIgnoreCase("готово")) {
                    completeTaskEdit(chatId, state);
                } else {
                    if (state.getCriteriaList() == null) {
                        state.setCriteriaList(new ArrayList<>());
                    }
                    state.getCriteriaList().add(messageText);
                    stateService.saveState(taskEditKey(chatId), state);
                    sendTextMessage(chatId, "✅ Критерий добавлен! Введите следующий (или 'готово'):");
                }
                break;
        }
    }

    public void handleReviewComment(long chatId, String messageText) {
        String taskIdStr = stateService.getState(reviewCommentKey(chatId), String.class);
        Task task = null;
        if (taskIdStr != null) {
            task = botService.getTaskRepository().findById(Long.parseLong(taskIdStr)).orElse(null);
        }

        if (task == null) {
            sendTextMessage(chatId, "❌ Задача не найдена.");
            stateService.removeState(reviewCommentKey(chatId));
            return;
        }

        final Task finalTask = task;

        task.setStatus(Status.NEED_IMPROVEMENT);
        task.setReviewComment(messageText);
        Task savedTask = botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Задача отправлена на доработку!");

        botService.getTelegramService().getChatIdByUser(finalTask.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "🔄 Задача \"" + finalTask.getTitle() + "\" требует доработки!\n\n" +
                    "Комментарий: " + messageText);
        });

        stateService.removeState(reviewCommentKey(chatId));
        stateService.saveState(selectedTaskIdKey(chatId), savedTask.getId());
        showTaskDetails(chatId, savedTask, task.getSupervisor());
    }

    public boolean handleTaskAction(long chatId, String messageText, User user) {
        Long taskId = stateService.getState(selectedTaskIdKey(chatId), Long.class);
        if (taskId == null) return false;
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) return false;

        if (user.getRole() == Role.SUPERVISOR) {
            switch (messageText) {
                case "✅ Принять":
                    showCriteriaMenu(chatId, task, user, "accept");
                    return true;
                case "🔄 Доработка":
                    startReviewComment(chatId, task, user);
                    return true;
                case "✏️ Редактировать":
                    startEdit(chatId, task, user);
                    return true;
                case "❌ Отменить":
                    cancelTask(chatId, task, user);
                    return true;
                case "💬 Комментарии":
                    commentMenuHandler.showCommentMenu(chatId, task, user);
                    return true;
                case "📎 Файлы":
                    fileMenuHandler.showFileMenu(chatId, task, user);
                    return true;
                case "🔙 Назад":
                    Long deadlineId = stateService.getState(currentDeadlineIdKey(chatId), Long.class);
                    if (deadlineId != null && deadlineHandler != null) {
                        Deadline deadline = botService.getDeadlineRepository().findByIdWithTasks(deadlineId).orElse(null);
                        if (deadline != null) {
                            deadlineHandler.showTasksMenu(chatId, deadline, user);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }

        if (user.getRole() == Role.STUDENT) {
            switch (messageText) {
                case "▶️ Взять в работу":
                    startTask(chatId, task, user);
                    return true;
                case "📤 Отправить на проверку":
                case "🔄 Отправить на проверку":
                    showSendForReviewMenu(chatId, task, user);
                    return true;
                case "💬 Комментарии":
                    commentMenuHandler.showCommentMenu(chatId, task, user);
                    return true;
                case "📎 Файлы":
                    fileMenuHandler.showFileMenu(chatId, task, user);
                    return true;
                case "🔙 Назад к задачам":
                    Long deadlineId = stateService.getState(currentDeadlineIdKey(chatId), Long.class);
                    if (deadlineId != null && deadlineHandler != null) {
                        Deadline deadline = botService.getDeadlineRepository().findByIdWithTasks(deadlineId).orElse(null);
                        if (deadline != null) {
                            deadlineHandler.showTasksMenu(chatId, deadline, user);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    public void handleCriteriaSelection(long chatId, String messageText, User user) {
        if (messageText.equals("🏠Главное меню") || messageText.equals("❌ Отменить")) {
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            botService.getNavigationHandler().sendMainMenu(chatId, user);
            return;
        }

        Long taskId = stateService.getState(criteriaMenuTaskIdKey(chatId), Long.class);
        if (taskId == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            return;
        }
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            return;
        }

        String action = stateService.getState(criteriaActionKey(chatId), String.class);
        if (action == null) action = "";

        int totalCount = task.getCompletionCriteria() != null ? task.getCompletionCriteria().split("\n").length : 0;
        int completedCount = 0;
        if (task.getCriteriaStatus() != null && !task.getCriteriaStatus().isEmpty()) {
            completedCount = task.getCriteriaStatus().split(",").length;
        }
        boolean allCompleted = (totalCount == 0 || completedCount == totalCount);

        if (messageText.equals("✅ Отметить все критерии")) {
            markAllCriteriaCompleted(chatId, task, user);
            showCriteriaMenu(chatId, task, user, action);
            return;
        }

        if (messageText.contains("Принять задачу") && "accept".equals(action)) {
            if (!allCompleted) {
                markAllCriteriaCompleted(chatId, task, user);
            }
            acceptTaskWithCriteria(chatId, task, user);
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            return;
        }

        if (messageText.contains("Отправить на доработку") && "rework".equals(action)) {
            startReviewComment(chatId, task, user);
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            return;
        }

        if (messageText.equals("🔙 Назад к задаче")) {
            stateService.removeState(criteriaMenuTaskIdKey(chatId));
            stateService.removeState(criteriaActionKey(chatId));
            showTaskDetails(chatId, task, user);
            return;
        }

        try {
            int criterionNumber = Integer.parseInt(messageText);
            markCriteriaCompleted(chatId, task, criterionNumber, user);
            showCriteriaMenu(chatId, task, user, action);
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "❌ Пожалуйста, введите номер критерия или используйте кнопки меню.");
        }
    }

    public void handlePendingReview(long chatId, String messageText, User student) {
        Long taskId = stateService.getState(pendingReviewTaskIdKey(chatId), Long.class);
        if (taskId == null) return;
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) return;

        final Task finalTask = task;

        switch (messageText) {
            case "💬 Добавить комментарий":
                stateService.saveState(pendingCommentTaskIdKey(chatId), finalTask.getId());
                sendTextMessage(chatId, "Введите комментарий к отправке:");
                break;
            case "📎 Добавить файл":
                stateService.saveState(pendingFileTaskIdKey(chatId), finalTask.getId());
                sendTextMessage(chatId, "📎 Отправьте файл для прикрепления:");
                break;
            case "✅ Отправить":
                finalTask.setStatus(Status.WAITING_FOR_REVIEW);
                botService.getTaskRepository().save(finalTask);
                sendTextMessage(chatId, "✅ Задача \"" + finalTask.getTitle() + "\" отправлена на проверку!");

                botService.getTelegramService().getChatIdByUser(finalTask.getSupervisor()).ifPresent(supervisorChatId -> {
                    sendTextMessage(supervisorChatId, "🔔 Студент " + finalTask.getStudent().getFullName() +
                            " отправил задачу \"" + finalTask.getTitle() + "\" на проверку!");
                    if (finalTask.getReviewComment() != null && !finalTask.getReviewComment().isEmpty()) {
                        sendTextMessage(supervisorChatId, "📝 Комментарий студента: " + finalTask.getReviewComment());
                    }
                });

                clearPendingReviewState(chatId);
                showTaskDetails(chatId, finalTask, student);
                break;
            case "❌ Отмена":
                clearPendingReviewState(chatId);
                showTaskDetails(chatId, finalTask, student);
                break;
            default:
                sendTextMessage(chatId, "Используйте кнопки меню.");
                break;
        }
    }

    public void handlePendingComment(long chatId, String text, User student) {
        Long taskId = stateService.getState(pendingCommentTaskIdKey(chatId), Long.class);
        if (taskId == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(pendingCommentTaskIdKey(chatId));
            return;
        }
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(pendingCommentTaskIdKey(chatId));
            return;
        }

        final Task finalTask = task;
        finalTask.setReviewComment(text);
        botService.getTaskRepository().save(finalTask);
        sendTextMessage(chatId, "✅ Комментарий добавлен!");
        stateService.removeState(pendingCommentTaskIdKey(chatId));
        showSendForReviewMenu(chatId, finalTask, student);
    }

    public void handlePendingFile(long chatId, org.telegram.telegrambots.meta.api.objects.Document document, User student) {
        Long taskId = stateService.getState(pendingFileTaskIdKey(chatId), Long.class);
        if (taskId == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(pendingFileTaskIdKey(chatId));
            return;
        }
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(pendingFileTaskIdKey(chatId));
            return;
        }

        final Task finalTask = task;

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<AttachedFile> files = new ArrayList<>();
            if (finalTask.getAttachedFiles() != null && !finalTask.getAttachedFiles().isEmpty()) {
                files = mapper.readValue(finalTask.getAttachedFiles(),
                        new TypeReference<List<AttachedFile>>() {});
            }

            AttachedFile newFile = new AttachedFile();
            newFile.setFileName(document.getFileName());
            newFile.setFileId(document.getFileId());
            newFile.setFileSize(document.getFileSize());
            newFile.setMimeType(document.getMimeType());
            newFile.setUploadedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            newFile.setAuthorId(student.getId());
            newFile.setAuthorName(student.getFullName());

            files.add(newFile);
            finalTask.setAttachedFiles(mapper.writeValueAsString(files));
            botService.getTaskRepository().save(finalTask);

            sendTextMessage(chatId, String.format("✅ Файл \"%s\" прикреплен!", document.getFileName()));
            stateService.removeState(pendingFileTaskIdKey(chatId));
            showSendForReviewMenu(chatId, finalTask, student);

        } catch (Exception e) {
            log.error("Error saving file: {}", e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при сохранении файла.");
        }
    }

    private void showSendForReviewMenu(long chatId, Task task, User student) {
        stateService.saveState(pendingReviewTaskIdKey(chatId), task.getId());
        sendMessageWithKeyboard(chatId, "📝 Что хотите сделать?", getPendingReviewKeyboard());
    }

    private void completeTaskCreation(long chatId, TaskCreationState state) {
        if (state.getCompletionCriteriaList() == null || state.getCompletionCriteriaList().isEmpty()) {
            sendTextMessage(chatId, "❌ Добавьте хотя бы один критерий.");
            return;
        }

        StringBuilder criteriaBuilder = new StringBuilder();
        for (int i = 0; i < state.getCompletionCriteriaList().size(); i++) {
            criteriaBuilder.append(i + 1).append(". ").append(state.getCompletionCriteriaList().get(i));
            if (i < state.getCompletionCriteriaList().size() - 1) {
                criteriaBuilder.append("\n");
            }
        }

        Task task = new Task();
        task.setTitle(state.getTitle());
        task.setDescription(state.getDescription());
        task.setCompletionCriteria(criteriaBuilder.toString());
        task.setDeadline(botService.getDeadlineRepository().findById(state.getDeadlineId()).orElse(null));
        if (task.getDeadline() != null) {
            task.setStudent(task.getDeadline().getStudent());
        }

        Optional<User> supervisorOpt = botService.getTelegramService().getUserByChatId(chatId);
        supervisorOpt.ifPresent(task::setSupervisor);

        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Задача создана!");
        stateService.removeState(taskCreationKey(chatId));
    }

    private void completeTaskEdit(long chatId, TaskEditState state) {
        Task task = botService.getTaskRepository().findById(state.getTaskId()).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Задача не найдена.");
            stateService.removeState(taskEditKey(chatId));
            return;
        }

        if (state.getCriteriaList() != null && !state.getCriteriaList().isEmpty()) {
            StringBuilder criteriaBuilder = new StringBuilder();
            for (int i = 0; i < state.getCriteriaList().size(); i++) {
                criteriaBuilder.append(i + 1).append(". ").append(state.getCriteriaList().get(i));
                if (i < state.getCriteriaList().size() - 1) {
                    criteriaBuilder.append("\n");
                }
            }
            task.setCompletionCriteria(criteriaBuilder.toString());
        }
        task.setTitle(state.getTitle());
        if (state.getDescription() != null) {
            task.setDescription(state.getDescription());
        }

        Task savedTask = botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Задача обновлена!");
        stateService.removeState(taskEditKey(chatId));
        stateService.saveState(selectedTaskIdKey(chatId), savedTask.getId());
        showTaskDetails(chatId, savedTask, savedTask.getSupervisor());
    }

    private void startReviewComment(long chatId, Task task, User supervisor) {
        stateService.saveState(reviewCommentKey(chatId), task.getId().toString());
        sendTextMessage(chatId, "Введите комментарий о доработке:");
    }

    private void cancelTask(long chatId, Task task, User supervisor) {
        task.setStatus(Status.CANCELED);
        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "❌ Задача отменена!");

        botService.getTelegramService().getChatIdByUser(task.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "❌ Задача \"" + task.getTitle() + "\" отменена.");
        });

        stateService.removeState(selectedTaskIdKey(chatId));
    }

    private void startEdit(long chatId, Task task, User supervisor) {
        TaskEditState state = new TaskEditState();
        state.setTaskId(task.getId());
        state.setStep(0);
        state.setCriteriaList(new ArrayList<>());
        stateService.saveState(taskEditKey(chatId), state);
        sendTextMessage(chatId, "Введите новое название задачи:");
    }

    private void startTask(long chatId, Task task, User student) {
        task.setStatus(Status.IN_PROGRESS);
        botService.getTaskRepository().save(task);
        sendTextMessage(chatId, "▶️ Задача \"" + task.getTitle() + "\" взята в работу!");
    }

    private void showCriteriaMenu(long chatId, Task task, User user, String action) {
        stateService.saveState(criteriaMenuTaskIdKey(chatId), task.getId());
        stateService.saveState(criteriaActionKey(chatId), action);

        StringBuilder message = new StringBuilder();
        message.append("📋 Отметьте выполненные критерии:\n\n");

        int completedCount = 0;
        int totalCount = 0;

        if (task.getCompletionCriteria() != null) {
            String[] criteria = task.getCompletionCriteria().split("\n");
            totalCount = criteria.length;

            Set<Integer> completedSet = new HashSet<>();
            if (task.getCriteriaStatus() != null && !task.getCriteriaStatus().isEmpty()) {
                for (String s : task.getCriteriaStatus().split(",")) {
                    try {
                        completedSet.add(Integer.parseInt(s.trim()));
                    } catch (NumberFormatException e) {}
                }
            }
            completedCount = completedSet.size();

            for (int i = 0; i < criteria.length; i++) {
                boolean isCompleted = completedSet.contains(i + 1);
                String checkMark = isCompleted ? "☑️" : "⬜️";
                message.append(String.format("%s %d. %s\n", checkMark, i + 1, criteria[i]));
            }

            message.append("\n✅ Отмечено: ").append(completedCount).append(" из ").append(totalCount);
        } else {
            message.append("✅ Нет критериев для оценки.");
        }

        message.append("\n\n• Введите номер критерия, чтобы отметить его по отдельности");
        message.append("\n• Или нажмите кнопку ниже для массовой отметки.");

        sendMessageWithKeyboard(chatId, message.toString(), getCriteriaMenuKeyboard(action, completedCount, totalCount));
    }

    private ReplyKeyboardMarkup getCriteriaMenuKeyboard(String action, int completedCount, int totalCount) {
        List<List<String>> buttons = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add("✅ Отметить все критерии");
        buttons.add(row1);

        if ("accept".equals(action)) {
            List<String> row2 = new ArrayList<>();
            if (totalCount == 0 || completedCount == totalCount) {
                row2.add("✅ Принять задачу");
            } else {
                row2.add("✅ Принять задачу (отметить все критерии)");
            }
            buttons.add(row2);
        } else {
            List<String> row2 = new ArrayList<>();
            if (totalCount == 0 || completedCount == totalCount) {
                row2.add("🔄 Отправить на доработку");
            } else {
                row2.add("🔄 Отправить на доработку (отметьте все критерии)");
            }
            buttons.add(row2);
        }

        List<String> row3 = new ArrayList<>();
        row3.add("🔙 Назад к задаче");
        buttons.add(row3);

        return createKeyboard(buttons);
    }

    private ReplyKeyboardMarkup getPendingReviewKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("💬 Добавить комментарий");
        row1.add("📎 Добавить файл");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("✅ Отправить");
        row2.add("❌ Отмена");
        buttons.add(row2);
        return createKeyboard(buttons);
    }

    public void clearPendingReviewState(long chatId) {
        stateService.removeState(pendingReviewTaskIdKey(chatId));
        stateService.removeState(pendingCommentTaskIdKey(chatId));
        stateService.removeState(pendingFileTaskIdKey(chatId));
    }

    private void markCriteriaCompleted(long chatId, Task task, int criterionNumber, User supervisor) {
        String[] criteria = task.getCompletionCriteria().split("\n");
        if (criterionNumber < 1 || criterionNumber > criteria.length) {
            sendTextMessage(chatId, "❌ Неверный номер критерия.");
            return;
        }

        Set<Integer> completedSet = new HashSet<>();
        if (task.getCriteriaStatus() != null && !task.getCriteriaStatus().isEmpty()) {
            for (String s : task.getCriteriaStatus().split(",")) {
                try {
                    completedSet.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException e) {}
            }
        }

        if (completedSet.contains(criterionNumber)) {
            sendTextMessage(chatId, "⚠️ Критерий " + criterionNumber + " уже отмечен!");
            return;
        }

        completedSet.add(criterionNumber);

        StringBuilder newStatus = new StringBuilder();
        for (Integer num : completedSet) {
            if (newStatus.length() > 0) newStatus.append(",");
            newStatus.append(num);
        }

        task.setCriteriaStatus(newStatus.toString());
        Task savedTask = botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Критерий " + criterionNumber + " отмечен!");
        stateService.saveState(selectedTaskIdKey(chatId), savedTask.getId());
    }

    private void markAllCriteriaCompleted(long chatId, Task task, User supervisor) {
        if (task.getCompletionCriteria() == null) {
            sendTextMessage(chatId, "❌ Нет критериев для отметки.");
            return;
        }

        String[] criteria = task.getCompletionCriteria().split("\n");
        Set<Integer> completedSet = new HashSet<>();

        for (int i = 1; i <= criteria.length; i++) {
            completedSet.add(i);
        }

        StringBuilder newStatus = new StringBuilder();
        for (Integer num : completedSet) {
            if (newStatus.length() > 0) newStatus.append(",");
            newStatus.append(num);
        }

        task.setCriteriaStatus(newStatus.toString());
        Task savedTask = botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Все " + criteria.length + " критериев отмечены как выполненные!");
        stateService.saveState(selectedTaskIdKey(chatId), savedTask.getId());
    }

    private void acceptTaskWithCriteria(long chatId, Task task, User supervisor) {
        task.setStatus(Status.DONE);
        botService.getTaskRepository().save(task);
        sendTextMessage(chatId, "✅ Задача принята!");

        stateService.saveState(selectedTaskIdKey(chatId), task.getId());
        stateService.saveState(currentDeadlineIdKey(chatId), task.getDeadline().getId());

        botService.getTelegramService().getChatIdByUser(task.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "🎉 Задача \"" + task.getTitle() + "\" принята руководителем!");
        });

        showTaskDetails(chatId, task, supervisor);
    }

    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    public Task getCurrentTask(long chatId) {
        Long taskId = stateService.getState(selectedTaskIdKey(chatId), Long.class);
        if (taskId == null) return null;
        return botService.getTaskRepository().findById(taskId).orElse(null);
    }
}