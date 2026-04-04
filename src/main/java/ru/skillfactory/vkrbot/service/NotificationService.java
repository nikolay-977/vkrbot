package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailService emailService;
    private final TelegramBotService telegramBotService;

    /**
     * Уведомление о создании пользователя
     */
    public void notifyUserCreated(User user) {
        // Отправляем токен на email для не-админов
        if (user.getRole() != Role.ADMIN) {
            emailService.sendTokenToUser(user);
            log.info("Token sent to email: {}", user.getEmail());
        }

        // Если у пользователя уже есть Telegram chat, отправляем приветствие
        try {
            telegramBotService.notifyUserCreated(user);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification for user creation: {}", e.getMessage());
        }
    }

    /**
     * Уведомление о назначении научного руководителя
     */
    public void notifySupervisorAssigned(User student, User supervisor) {
        // Email уведомления
        String studentSubject = "🎓 Назначен научный руководитель";
        String studentText = String.format(
                "Здравствуйте, %s!\n\n" +
                        "Вам назначен научный руководитель:\n" +
                        "👤 %s\n" +
                        "📧 Email: %s\n" +
                        "%s%s" +
                        "\n\nВы можете связаться с руководителем для обсуждения учебных вопросов.",
                student.getFullName(),
                supervisor.getFullName(),
                supervisor.getEmail(),
                supervisor.getPhone() != null ? "📞 Телефон: " + supervisor.getPhone() + "\n" : "",
                supervisor.getTelegram() != null ? "✈️ Telegram: " + supervisor.getTelegram() + "\n" : ""
        );
        emailService.sendNotification(student, studentSubject, studentText);

        String supervisorSubject = "📋 Новый студент";
        String supervisorText = String.format(
                "Здравствуйте, %s!\n\n" +
                        "К вам прикреплен новый студент:\n" +
                        "👤 %s\n" +
                        "📧 Email: %s\n" +
                        "%s%s" +
                        "\n\nПожалуйста, свяжитесь со студентом для дальнейшей работы.",
                supervisor.getFullName(),
                student.getFullName(),
                student.getEmail(),
                student.getPhone() != null ? "📞 Телефон: " + student.getPhone() + "\n" : "",
                student.getTelegram() != null ? "✈️ Telegram: " + student.getTelegram() + "\n" : ""
        );
        emailService.sendNotification(supervisor, supervisorSubject, supervisorText);

        // Telegram уведомления
        try {
            telegramBotService.notifyStudentAboutSupervisor(student);
            telegramBotService.notifySupervisorAboutNewStudent(supervisor, student);
        } catch (Exception e) {
            log.error("Failed to send Telegram notifications about supervisor assignment: {}", e.getMessage());
        }
    }

    /**
     * Уведомление о блокировке пользователя
     */
    public void notifyUserBlocked(User user) {
        String subject = "🔒 Ваш аккаунт заблокирован";
        String text = String.format(
                "Здравствуйте, %s!\n\n" +
                        "Ваш аккаунт был заблокирован администратором системы.\n" +
                        "Для получения дополнительной информации и разблокировки обратитесь к администратору.\n\n" +
                        "С уважением,\n" +
                        "Администрация системы",
                user.getFullName()
        );
        emailService.sendNotification(user, subject, text);

        // Telegram уведомление
        try {
            telegramBotService.notifyUserBlocked(user);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification about blocking: {}", e.getMessage());
        }
    }

    /**
     * Уведомление о разблокировке пользователя
     */
    public void notifyUserUnblocked(User user) {
        String subject = "✅ Ваш аккаунт разблокирован";
        String text = String.format(
                "Здравствуйте, %s!\n\n" +
                        "Ваш аккаунт был разблокирован администратором.\n" +
                        "Вы снова можете полноценно пользоваться системой.\n\n" +
                        "С уважением,\n" +
                        "Администрация системы",
                user.getFullName()
        );
        emailService.sendNotification(user, subject, text);

        // Telegram уведомление
        try {
            telegramBotService.notifyUserUnblocked(user);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification about unblocking: {}", e.getMessage());
        }
    }

    /**
     * Уведомление о смене роли
     */
    public void notifyRoleChanged(User user, String oldRole, String newRole) {
        String subject = "🔄 Изменение роли в системе";
        String text = String.format(
                "Здравствуйте, %s!\n\n" +
                        "Ваша роль в системе была изменена:\n" +
                        "➡️ Было: %s\n" +
                        "➡️ Стало: %s\n\n" +
                        "С уважением,\n" +
                        "Администрация системы",
                user.getFullName(),
                oldRole,
                newRole
        );
        emailService.sendNotification(user, subject, text);
    }

    /**
     * Уведомление о перегенерации токена
     */
    public void notifyTokenRegenerated(User user) {
        if (user.getRole() != Role.ADMIN) {
            emailService.sendTokenToUser(user);
            log.info("New token sent to email: {}", user.getEmail());
        }

        try {
            telegramBotService.notifyTokenRegenerated(user);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification about token regeneration: {}", e.getMessage());
        }
    }
}