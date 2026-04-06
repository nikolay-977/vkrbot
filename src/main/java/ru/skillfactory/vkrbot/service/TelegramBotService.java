package ru.skillfactory.vkrbot.service;

import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;
import ru.skillfactory.vkrbot.handler.*;
import ru.skillfactory.vkrbot.handler.DeadlineHandler;
import ru.skillfactory.vkrbot.handler.TaskHandler;
import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.DeadlineRepository;
import ru.skillfactory.vkrbot.repository.TaskRepository;
import ru.skillfactory.vkrbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
@Getter
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final TelegramService telegramService;
    private final DeadlineRepository deadlineRepository;
    private final TaskRepository taskRepository;

    public final Map<Long, Object> userStates = new HashMap<>();

    private final NavigationHandler navigationHandler;
    private final UserHandler userHandler;
    private final NotificationHandler notificationHandler;
    private final CommentMenuHandler commentMenuHandler;
    private final FileMenuHandler fileMenuHandler;
    private final DeadlineHandler deadlineHandler;
    private final TaskHandler taskHandler;
    private final SupervisorHandler supervisorHandler;
    private final StudentHandler studentHandler;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @PostConstruct
    public void init() {
        if (botToken == null || botToken.isEmpty()) {
            log.warn("Telegram bot token is not configured. Bot will not work.");
        } else {
            log.info("Telegram bot initialized with username: {}", botUsername);
            initializeHandlers();
        }
    }

    private void initializeHandlers() {
        navigationHandler.setBotService(this);
        userHandler.setBotService(this);
        notificationHandler.setBotService(this);
        commentMenuHandler.setBotService(this);
        fileMenuHandler.setBotService(this);

        deadlineHandler.setBotService(this);
        taskHandler.setBotService(this);

        studentHandler.setBotService(this);
        supervisorHandler.setBotService(this);
        studentHandler.setBotService(this);

        deadlineHandler.setTaskHandler(taskHandler);
        taskHandler.setDeadlineHandler(deadlineHandler);
        taskHandler.setCommentMenuHandler(commentMenuHandler);
        taskHandler.setFileMenuHandler(fileMenuHandler);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = update.hasMessage() ? update.getMessage().getChatId() : 0;

        if (update.hasMessage() && update.getMessage().hasDocument()) {
            handleDocumentUpload(chatId, update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            handlePhotoUpload(chatId, update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        String firstName = update.getMessage().getFrom().getFirstName();

        log.info("Received message from chat {}: {}", chatId, messageText);

        try {
            User user = null;
            if (telegramService.isChatConnected(chatId)) {
                var userOpt = telegramService.getUserByChatId(chatId);
                if (userOpt.isPresent()) {
                    user = userOpt.get();
                }
            }

            if (handleCommonStates(chatId, messageText, user)) {
                return;
            }

            if (telegramService.isChatConnected(chatId)) {
                handleAuthorizedUser(chatId, messageText, user);
            } else {
                userHandler.handleUnauthorized(chatId, messageText, firstName);
            }
        } catch (Exception e) {
            log.error("Error processing message from chat {}: {}", chatId, e.getMessage());
            navigationHandler.sendTextMessage(chatId, "❌ Произошла ошибка. Пожалуйста, попробуйте позже.");
        }
    }

    private void handleAuthorizedUser(long chatId, String messageText, User user) {
        if (!user.isEnabled()) {
            navigationHandler.sendTextMessage(chatId, "❌ Ваш аккаунт заблокирован.");
            telegramService.disconnectChat(chatId);
            return;
        }

        if (user.getRole() == Role.STUDENT) {
            handleStudentUser(chatId, messageText, user);
        } else if (user.getRole() == Role.SUPERVISOR) {
            handleSupervisorUser(chatId, messageText, user);
        } else {
            navigationHandler.sendMainMenu(chatId, user);
        }
    }

    private void handleDocumentUpload(long chatId, Update update) {
        var document = update.getMessage().getDocument();
        try {
            if (fileMenuHandler.isInFileUploadState(chatId)) {
                var userOpt = telegramService.getUserByChatId(chatId);
                if (userOpt.isPresent()) {
                    fileMenuHandler.handleFileUpload(chatId, document, userOpt.get());
                }
            } else if (taskHandler.isInPendingFileState(chatId)) {
                var userOpt = telegramService.getUserByChatId(chatId);
                if (userOpt.isPresent()) {
                    taskHandler.handlePendingFile(chatId, document, userOpt.get());
                }
            }
        } catch (Exception e) {
            log.error("Error processing file upload: {}", e.getMessage());
            navigationHandler.sendTextMessage(chatId, "❌ Ошибка при загрузке файла.");
        }
    }

    private void handlePhotoUpload(long chatId, Update update) {
        var photos = update.getMessage().getPhoto();
        var largestPhoto = photos.get(photos.size() - 1);
        try {
            if (fileMenuHandler.isInFileUploadState(chatId)) {
                var userOpt = telegramService.getUserByChatId(chatId);
                if (userOpt.isPresent()) {
                    org.telegram.telegrambots.meta.api.objects.Document photoDoc = new org.telegram.telegrambots.meta.api.objects.Document();
                    photoDoc.setFileId(largestPhoto.getFileId());
                    photoDoc.setFileName("photo_" + System.currentTimeMillis() + ".jpg");
                    photoDoc.setFileSize(Long.valueOf(largestPhoto.getFileSize()));
                    photoDoc.setMimeType("image/jpeg");
                    fileMenuHandler.handleFileUpload(chatId, photoDoc, userOpt.get());
                }
            }
        } catch (Exception e) {
            log.error("Error processing photo upload: {}", e.getMessage());
            navigationHandler.sendTextMessage(chatId, "❌ Ошибка при загрузке фото.");
        }
    }

    private void handleSupervisorUser(long chatId, String messageText, User user) {
        if (messageText.equals("📋 Мои студенты")) {
            supervisorHandler.showStudentsList(chatId, user);
            return;
        }
        if (messageText.equals("ℹ️ Мои данные")) {
            userHandler.showUserInfo(chatId, user);
            return;
        }
        if (messageText.equals("🚪 Выйти")) {
            userHandler.logout(chatId, user);
            return;
        }
        if (navigationHandler.handleNavigation(chatId, messageText, user)) {
            return;
        }
        if (supervisorHandler.handleStudentSelection(chatId, messageText, user)) {
            return;
        }
        if (deadlineHandler.handleDeadlineAction(chatId, messageText, user)) {
            return;
        }
        if (taskHandler.handleTaskAction(chatId, messageText, user)) {
            return;
        }
        if (supervisorHandler.handleStudentChoice(chatId, messageText, user)) {
            return;
        }
        navigationHandler.sendMainMenu(chatId, user);
    }

    private void handleStudentUser(long chatId, String messageText, User user) {
        switch (messageText) {
            case "🎓 Диплом":
                studentHandler.showDiplomaInfo(chatId, user);
                return;
            case "📋 Дедлайны":
                studentHandler.showStudentDeadlines(chatId, user);
                return;
            case "🔙 Назад к дедлайнам":
                studentHandler.showStudentDeadlines(chatId, user);
                return;
            case "🔙 Назад к задачам":
                Deadline deadline = studentHandler.getCurrentDeadline(chatId);
                if (deadline != null) {
                    studentHandler.showStudentTasksForDeadline(chatId, deadline, user);
                } else {
                    studentHandler.showStudentDeadlines(chatId, user);
                }
                return;
            case "ℹ️ Мои данные":
                userHandler.showUserInfo(chatId, user);
                return;
            case "👨‍🏫 Научный руководитель":
                studentHandler.showSupervisorInfo(chatId, user);
                return;
            case "🚪 Выйти":
                userHandler.logout(chatId, user);
                return;
        }

        if (navigationHandler.handleNavigation(chatId, messageText, user)) {
            return;
        }

        if (studentHandler.handleStudentDeadlineSelection(chatId, messageText, user)) {
            return;
        }
        if (studentHandler.handleStudentTaskSelection(chatId, messageText, user)) {
            return;
        }

        navigationHandler.sendMainMenu(chatId, user);
    }

    private boolean handleCommonStates(long chatId, String messageText, User user) {
        if (fileMenuHandler.isInDeleteFileState(chatId)) {
            fileMenuHandler.confirmDeleteFile(chatId, messageText, user);
            return true;
        }
        if (deadlineHandler.isInCreationState(chatId)) {
            deadlineHandler.handleCreation(chatId, messageText);
            return true;
        }
        if (taskHandler.isInCriteriaMenu(chatId)) {
            if (user != null) {
                taskHandler.handleCriteriaSelection(chatId, messageText, user);
            } else {
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка: пользователь не найден.");
            }
            return true;
        }
        if (taskHandler.isInCreationState(chatId)) {
            taskHandler.handleCreation(chatId, messageText);
            return true;
        }
        if (taskHandler.isInEditState(chatId)) {
            taskHandler.handleEdit(chatId, messageText);
            return true;
        }
        if (taskHandler.isInReviewCommentState(chatId)) {
            taskHandler.handleReviewComment(chatId, messageText);
            return true;
        }
        if (taskHandler.isInPendingReviewState(chatId)) {
            if (user != null) {
                taskHandler.handlePendingReview(chatId, messageText, user);
            } else {
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка: пользователь не найден.");
            }
            return true;
        }
        if (taskHandler.isInPendingCommentState(chatId)) {
            if (user != null) {
                taskHandler.handlePendingComment(chatId, messageText, user);
            } else {
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка: пользователь не найден.");
            }
            return true;
        }
        if (taskHandler.isInTaskView(chatId)) {
            if (user != null) {
                if (taskHandler.handleTaskAction(chatId, messageText, user)) {
                    return true;
                }
            }
        }
        if (commentMenuHandler.isInCommentMenu(chatId)) {
            handleCommentMenu(chatId, messageText, user);
            return true;
        }
        if (fileMenuHandler.isInFileMenu(chatId)) {
            handleFileMenu(chatId, messageText, user);
            return true;
        }
        if (commentMenuHandler.isInAddCommentState(chatId)) {
            if (user != null) {
                commentMenuHandler.handleAddCommentText(chatId, messageText, user);
            } else {
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка: пользователь не найден.");
            }
            return true;
        }
        if (fileMenuHandler.isInFileUploadState(chatId)) {
            navigationHandler.sendTextMessage(chatId, "📎 Пожалуйста, отправьте файл.");
            return true;
        }
        return false;
    }

    private void handleCommentMenu(long chatId, String messageText, User user) {
        if (messageText.equals("➕ Добавить комментарий")) {
            commentMenuHandler.startAddComment(chatId);
        } else if (messageText.equals("🔙 Назад к задаче")) {
            Task task = commentMenuHandler.getCurrentTask(chatId);
            commentMenuHandler.exitToTask(chatId);
            if (task != null && user != null) {
                taskHandler.showTaskDetails(chatId, task, user);
            }
        } else {
            try {
                int commentNumber = Integer.parseInt(messageText);
                if (user != null) {
                    commentMenuHandler.handleDeleteComment(chatId, commentNumber, user);
                }
            } catch (NumberFormatException e) {
                if (user != null) {
                    commentMenuHandler.handleAddCommentText(chatId, messageText, user);
                }
            }
        }
    }

    private void handleFileMenu(long chatId, String messageText, User user) {
        if (messageText.equals("📎 Добавить файл")) {
            Task task = fileMenuHandler.getCurrentTask(chatId);
            fileMenuHandler.startFileUpload(chatId, task);
        } else if (messageText.equals("🔙 Назад к задаче")) {
            Task task = fileMenuHandler.getCurrentTask(chatId);
            fileMenuHandler.exitToTask(chatId);
            if (task != null && user != null) {
                taskHandler.showTaskDetails(chatId, task, user);
            }
        } else if (messageText.toLowerCase().startsWith("удалить ")) {
            try {
                int fileNumber = Integer.parseInt(messageText.substring(8).trim());
                fileMenuHandler.startDeleteFile(chatId, fileNumber);
            } catch (NumberFormatException e) {
                navigationHandler.sendTextMessage(chatId, "❌ Неверный формат. Используйте: удалить 1");
            }
        } else {
            try {
                int fileNumber = Integer.parseInt(messageText);
                if (user != null) {
                    fileMenuHandler.handleDownloadFile(chatId, fileNumber, user);
                }
            } catch (NumberFormatException e) {
            }
        }
    }
}