package ru.skillfactory.vkrbot.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.meta.api.objects.Document;
import ru.skillfactory.vkrbot.dto.AttachedFile;
import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.CommentRepository;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class FileMenuHandler extends BaseHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommentRepository commentRepository;
    private final StateService stateService;

    public FileMenuHandler(CommentRepository commentRepository, StateService stateService) {
        this.commentRepository = commentRepository;
        this.stateService = stateService;
    }

    private String fileMenuTaskIdKey(long chatId) {
        return "fileMenuTaskId:" + chatId;
    }

    private String fileUploadTaskIdKey(long chatId) {
        return "fileUploadTaskId:" + chatId;
    }

    private String deleteFileKey(long chatId) {
        return "deleteFile:" + chatId;
    }

    public boolean isInFileMenu(long chatId) {
        return stateService.hasState(fileMenuTaskIdKey(chatId));
    }

    public boolean isInFileUploadState(long chatId) {
        return stateService.hasState(fileUploadTaskIdKey(chatId));
    }

    public boolean isInDeleteFileState(long chatId) {
        return stateService.hasState(deleteFileKey(chatId));
    }

    public Task getCurrentTask(long chatId) {
        Long taskId = stateService.getState(fileMenuTaskIdKey(chatId), Long.class);
        if (taskId == null) return null;
        return botService.getTaskRepository().findById(taskId).orElse(null);
    }

    public void showFileMenu(long chatId, Task task, User user) {
        stateService.saveState(fileMenuTaskIdKey(chatId), task.getId());

        StringBuilder message = new StringBuilder();
        message.append("📎 Файлы, прикрепленные к задаче:\n\n");
        message.append("📌 ").append(task.getTitle()).append("\n\n");

        if (task.getAttachedFiles() == null || task.getAttachedFiles().isEmpty()) {
            message.append("Нет прикрепленных файлов.\n");
        } else {
            try {
                List<AttachedFile> files = mapper.readValue(task.getAttachedFiles(),
                        new TypeReference<List<AttachedFile>>() {});
                for (int i = 0; i < files.size(); i++) {
                    AttachedFile file = files.get(i);
                    String fileSizeStr = formatFileSize(file.getFileSize());
                    String deleteBtn = " ❌";
                    message.append(String.format("%d. %s (%s)%s\n", i + 1, file.getFileName(), fileSizeStr, deleteBtn));
                    message.append(String.format("   📅 Загружен: %s\n", file.getUploadedAt()));
                    message.append("\n");
                }
            } catch (Exception e) {
                log.error("Error parsing files: {}", e.getMessage());
            }
        }

        message.append("\n• Введите номер файла для скачивания");
        message.append("\n• Введите 'удалить X' для удаления файла (например: удалить 1)");
        message.append("\n• Или нажмите кнопку ниже для добавления нового файла.");

        sendMessageWithKeyboard(chatId, message.toString(), getFileMenuKeyboard());
    }

    private ReplyKeyboardMarkup getFileMenuKeyboard() {
        List<List<String>> buttons = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add("📎 Добавить файл");
        buttons.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add("🔙 Назад к задаче");
        row2.add("🏠Главное меню");
        buttons.add(row2);

        return createKeyboard(buttons);
    }

    public void startFileUpload(long chatId, Task task) {
        stateService.saveState(fileUploadTaskIdKey(chatId), task.getId());
        sendTextMessage(chatId, "📎 Отправьте файл, который хотите прикрепить к задаче.\n\nПоддерживаются любые форматы.");
    }

    public void handleFileUpload(long chatId, Document document, User user) {
        Long taskId = stateService.getState(fileUploadTaskIdKey(chatId), Long.class);
        if (taskId == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена. Возможно, сессия истекла.");
            stateService.removeState(fileUploadTaskIdKey(chatId));
            return;
        }

        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(fileUploadTaskIdKey(chatId));
            return;
        }

        log.info("Handling file upload for task: {}, user: {}, fileName: {}", task.getId(), user.getFullName(), document.getFileName());

        try {
            List<AttachedFile> files = new ArrayList<>();
            if (task.getAttachedFiles() != null && !task.getAttachedFiles().isEmpty()) {
                files = mapper.readValue(task.getAttachedFiles(),
                        new TypeReference<List<AttachedFile>>() {});
                log.info("Existing files count: {}", files.size());
            }

            AttachedFile newFile = new AttachedFile();
            newFile.setFileName(document.getFileName());
            newFile.setFileId(document.getFileId());
            newFile.setFileSize(document.getFileSize());
            newFile.setMimeType(document.getMimeType());
            newFile.setUploadedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            newFile.setAuthorId(user.getId());
            newFile.setAuthorName(user.getFullName());

            files.add(newFile);
            task.setAttachedFiles(mapper.writeValueAsString(files));
            Task savedTask = botService.getTaskRepository().save(task);

            log.info("File saved successfully, total files: {}", files.size());

            Comment comment = new Comment();
            comment.setText(String.format("📎 Прикреплен файл: %s", document.getFileName()));
            comment.setTask(savedTask);
            comment.setAuthor(user);
            commentRepository.save(comment);

            sendTextMessage(chatId, String.format("✅ Файл \"%s\" успешно прикреплен!", document.getFileName()));
            stateService.removeState(fileUploadTaskIdKey(chatId));
            stateService.saveState(fileMenuTaskIdKey(chatId), savedTask.getId());
            showFileMenu(chatId, savedTask, user);

        } catch (Exception e) {
            log.error("Error saving file: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ошибка при сохранении файла: " + e.getMessage());
            stateService.removeState(fileUploadTaskIdKey(chatId));
        }
    }

    public void handleDownloadFile(long chatId, int fileNumber, User user) {
        Task task = getCurrentTask(chatId);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            return;
        }

        try {
            List<AttachedFile> files = mapper.readValue(task.getAttachedFiles(),
                    new TypeReference<List<AttachedFile>>() {});

            if (fileNumber < 1 || fileNumber > files.size()) {
                sendTextMessage(chatId, "❌ Неверный номер файла.");
                return;
            }

            AttachedFile file = files.get(fileNumber - 1);

            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setDocument(new InputFile(file.getFileId()));
            sendDocument.setCaption(String.format("📎 %s (загружен: %s)", file.getFileName(), file.getUploadedAt()));

            botService.execute(sendDocument);

        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при скачивании файла.");
        }
    }

    public void startDeleteFile(long chatId, int fileNumber) {
        log.info("startDeleteFile called for chat {}, fileNumber {}", chatId, fileNumber);
        stateService.saveState(deleteFileKey(chatId), fileNumber);
        sendTextMessage(chatId, String.format("⚠️ Вы уверены, что хотите удалить файл №%d?\n\nОтправьте 'ДА' для подтверждения или 'НЕТ' для отмены.", fileNumber));
    }

    public void confirmDeleteFile(long chatId, String confirm, User user) {
        Integer fileNumber = stateService.getState(deleteFileKey(chatId), Integer.class);
        if (fileNumber == null) {
            sendTextMessage(chatId, "❌ Операция удаления не найдена.");
            stateService.removeState(deleteFileKey(chatId));
            return;
        }

        if (confirm.equalsIgnoreCase("НЕТ")) {
            sendTextMessage(chatId, "❌ Удаление отменено.");
            stateService.removeState(deleteFileKey(chatId));
            Task task = getCurrentTask(chatId);
            if (task != null) {
                showFileMenu(chatId, task, user);
            }
            return;
        }

        if (!confirm.equalsIgnoreCase("ДА")) {
            sendTextMessage(chatId, "❌ Пожалуйста, отправьте 'ДА' для подтверждения или 'НЕТ' для отмены.");
            return;
        }

        Task task = getCurrentTask(chatId);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(deleteFileKey(chatId));
            return;
        }

        task = botService.getTaskRepository().findById(task.getId()).orElse(task);

        if (task.getAttachedFiles() == null || task.getAttachedFiles().isEmpty()) {
            sendTextMessage(chatId, "❌ Файлы не найдены.");
            stateService.removeState(deleteFileKey(chatId));
            showFileMenu(chatId, task, user);
            return;
        }

        try {
            List<AttachedFile> files = mapper.readValue(task.getAttachedFiles(),
                    new TypeReference<List<AttachedFile>>() {});

            if (fileNumber < 1 || fileNumber > files.size()) {
                sendTextMessage(chatId, "❌ Неверный номер файла.");
                stateService.removeState(deleteFileKey(chatId));
                return;
            }

            AttachedFile file = files.get(fileNumber - 1);

            log.info("Attempting to delete file: {}, user role: {}, file authorId: {}",
                    file.getFileName(), user.getRole(), file.getAuthorId());

            if (user.getRole() != Role.SUPERVISOR) {
                if (file.getAuthorId() == null || !file.getAuthorId().equals(user.getId())) {
                    sendTextMessage(chatId, "❌ Вы можете удалять только свои файлы.");
                    stateService.removeState(deleteFileKey(chatId));
                    return;
                }
            }

            files.remove(fileNumber - 1);

            if (files.isEmpty()) {
                task.setAttachedFiles(null);
            } else {
                task.setAttachedFiles(mapper.writeValueAsString(files));
            }

            Task savedTask = botService.getTaskRepository().save(task);
            log.info("File deleted successfully, remaining files: {}", files.size());

            Comment comment = new Comment();
            comment.setText(String.format("🗑 Удален файл: %s", file.getFileName()));
            comment.setTask(savedTask);
            comment.setAuthor(user);
            commentRepository.save(comment);

            sendTextMessage(chatId, String.format("✅ Файл \"%s\" удален!", file.getFileName()));
            stateService.removeState(deleteFileKey(chatId));
            stateService.saveState(fileMenuTaskIdKey(chatId), savedTask.getId());
            showFileMenu(chatId, savedTask, user);

        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ошибка при удалении файла: " + e.getMessage());
            stateService.removeState(deleteFileKey(chatId));
        }
    }

    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    public void exitToTask(long chatId) {
        stateService.removeState(fileMenuTaskIdKey(chatId));
        stateService.removeState(fileUploadTaskIdKey(chatId));
        stateService.removeState(deleteFileKey(chatId));
    }
}