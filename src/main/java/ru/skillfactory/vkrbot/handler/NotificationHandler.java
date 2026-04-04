package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationHandler extends BaseHandler {

    public void notifyUserCreated(User user) {
        botService.getTelegramService().getChatIdByUser(user).ifPresent(chatId -> {
            String message = String.format(
                    "👋 Добро пожаловать в систему, %s!\n\n" +
                            "Ваш аккаунт успешно создан.\n" +
                            "Роль: %s\n\n" +
                            "Используйте меню для навигации.",
                    user.getFullName(),
                    getRoleDisplayName(user.getRole())
            );
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message,
                    botService.getNavigationHandler().getKeyboardForRole(user.getRole()));
        });
    }

    public void notifyStudentAboutSupervisor(User student) {
        if (student.getSupervisor() == null) return;

        botService.getTelegramService().getChatIdByUser(student).ifPresent(chatId -> {
            String message = String.format(
                    "🔔 Уведомление!\n\n" +
                            "Вам назначен научный руководитель:\n" +
                            "%s\n\n" +
                            "Нажмите кнопку '🎓 Научный руководитель' для просмотра контактов.",
                    student.getSupervisor().getFullName()
            );
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message,
                    botService.getNavigationHandler().getKeyboardForRole(student.getRole()));
        });
    }

    public void notifySupervisorAboutNewStudent(User supervisor, User student) {
        botService.getTelegramService().getChatIdByUser(supervisor).ifPresent(chatId -> {
            String message = String.format(
                    "🔔 Уведомление!\n\n" +
                            "К вам прикреплен новый студент:\n" +
                            "%s\n\n" +
                            "Нажмите кнопку '📋 Мои студенты' для просмотра списка.",
                    student.getFullName()
            );
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId, message,
                    botService.getNavigationHandler().getKeyboardForRole(supervisor.getRole()));
        });
    }

    public void notifyUserBlocked(User user) {
        botService.getTelegramService().getChatIdByUser(user).ifPresent(chatId -> {
            botService.getNavigationHandler().sendTextMessage(chatId, "🔒 Ваш аккаунт был заблокирован администратором.");
        });
    }

    public void notifyUserUnblocked(User user) {
        botService.getTelegramService().getChatIdByUser(user).ifPresent(chatId -> {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId, "✅ Ваш аккаунт был разблокирован.",
                    botService.getNavigationHandler().getKeyboardForRole(user.getRole()));
        });
    }

    public void notifyTokenRegenerated(User user) {
        botService.getTelegramService().getChatIdByUser(user).ifPresent(chatId -> {
            botService.getNavigationHandler().sendMessageWithKeyboard(chatId, "🔄 Ваш токен был перегенерирован. Новый токен отправлен на email.",
                    botService.getNavigationHandler().getKeyboardForRole(user.getRole()));
        });
    }

    private String getRoleDisplayName(Role role) {
        return switch (role) {
            case STUDENT -> "Студент";
            case SUPERVISOR -> "Научный руководитель";
            case ADMIN -> "Администратор";
        };
    }
}