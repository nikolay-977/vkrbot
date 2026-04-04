package ru.skillfactory.vkrbot.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.skillfactory.vkrbot.dto.AttachedFile;
import ru.skillfactory.vkrbot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.skillfactory.vkrbot.repository.CommentRepository;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class TaskHandler extends BaseHandler {

    private final Map<Long, TaskCreationState> taskCreationStates = new HashMap<>();
    private final Map<Long, Task> selectedTaskForAction = new HashMap<>();
    private final Map<Long, TaskEditState> taskEditStates = new HashMap<>();
    private final Map<Long, String> reviewCommentStates = new HashMap<>();
    private final Map<Long, Deadline> currentDeadlineCache = new HashMap<>();
    private final Map<Long, Task> addCommentState = new HashMap<>();
    private final Map<Long, Task> fileUploadState = new HashMap<>();
    private final Map<Long, Task> fileViewState = new HashMap<>();
    private final Map<Long, Task> criteriaMenuState = new HashMap<>();
    private final Map<Long, String> criteriaActionState = new HashMap<>();

    private final CommentRepository commentRepository;
    private final StudentHandler studentHandler;
    private CommentMenuHandler commentMenuHandler;
    private FileMenuHandler fileMenuHandler;

    private DeadlineHandler deadlineHandler;

    public TaskHandler(StudentHandler studentHandler, CommentRepository commentRepository,
                       CommentMenuHandler commentMenuHandler, FileMenuHandler fileMenuHandler) {
        this.studentHandler = studentHandler;
        this.commentRepository = commentRepository;
        this.commentMenuHandler = commentMenuHandler;
        this.fileMenuHandler = fileMenuHandler;
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

    public boolean isInCreationState(long chatId) {
        return taskCreationStates.containsKey(chatId);
    }

    public boolean isInEditState(long chatId) {
        return taskEditStates.containsKey(chatId);
    }

    public boolean isInReviewCommentState(long chatId) {
        return reviewCommentStates.containsKey(chatId);
    }

    public boolean isInAddCommentState(long chatId) {
        return addCommentState.containsKey(chatId);
    }

    public boolean isInFileUploadState(long chatId) {
        return fileUploadState.containsKey(chatId);
    }

    public boolean isInFileViewState(long chatId) {
        return fileViewState.containsKey(chatId);
    }

    public void handleCreation(long chatId, String messageText) {
        TaskCreationState state = taskCreationStates.get(chatId);

        if (messageText.equals("Главное меню") || messageText.equals("🔙 Назад к дедлайнам")) {
            taskCreationStates.remove(chatId);
            botService.getNavigationHandler().sendMainMenu(chatId,
                    botService.getTelegramService().getUserByChatId(chatId).orElse(null));
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                sendTextMessage(chatId, "Введите описание задачи:");
                break;
            case 1:
                state.setDescription(messageText);
                state.setStep(2);
                sendTextMessage(chatId, "Введите критерий (или 'готово'):");
                break;
            case 2:
                if (messageText.equalsIgnoreCase("готово")) {
                    completeTaskCreation(chatId, state);
                } else {
                    if (state.getCompletionCriteriaList() == null) {
                        state.setCompletionCriteriaList(new ArrayList<>());
                    }
                    state.getCompletionCriteriaList().add(messageText);
                    sendTextMessage(chatId, "✅ Критерий добавлен! Введите следующий (или 'готово'):");
                }
                break;
        }
    }

    public void handleEdit(long chatId, String messageText) {
        TaskEditState state = taskEditStates.get(chatId);
        Task task = state.getTask();

        if (messageText.equals("Главное меню")) {
            taskEditStates.remove(chatId);
            botService.getNavigationHandler().sendMainMenu(chatId,
                    botService.getTelegramService().getUserByChatId(chatId).orElse(null));
            return;
        }

        switch (state.getStep()) {
            case 0:
                state.setTitle(messageText);
                state.setStep(1);
                sendTextMessage(chatId, "Введите новое описание (или 'пропустить'):");
                break;
            case 1:
                if (!messageText.equalsIgnoreCase("пропустить")) {
                    state.setDescription(messageText);
                }
                state.setStep(2);
                sendTextMessage(chatId, "Введите новые критерии (по одному, 'готово' для завершения):");
                if (state.getCriteriaList() == null) {
                    state.setCriteriaList(new ArrayList<>());
                }
                break;
            case 2:
                if (messageText.equalsIgnoreCase("готово")) {
                    completeTaskEdit(chatId, state);
                } else {
                    state.getCriteriaList().add(messageText);
                    sendTextMessage(chatId, "✅ Критерий добавлен! Введите следующий (или 'готово'):");
                }
                break;
        }
    }

    public void handleReviewComment(long chatId, String messageText) {
        String taskIdStr = reviewCommentStates.get(chatId);
        Task task = botService.getTaskRepository().findById(Long.parseLong(taskIdStr)).orElse(null);

        if (task == null) {
            sendTextMessage(chatId, "❌ Задача не найдена.");
            reviewCommentStates.remove(chatId);
            return;
        }

        task.setStatus(Status.NEED_IMPROVEMENT);
        task.setReviewComment(messageText);
        Task savedTask = botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Задача отправлена на доработку!");

        botService.getTelegramService().getChatIdByUser(task.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "🔄 Задача \"" + task.getTitle() + "\" требует доработки!\n\n" +
                    "Комментарий: " + messageText);
        });

        reviewCommentStates.remove(chatId);
        selectedTaskForAction.put(chatId, savedTask);
        showTaskDetails(chatId, savedTask, task.getSupervisor());
    }

    public void startCreation(long chatId, Deadline deadline, User supervisor) {
        TaskCreationState state = new TaskCreationState();
        state.setDeadline(deadline);
        state.setStep(0);
        taskCreationStates.put(chatId, state);
        sendTextMessage(chatId, "Введите название задачи:");
    }

    public void showForAction(long chatId, Task task, User user) {
        log.info("=== SHOW FOR ACTION ===");
        selectedTaskForAction.put(chatId, task);
        currentDeadlineCache.put(chatId, task.getDeadline());
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
        sendMessage.setReplyMarkup(getTaskKeyboard(user.getRole(), task));
        sendMessage.setParseMode("HTML");

        try {
            botService.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private ReplyKeyboardMarkup getTaskKeyboard(Role role, Task task) {
        if (role == Role.SUPERVISOR) {
            return getSupervisorTaskKeyboard(task);
        } else {
            return getStudentTaskKeyboard(task);
        }
    }

    private ReplyKeyboardMarkup getSupervisorTaskKeyboard(Task task) {
        List<List<String>> buttons = new ArrayList<>();

        List<String> actionRow = new ArrayList<>();
        actionRow.add("✅ Принять");
        actionRow.add("🔄 Доработка");
        buttons.add(actionRow);

        List<String> editRow = new ArrayList<>();
        editRow.add("✏️ Редактировать");
        editRow.add("❌ Отменить");
        buttons.add(editRow);

        List<String> menuRow = new ArrayList<>();
        menuRow.add("💬 Комментарии");
        menuRow.add("📎 Файлы");
        buttons.add(menuRow);

        List<String> backRow = new ArrayList<>();
        backRow.add("🔙 Назад");
        backRow.add("Главное меню");
        buttons.add(backRow);

        return createKeyboard(buttons);
    }

    private ReplyKeyboardMarkup getStudentTaskKeyboard(Task task) {
        List<List<String>> buttons = new ArrayList<>();
        List<String> actionRow = new ArrayList<>();

        if (task.getStatus() == Status.CREATED) {
            actionRow.add("▶️ Взять в работу");
        } else if (task.getStatus() == Status.IN_PROGRESS || task.getStatus() == Status.NEED_IMPROVEMENT) {
            actionRow.add("📤 Отправить на проверку");
        }

        if (!actionRow.isEmpty()) {
            buttons.add(actionRow);
        }

        List<String> menuRow = new ArrayList<>();
        menuRow.add("💬 Комментарии");
        menuRow.add("📎 Файлы");
        buttons.add(menuRow);

        List<String> backRow = new ArrayList<>();
        backRow.add("🔙 Назад");
        backRow.add("Главное меню");
        buttons.add(backRow);

        return createKeyboard(buttons);
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
        task.setDeadline(state.getDeadline());
        task.setStudent(state.getDeadline().getStudent());

        Optional<User> supervisorOpt = botService.getTelegramService().getUserByChatId(chatId);
        supervisorOpt.ifPresent(task::setSupervisor);

        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "✅ Задача создана!");
        taskCreationStates.remove(chatId);
    }

    private void completeTaskEdit(long chatId, TaskEditState state) {
        Task task = state.getTask();

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
        taskEditStates.remove(chatId);
        selectedTaskForAction.put(chatId, savedTask);
        showTaskDetails(chatId, savedTask, savedTask.getSupervisor());
    }

    private void startReviewComment(long chatId, Task task, User supervisor) {
        reviewCommentStates.put(chatId, task.getId().toString());
        sendTextMessage(chatId, "Введите комментарий о доработке:");
    }

    private void sendForReview(long chatId, Task task, User student) {
        task.setStatus(Status.WAITING_FOR_REVIEW);
        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "📤 Задача отправлена на проверку!");

        botService.getTelegramService().getChatIdByUser(task.getSupervisor()).ifPresent(supervisorChatId -> {
            sendTextMessage(supervisorChatId, "🔔 Студент отправил задачу \"" + task.getTitle() + "\" на проверку!");
        });

        selectedTaskForAction.remove(chatId);
    }

    private void cancelTask(long chatId, Task task, User supervisor) {
        task.setStatus(Status.CANCELED);
        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "❌ Задача отменена!");

        botService.getTelegramService().getChatIdByUser(task.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "❌ Задача \"" + task.getTitle() + "\" отменена.");
        });

        selectedTaskForAction.remove(chatId);
    }

    private void startTask(long chatId, Task task, User student) {
        task.setStatus(Status.IN_PROGRESS);
        botService.getTaskRepository().save(task);

        sendTextMessage(chatId, "▶️ Задача взята в работу!");
        selectedTaskForAction.remove(chatId);
    }

    private void startEdit(long chatId, Task task, User supervisor) {
        TaskEditState state = new TaskEditState();
        state.setTask(task);
        state.setStep(0);
        taskEditStates.put(chatId, state);
        sendTextMessage(chatId, "Введите новое название задачи:");
    }

    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }


    public boolean isInCriteriaMenu(long chatId) {
        return criteriaMenuState.containsKey(chatId);
    }

    public boolean handleTaskAction(long chatId, String messageText, User user) {
        if (!selectedTaskForAction.containsKey(chatId)) {
            return false;
        }

        Task task = selectedTaskForAction.get(chatId);

        if (messageText.equals("🔙 Назад")) {
            log.info("=== BACK TO TASKS LIST ===");
            selectedTaskForAction.remove(chatId);
            Deadline deadline = currentDeadlineCache.get(chatId);
            if (deadline != null && deadlineHandler != null) {
                deadlineHandler.showTasksMenu(chatId, deadline, user);
            }
            return true;
        }

        if (messageText.equals("💬 Комментарии")) {
            commentMenuHandler.showCommentMenu(chatId, task, user);
            return true;
        }

        if (messageText.equals("📎 Файлы")) {
            fileMenuHandler.showFileMenu(chatId, task, user);
            return true;
        }

        if (messageText.equals("🔙 Назад к задаче")) {
            showTaskDetails(chatId, task, user);
            return true;
        }

        if (messageText.equals("✅ Принять")) {
            showCriteriaMenu(chatId, task, user, "accept");
            return true;
        }

        if (messageText.equals("🔄 Доработка")) {
            showCriteriaMenu(chatId, task, user, "rework");
            return true;
        }

        if (messageText.equals("✏️ Редактировать")) {
            startEdit(chatId, task, user);
            return true;
        }

        if (messageText.equals("❌ Отменить")) {
            cancelTask(chatId, task, user);
            return true;
        }

        if (messageText.equals("▶️ Взять в работу")) {
            startTask(chatId, task, user);
            return true;
        }

        if (messageText.equals("📤 Отправить на проверку")) {
            sendForReview(chatId, task, user);
            return true;
        }

        return false;
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
        selectedTaskForAction.put(chatId, savedTask);
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
        selectedTaskForAction.put(chatId, savedTask);
    }

    private void acceptTaskWithCriteria(long chatId, Task task, User supervisor) {
        task.setStatus(Status.DONE);
        botService.getTaskRepository().save(task);
        sendTextMessage(chatId, "✅ Задача принята!");

        selectedTaskForAction.put(chatId, task);
        currentDeadlineCache.put(chatId, task.getDeadline());

        botService.getTelegramService().getChatIdByUser(task.getStudent()).ifPresent(studentChatId -> {
            sendTextMessage(studentChatId, "🎉 Задача \"" + task.getTitle() + "\" принята руководителем!");
        });

        showTaskDetails(chatId, task, supervisor);
    }

    private ReplyKeyboardMarkup getCriteriaMenuKeyboard(String action, int completedCount, int totalCount) {
        List<List<String>> buttons = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add("✅ Отметить все критерии");
        buttons.add(row1);

        if ("accept".equals(action)) {
            List<String> row2 = new ArrayList<>();
            if (totalCount == 0 || completedCount == totalCount) {
                row2.add("✅ Завершить и принять задачу");
            } else {
                row2.add("❌ Завершить и принять задачу (отметьте все критерии)");
            }
            buttons.add(row2);
        } else {
            List<String> row2 = new ArrayList<>();
            if (totalCount == 0 || completedCount == totalCount) {
                row2.add("🔄 Завершить и отправить на доработку");
            } else {
                row2.add("❌ Завершить и отправить на доработку (отметьте все критерии)");
            }
            buttons.add(row2);
        }

        List<String> row3 = new ArrayList<>();
        row3.add("🔙 Назад к задаче");
        buttons.add(row3);

        return createKeyboard(buttons);
    }

    private void showCriteriaMenu(long chatId, Task task, User user, String action) {
        criteriaMenuState.put(chatId, task);
        criteriaActionState.put(chatId, action);

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

    public void handleCriteriaSelection(long chatId, String messageText, User user) {
        Task task = criteriaMenuState.get(chatId);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            criteriaMenuState.remove(chatId);
            criteriaActionState.remove(chatId);
            return;
        }

        String action = criteriaActionState.get(chatId);

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

        if (messageText.equals("✅ Завершить и принять задачу") && "accept".equals(action)) {
            if (!allCompleted) {
                sendTextMessage(chatId, "❌ Не все критерии отмечены! Отмечено " + completedCount + " из " + totalCount + ".\n\nОтметьте все критерии перед принятием задачи.");
                return;
            }
            acceptTaskWithCriteria(chatId, task, user);
            criteriaMenuState.remove(chatId);
            criteriaActionState.remove(chatId);
            return;
        }

        if (messageText.equals("🔄 Завершить и отправить на доработку") && "rework".equals(action)) {
            if (!allCompleted) {
                sendTextMessage(chatId, "❌ Не все критерии отмечены! Отмечено " + completedCount + " из " + totalCount + ".\n\nОтметьте все критерии перед отправкой на доработку.");
                return;
            }
            startReviewComment(chatId, task, user);
            criteriaMenuState.remove(chatId);
            criteriaActionState.remove(chatId);
            return;
        }

        if (messageText.equals("🔙 Назад к задаче")) {
            criteriaMenuState.remove(chatId);
            criteriaActionState.remove(chatId);
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
}