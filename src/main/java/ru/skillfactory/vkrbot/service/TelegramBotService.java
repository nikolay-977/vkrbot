package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import ru.skillfactory.vkrbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final TelegramService telegramService;

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
        }
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
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        String firstName = update.getMessage().getFrom().getFirstName();

        log.info("Received message from chat {}: {}", chatId, messageText);

        try {
            // Проверяем, авторизован ли уже пользователь
            if (telegramService.isChatConnected(chatId)) {
                handleAuthorizedUser(chatId, messageText);
            } else {
                handleUnauthorizedUser(chatId, messageText, firstName);
            }
        } catch (Exception e) {
            log.error("Error processing message from chat {}: {}", chatId, e.getMessage());
            sendTextMessage(chatId, "❌ Произошла ошибка. Пожалуйста, попробуйте позже.");
        }
    }

    private void handleUnauthorizedUser(long chatId, String messageText, String firstName) {
        if (messageText.equals("/start")) {
            String welcomeMessage = String.format(
                    "👋 Здравствуйте, %s!\n\n" +
                            "Для доступа к функционалу бота необходимо авторизоваться.\n" +
                            "Пожалуйста, введите ваш токен, который был отправлен на email при создании аккаунта.\n\n" +
                            "Если у вас нет токена, обратитесь к администратору системы.",
                    firstName
            );
            sendTextMessage(chatId, welcomeMessage);
            return;
        }

        // Проверяем токен
        String token = messageText.trim();
        try {
            Optional<User> userOpt = userRepository.findByToken(token);

            if (userOpt.isEmpty()) {
                sendTextMessage(chatId, "❌ Неверный токен. Пожалуйста, проверьте токен и попробуйте снова.");
                return;
            }

            User user = userOpt.get();

            if (!user.isEnabled()) {
                sendTextMessage(chatId, "❌ Ваш аккаунт заблокирован. Обратитесь к администратору.");
                return;
            }

            // Подключаем чат к пользователю
            telegramService.connectUserToChat(chatId, user);

            String successMessage = String.format(
                    "✅ Авторизация успешна!\n\n" +
                            "Добро пожаловать, %s!\n" +
                            "Ваша роль: %s\n\n" +
                            "Используйте кнопки меню для навигации.",
                    user.getFullName(),
                    getRoleDisplayName(user.getRole())
            );

            sendMessageWithMenu(chatId, successMessage, user.getRole());

        } catch (Exception e) {
            log.error("Error validating token for chat {}: {}", chatId, e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при проверке токена. Попробуйте позже.");
        }
    }

    private void handleAuthorizedUser(long chatId, String messageText) {
        try {
            Optional<User> userOpt = telegramService.getUserByChatId(chatId);
            if (userOpt.isEmpty()) {
                sendTextMessage(chatId, "❌ Сессия устарела. Пожалуйста, введите токен заново.");
                return;
            }

            User user = userOpt.get();

            if (!user.isEnabled()) {
                sendTextMessage(chatId, "❌ Ваш аккаунт заблокирован. Доступ к функциям ограничен.");
                telegramService.disconnectChat(chatId);
                return;
            }

            switch (messageText) {
                case "🎓 Научный руководитель":
                    if (user.getRole() == Role.STUDENT) {
                        showSupervisorInfo(chatId, user);
                    } else {
                        sendTextMessage(chatId, "❌ Эта функция доступна только студентам.");
                    }
                    break;

                case "📋 Мои студенты":
                    if (user.getRole() == Role.SUPERVISOR) {
                        showStudentsList(chatId, user);
                    } else {
                        sendTextMessage(chatId, "❌ Эта функция доступна только научным руководителям.");
                    }
                    break;

                case "ℹ️ Мои данные":
                    showUserInfo(chatId, user);
                    break;

                case "🔙 Главное меню":
                case "/start":
                    sendMessageWithMenu(chatId, "Главное меню:", user.getRole());
                    break;

                case "🚪 Выйти":
                    telegramService.disconnectChat(chatId);
                    sendTextMessage(chatId, "👋 Вы вышли из системы. Для повторного входа введите токен.");
                    break;

                default:
                    sendMessageWithMenu(chatId, "Используйте кнопки меню для навигации:", user.getRole());
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling authorized user {}: {}", chatId, e.getMessage());
            sendTextMessage(chatId, "❌ Произошла ошибка. Пожалуйста, попробуйте позже.");
        }
    }

    private void showSupervisorInfo(long chatId, User student) {
        if (student.getSupervisor() == null) {
            sendMessageWithMenu(chatId, "❌ У вас еще не назначен научный руководитель.", student.getRole());
            return;
        }

        User supervisor = student.getSupervisor();
        String message = String.format(
                "🎓 Ваш научный руководитель:\n\n" +
                        "👤 ФИО: %s\n" +
                        "📧 Email: %s\n" +
                        "%s%s" +
                        "\n\nВы можете связаться с руководителем по указанным контактам.",
                supervisor.getFullName(),
                supervisor.getEmail(),
                supervisor.getPhone() != null ? "📞 Телефон: " + supervisor.getPhone() + "\n" : "",
                supervisor.getTelegram() != null ? "✈️ Telegram: " + supervisor.getTelegram() + "\n" : ""
        );

        sendMessageWithMenu(chatId, message, student.getRole());
    }

    private void showStudentsList(long chatId, User supervisor) {
        List<User> students = userRepository.findBySupervisor(supervisor);

        if (students.isEmpty()) {
            sendMessageWithMenu(chatId, "📋 У вас пока нет студентов.", supervisor.getRole());
            return;
        }

        StringBuilder message = new StringBuilder("📋 Ваши студенты:\n\n");

        for (int i = 0; i < students.size(); i++) {
            User student = students.get(i);
            message.append(String.format("%d. %s\n", i + 1, student.getFullName()));
            message.append(String.format("   📧 %s\n", student.getEmail()));
            if (student.getPhone() != null) {
                message.append(String.format("   📞 %s\n", student.getPhone()));
            }
            if (student.getTelegram() != null) {
                message.append(String.format("   ✈️ %s\n", student.getTelegram()));
            }
            message.append("\n");
        }

        sendMessageWithMenu(chatId, message.toString(), supervisor.getRole());
    }

    private void showUserInfo(long chatId, User user) {
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

        sendMessageWithMenu(chatId, message, user.getRole());
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "••••••••";
        return token.substring(0, 4) + "••••" + token.substring(token.length() - 4);
    }

    private void sendMessageWithMenu(long chatId, String text, Role role) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(getKeyboardForRole(role));
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private ReplyKeyboardMarkup getKeyboardForRole(Role role) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        if (role == Role.STUDENT) {
            row.add(new KeyboardButton("🎓 Научный руководитель"));
        } else if (role == Role.SUPERVISOR) {
            row.add(new KeyboardButton("📋 Мои студенты"));
        }

        keyboard.add(row);

        KeyboardRow secondRow = new KeyboardRow();
        secondRow.add(new KeyboardButton("ℹ️ Мои данные"));
        secondRow.add(new KeyboardButton("🔙 Главное меню"));
        keyboard.add(secondRow);

        KeyboardRow thirdRow = new KeyboardRow();
        thirdRow.add(new KeyboardButton("🚪 Выйти"));
        keyboard.add(thirdRow);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private String getRoleDisplayName(Role role) {
        return switch (role) {
            case STUDENT -> "Студент";
            case SUPERVISOR -> "Научный руководитель";
            case ADMIN -> "Администратор";
        };
    }

    // Методы для уведомлений (вызываются из NotificationService)
    public void notifyUserCreated(User user) {
        telegramService.getChatIdByUser(user).ifPresent(chatId -> {
            String message = String.format(
                    "👋 Добро пожаловать в систему, %s!\n\n" +
                            "Ваш аккаунт успешно создан.\n" +
                            "Роль: %s\n\n" +
                            "Используйте меню для навигации.",
                    user.getFullName(),
                    getRoleDisplayName(user.getRole())
            );
            sendMessageWithMenu(chatId, message, user.getRole());
        });
    }

    public void notifyStudentAboutSupervisor(User student) {
        if (student.getSupervisor() == null) return;

        telegramService.getChatIdByUser(student).ifPresent(chatId -> {
            String message = String.format(
                    "🔔 Уведомление!\n\n" +
                            "Вам назначен научный руководитель:\n" +
                            "%s\n\n" +
                            "Нажмите кнопку '🎓 Научный руководитель' для просмотра контактов.",
                    student.getSupervisor().getFullName()
            );
            sendMessageWithMenu(chatId, message, student.getRole());
        });
    }

    public void notifySupervisorAboutNewStudent(User supervisor, User student) {
        telegramService.getChatIdByUser(supervisor).ifPresent(chatId -> {
            String message = String.format(
                    "🔔 Уведомление!\n\n" +
                            "К вам прикреплен новый студент:\n" +
                            "%s\n\n" +
                            "Нажмите кнопку '📋 Мои студенты' для просмотра списка.",
                    student.getFullName()
            );
            sendMessageWithMenu(chatId, message, supervisor.getRole());
        });
    }

    public void notifyUserBlocked(User user) {
        telegramService.getChatIdByUser(user).ifPresent(chatId -> {
            String message = "🔒 Ваш аккаунт был заблокирован администратором.";
            sendTextMessage(chatId, message);
        });
    }

    public void notifyUserUnblocked(User user) {
        telegramService.getChatIdByUser(user).ifPresent(chatId -> {
            String message = "✅ Ваш аккаунт был разблокирован. Вы снова можете пользоваться системой.";
            sendMessageWithMenu(chatId, message, user.getRole());
        });
    }

    public void notifyTokenRegenerated(User user) {
        telegramService.getChatIdByUser(user).ifPresent(chatId -> {
            String message = "🔄 Ваш токен был перегенерирован. Новый токен отправлен на email.";
            sendMessageWithMenu(chatId, message, user.getRole());
        });
    }
}