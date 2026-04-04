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

        mailSender.setProtocol("smtps");

        Properties props = mailSender.getJavaMailProperties();

        props.put("mail.smtps.auth", "true");

        props.put("mail.smtps.ssl.enable", "true");
        props.put("mail.smtps.ssl.trust", host);

        props.put("mail.smtps.host", host);
        props.put("mail.smtps.port", String.valueOf(port));
        props.put("mail.smtps.connectiontimeout", "10000");
        props.put("mail.smtps.timeout", "10000");

        props.put("mail.smtps.auth.mechanisms", "LOGIN PLAIN");

        props.put("mail.smtps.starttls.enable", "false");
        props.put("mail.smtps.starttls.required", "false");

        props.put("mail.debug", "true");

        mailSender.setJavaMailProperties(props);

        mailSender.setPassword(password);
        mailSender.setUsername(username);

        return mailSender;
    }
}