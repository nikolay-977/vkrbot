package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class UserHandler extends BaseHandler {

    public void handleUnauthorized(long chatId, String messageText, String firstName) {
        if (messageText.equals("/start")) {
            String welcomeMessage = String.format(
                    "👋 Здравствуйте, %s!\n\n" +
                            "Для доступа к функционалу бота необходимо авторизоваться.\n" +
                            "Пожалуйста, введите ваш токен...",
                    firstName
            );
            sendTextMessage(chatId, welcomeMessage);
            return;
        }

        String token = messageText.trim();
        try {
            Optional<User> userOpt = botService.getUserRepository().findByToken(token);

            if (userOpt.isEmpty()) {
                sendTextMessage(chatId, "❌ Неверный токен.");
                return;
            }

            User user = userOpt.get();

            if (!user.isEnabled()) {
                sendTextMessage(chatId, "❌ Ваш аккаунт заблокирован.");
                return;
            }

            botService.getTelegramService().connectUserToChat(chatId, user);

            String successMessage = String.format(
                    "✅ Авторизация успешна!\n\nДобро пожаловать, %s!\nВаша роль: %s",
                    user.getFullName(), getRoleDisplayName(user.getRole())
            );

            botService.getNavigationHandler().sendMainMenu(chatId, user);

        } catch (Exception e) {
            log.error("Error validating token for chat {}: {}", chatId, e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при проверке токена.");
        }
    }

    public void showUserInfo(long chatId, User user) {
        String message = String.format(
                "ℹ️ Ваши данные:\n\n" +
                        "👤 ФИО: %s\n" +
                        "📧 Email: %s\n" +
                        "🎭 Роль: %s\n" +
                        "%s%s" +
                        "🔑 Токен: %s\n" +
                        "✅ Статус: %s",
                user.getFullName(),
                user.getEmail(),
                getRoleDisplayName(user.getRole()),
                user.getPhone() != null ? "📞 Телефон: " + user.getPhone() + "\n" : "",
                user.getTelegram() != null ? "✈️ Telegram: " + user.getTelegram() + "\n" : "",
                maskToken(user.getToken()),
                user.isEnabled() ? "🟢 Активен" : "🔴 Заблокирован"
        );

        sendTextMessage(chatId, message);
    }

    public void logout(long chatId, User user) {
        botService.getUserStates().clear();
        botService.getTelegramService().disconnectChat(chatId);
        sendTextMessage(chatId, "👋 Вы вышли из системы.");
    }

    private String getRoleDisplayName(Role role) {
        return switch (role) {
            case STUDENT -> "Студент";
            case SUPERVISOR -> "Научный руководитель";
            case ADMIN -> "Администратор";
        };
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "••••••••";
        return token.substring(0, 4) + "••••" + token.substring(token.length() - 4);
    }
}