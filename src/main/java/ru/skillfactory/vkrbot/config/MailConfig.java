package ru.skillfactory.vkrbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        // ВАЖНО: Явно устанавливаем протокол
        mailSender.setProtocol("smtps");

        Properties props = mailSender.getJavaMailProperties();

        // КРИТИЧЕСКИ ВАЖНО: включаем аутентификацию для smtps
        props.put("mail.smtps.auth", "true");

        // Настройки SSL
        props.put("mail.smtps.ssl.enable", "true");
        props.put("mail.smtps.ssl.trust", host);

        // Дополнительные обязательные настройки для Яндекс.Почты
        props.put("mail.smtps.host", host);
        props.put("mail.smtps.port", String.valueOf(port));
        props.put("mail.smtps.connectiontimeout", "10000");
        props.put("mail.smtps.timeout", "10000");

        // ВАЖНО: Явно указываем, что хотим использовать аутентификацию
        props.put("mail.smtps.auth.mechanisms", "LOGIN PLAIN");

        // Отключаем STARTTLS (мы используем SSL)
        props.put("mail.smtps.starttls.enable", "false");
        props.put("mail.smtps.starttls.required", "false");

        // Debug
        props.put("mail.debug", "true");

        mailSender.setJavaMailProperties(props);

        // Принудительно устанавливаем свойства еще раз через setter
        mailSender.setPassword(password);
        mailSender.setUsername(username);

        return mailSender;
    }
}