package ru.skillfactory.vkrbot.service;

import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;
import ru.skillfactory.vkrbot.handler.*;
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

    private final DeadlineHandler deadlineHandler;
    private final TaskHandler taskHandler;
    private final StudentHandler studentHandler;
    private final NavigationHandler navigationHandler;
    private final UserHandler userHandler;
    private final NotificationHandler notificationHandler;
    private final CommentMenuHandler commentMenuHandler;
    private final FileMenuHandler fileMenuHandler;

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
        deadlineHandler.setBotService(this);
        taskHandler.setBotService(this);
        studentHandler.setBotService(this);
        navigationHandler.setBotService(this);
        userHandler.setBotService(this);
        notificationHandler.setBotService(this);
        commentMenuHandler.setBotService(this);
        fileMenuHandler.setBotService(this);

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
            var document = update.getMessage().getDocument();

            try {
                if (fileMenuHandler.isInFileUploadState(chatId)) {
                    var userOpt = telegramService.getUserByChatId(chatId);
                    if (userOpt.isPresent()) {
                        fileMenuHandler.handleFileUpload(chatId, document, userOpt.get());
                    } else {
                        navigationHandler.sendTextMessage(chatId, "❌ Пользователь не найден.");
                    }
                    return;
                }
            } catch (Exception e) {
                log.error("Error processing file upload: {}", e.getMessage());
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка при загрузке файла.");
            }
            return;
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
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
                    } else {
                        navigationHandler.sendTextMessage(chatId, "❌ Пользователь не найден.");
                    }
                    return;
                }
            } catch (Exception e) {
                log.error("Error processing photo upload: {}", e.getMessage());
                navigationHandler.sendTextMessage(chatId, "❌ Ошибка при загрузке фото.");
            }
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

            if (fileMenuHandler.isInDeleteFileState(chatId)) {
                log.info("=== IN DELETE FILE STATE ===");
                fileMenuHandler.confirmDeleteFile(chatId, messageText, user);
                return;
            }

            if (deadlineHandler.isInCreationState(chatId)) {
                deadlineHandler.handleCreation(chatId, messageText);
                return;
            }

            if (taskHandler.isInCriteriaMenu(chatId)) {
                taskHandler.handleCriteriaSelection(chatId, messageText, user);
                return;
            }

            if (taskHandler.isInCreationState(chatId)) {
                taskHandler.handleCreation(chatId, messageText);
                return;
            }

            if (taskHandler.isInEditState(chatId)) {
                taskHandler.handleEdit(chatId, messageText);
                return;
            }

            if (taskHandler.isInReviewCommentState(chatId)) {
                taskHandler.handleReviewComment(chatId, messageText);
                return;
            }

            if (commentMenuHandler.isInCommentMenu(chatId)) {
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
                        commentMenuHandler.handleDeleteComment(chatId, commentNumber, user);
                    } catch (NumberFormatException e) {
                        commentMenuHandler.handleAddCommentText(chatId, messageText, user);
                    }
                }
                return;
            }

            if (fileMenuHandler.isInFileMenu(chatId)) {
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
                        fileMenuHandler.handleDownloadFile(chatId, fileNumber, user);
                    } catch (NumberFormatException e) {
                    }
                }
                return;
            }

            if (commentMenuHandler.isInAddCommentState(chatId)) {
                commentMenuHandler.handleAddCommentText(chatId, messageText, user);
                return;
            }

            if (fileMenuHandler.isInFileUploadState(chatId)) {
                navigationHandler.sendTextMessage(chatId, "📎 Пожалуйста, отправьте файл.");
                return;
            }

            if (fileMenuHandler.isInDeleteFileState(chatId)) {
                fileMenuHandler.confirmDeleteFile(chatId, messageText, user);
                return;
            }

            if (telegramService.isChatConnected(chatId)) {
                handleAuthorizedUser(chatId, messageText);
            } else {
                userHandler.handleUnauthorized(chatId, messageText, firstName);
            }
        } catch (Exception e) {
            log.error("Error processing message from chat {}: {}", chatId, e.getMessage());
            navigationHandler.sendTextMessage(chatId, "❌ Произошла ошибка. Пожалуйста, попробуйте позже.");
        }
    }

    private void handleAuthorizedUser(long chatId, String messageText) {
        try {
            var userOpt = telegramService.getUserByChatId(chatId);
            if (userOpt.isEmpty()) {
                navigationHandler.sendTextMessage(chatId, "❌ Сессия устарела.");
                return;
            }

            User user = userOpt.get();

            if (!user.isEnabled()) {
                navigationHandler.sendTextMessage(chatId, "❌ Ваш аккаунт заблокирован.");
                telegramService.disconnectChat(chatId);
                return;
            }

            if (messageText.equals("📋 Мои студенты")) {
                if (user.getRole() == Role.SUPERVISOR) {
                    studentHandler.showStudentsList(chatId, user);
                } else {
                    navigationHandler.sendTextMessage(chatId, "❌ Эта функция доступна только научным руководителям.");
                }
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

            if (studentHandler.handleStudentSelection(chatId, messageText, user)) {
                return;
            }

            if (deadlineHandler.handleDeadlineAction(chatId, messageText, user)) {
                return;
            }

            if (taskHandler.handleTaskAction(chatId, messageText, user)) {
                return;
            }

            if (studentHandler.handleStudentChoice(chatId, messageText, user)) {
                return;
            }

            navigationHandler.sendMainMenu(chatId, user);

        } catch (Exception e) {
            log.error("Error handling authorized user {}: {}", chatId, e.getMessage());
            navigationHandler.sendTextMessage(chatId, "❌ Произошла ошибка.");
        }
    }
}