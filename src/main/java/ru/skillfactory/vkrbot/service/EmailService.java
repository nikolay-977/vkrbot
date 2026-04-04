package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendTokenToUser(User user) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject("Ваш токен доступа к системе");

            String roleText = user.getRole() == Role.SUPERVISOR ?
                    "научный руководитель" : "студент";

            String emailText = String.format(
                    "Здравствуйте, %s!\n\n" +
                            "Вам был создан аккаунт в системе.\n\n" +
                            "Ваш токен для доступа: %s\n" +
                            "Роль: %s\n\n" +
                            "С уважением,\n" +
                            "Администрация системы",
                    user.getFullName(), user.getToken(), roleText
            );

            message.setText(emailText);
            log.info(emailText);
//            mailSender.send(message);
            log.info("Email successfully sent to: {}", user.getEmail());

        } catch (MailException e) {
            log.error("Failed to send email to: {}. Error: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendNotification(User user, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Notification sent to: {}", user.getEmail());

        } catch (MailException e) {
            log.error("Failed to send notification to: {}. Error: {}", user.getEmail(), e.getMessage());
        }
    }
}