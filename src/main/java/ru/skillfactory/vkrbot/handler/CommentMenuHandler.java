package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.*;
import ru.skillfactory.vkrbot.repository.CommentRepository;
import ru.skillfactory.vkrbot.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class CommentMenuHandler extends BaseHandler {

    private final CommentRepository commentRepository;
    private final StateService stateService;

    public CommentMenuHandler(CommentRepository commentRepository, StateService stateService) {
        this.commentRepository = commentRepository;
        this.stateService = stateService;
    }

    private String commentMenuTaskIdKey(long chatId) {
        return "commentMenuTaskId:" + chatId;
    }

    private String addCommentTaskIdKey(long chatId) {
        return "addCommentTaskId:" + chatId;
    }

    public boolean isInCommentMenu(long chatId) {
        return stateService.hasState(commentMenuTaskIdKey(chatId));
    }

    public boolean isInAddCommentState(long chatId) {
        return stateService.hasState(addCommentTaskIdKey(chatId));
    }

    public Task getCurrentTask(long chatId) {
        Long taskId = stateService.getState(commentMenuTaskIdKey(chatId), Long.class);
        if (taskId == null) return null;
        return botService.getTaskRepository().findById(taskId).orElse(null);
    }

    public void showCommentMenu(long chatId, Task task, User user) {
        stateService.saveState(commentMenuTaskIdKey(chatId), task.getId());

        List<Comment> comments = commentRepository.findByTaskOrderByCreatedAtDesc(task);

        StringBuilder message = new StringBuilder();
        message.append("💬 Комментарии к задаче:\n\n");
        message.append("📌 ").append(task.getTitle()).append("\n\n");

        if (comments.isEmpty()) {
            message.append("Нет комментариев.\n");
        } else {
            List<Comment> reversed = new ArrayList<>(comments);
            Collections.reverse(reversed);
            for (int i = 0; i < reversed.size(); i++) {
                Comment comment = reversed.get(i);
                String authorIcon = comment.getAuthor().getRole() == Role.SUPERVISOR ? "👨‍🏫" : "👨‍🎓";
                String deleteBtn = (comment.getAuthor().getId().equals(user.getId())) ? " ❌" : "";
                message.append(String.format("%d. %s %s:%s\n", i + 1, authorIcon, comment.getAuthor().getFullName(), deleteBtn));
                message.append("   ").append(comment.getText()).append("\n");
                message.append(String.format("   📅 %s\n\n", comment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
            }
        }

        message.append("\nВведите номер комментария для удаления (только свои),");
        message.append("\nили нажмите кнопку ниже для добавления нового.");

        sendMessageWithKeyboard(chatId, message.toString(), getCommentMenuKeyboard());
    }

    private ReplyKeyboardMarkup getCommentMenuKeyboard() {
        List<List<String>> buttons = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add("➕ Добавить комментарий");
        buttons.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add("🔙 Назад к задаче");
        row2.add("🏠Главное меню");
        buttons.add(row2);

        return createKeyboard(buttons);
    }

    public void startAddComment(long chatId) {
        Long taskId = stateService.getState(commentMenuTaskIdKey(chatId), Long.class);
        if (taskId != null) {
            stateService.saveState(addCommentTaskIdKey(chatId), taskId);
            sendTextMessage(chatId, "Введите текст комментария:");
        } else {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена. Попробуйте вернуться и открыть комментарии заново.");
        }
    }

    public void handleAddCommentText(long chatId, String text, User user) {
        if (text.equals("🏠Главное меню")) {
            stateService.removeState(addCommentTaskIdKey(chatId));
            botService.getNavigationHandler().sendMainMenu(chatId, user);
            return;
        }
        if (text.equals("🔙 Назад к задаче")) {
            stateService.removeState(addCommentTaskIdKey(chatId));
            Long taskId = stateService.getState(commentMenuTaskIdKey(chatId), Long.class);
            if (taskId != null) {
                Task task = botService.getTaskRepository().findById(taskId).orElse(null);
                if (task != null) {
                    showCommentMenu(chatId, task, user);
                    return;
                }
            }
            botService.getNavigationHandler().sendMainMenu(chatId, user);
            return;
        }

        Long taskId = stateService.getState(addCommentTaskIdKey(chatId), Long.class);
        if (taskId == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена. Возможно, сессия истекла.");
            stateService.removeState(addCommentTaskIdKey(chatId));
            return;
        }
        Task task = botService.getTaskRepository().findById(taskId).orElse(null);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(addCommentTaskIdKey(chatId));
            return;
        }
        Comment comment = new Comment();
        comment.setText(text);
        comment.setTask(task);
        comment.setAuthor(user);
        commentRepository.save(comment);
        sendTextMessage(chatId, "✅ Комментарий добавлен!");
        stateService.removeState(addCommentTaskIdKey(chatId));
        showCommentMenu(chatId, task, user);
    }

    public void handleDeleteComment(long chatId, int commentNumber, User user) {
        Task task = getCurrentTask(chatId);
        if (task == null) {
            sendTextMessage(chatId, "❌ Ошибка: задача не найдена.");
            stateService.removeState(commentMenuTaskIdKey(chatId));
            return;
        }

        List<Comment> comments = commentRepository.findByTaskOrderByCreatedAtDesc(task);
        if (commentNumber < 1 || commentNumber > comments.size()) {
            sendTextMessage(chatId, "❌ Неверный номер комментария.");
            return;
        }

        Comment comment = comments.get(commentNumber - 1);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            sendTextMessage(chatId, "❌ Вы можете удалять только свои комментарии.");
            return;
        }

        commentRepository.delete(comment);
        sendTextMessage(chatId, "✅ Комментарий удален!");
        showCommentMenu(chatId, task, user);
    }

    public void exitToTask(long chatId) {
        stateService.removeState(commentMenuTaskIdKey(chatId));
        stateService.removeState(addCommentTaskIdKey(chatId));
    }
}