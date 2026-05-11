package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

        // Любое другое сообщение рассматриваем как попытку ввести токен
        // но лучше выдать понятную инструкцию
        String token = messageText.trim();
        try {
            Optional<User> userOpt = botService.getUserRepository().findByToken(token);
            if (userOpt.isEmpty()) {
                // Токен неверный – показываем инструкцию и удаляем клавиатуру
                sendAuthInstruction(chatId);
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

            if (user.getRole() == Role.STUDENT) {
                botService.getNavigationHandler().sendMessageWithKeyboard(chatId, successMessage,
                        botService.getNavigationHandler().getStudentMainKeyboard());
            } else {
                botService.getNavigationHandler().sendMainMenu(chatId, user);
            }
        } catch (Exception e) {
            log.error("Error validating token for chat {}: {}", chatId, e.getMessage());
            sendAuthInstruction(chatId);
        }
    }

    private void sendAuthInstruction(long chatId) {
        String instruction = "❌ Вы не авторизованы.\n\n" +
                "Пожалуйста, введите токен, который был отправлен на ваш email.\n" +
                "Если вы не получили токен, обратитесь к администратору.\n" +
                "Для повторного получения инструкции введите /start.";

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(instruction);
        ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove();
        removeKeyboard.setRemoveKeyboard(true);   // <-- обязательно установить true
        message.setReplyMarkup(removeKeyboard);
        try {
            botService.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending auth instruction", e);
        }
    }

    public void logout(long chatId, User user) {
        // Удаляем состояния (если нужно), но лучше убрать вызов userStates
        // botService.getUserStates().clear(); // можно удалить
        botService.getTelegramService().disconnectChat(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("👋 Вы вышли из системы.\n\nДля повторной авторизации введите ваш токен или /start.");
        ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove();
        removeKeyboard.setRemoveKeyboard(true);
        message.setReplyMarkup(removeKeyboard);
        try {
            botService.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending logout message", e);
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