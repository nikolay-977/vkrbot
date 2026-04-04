package ru.skillfactory.vkrbot.config;

import ru.skillfactory.vkrbot.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@Slf4j
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotService);
            log.info("Telegram bot successfully registered");
            return botsApi;
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot: {}", e.getMessage());
            throw new RuntimeException("Failed to register Telegram bot", e);
        }
    }
}