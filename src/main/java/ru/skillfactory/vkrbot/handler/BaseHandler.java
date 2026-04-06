package ru.skillfactory.vkrbot.handler;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import lombok.extern.slf4j.Slf4j;
import ru.skillfactory.vkrbot.model.Status;
import ru.skillfactory.vkrbot.service.TelegramBotService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class BaseHandler {

    public TelegramBotService botService;

    public void setBotService(TelegramBotService botService) {
        this.botService = botService;
    }

    public void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");

        try {
            botService.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessageWithKeyboard(long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        message.setParseMode("HTML");

        try {
            botService.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    public ReplyKeyboardMarkup createKeyboard(List<List<String>> buttons) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        for (List<String> rowButtons : buttons) {
            KeyboardRow row = new KeyboardRow();
            for (String button : rowButtons) {
                row.add(new KeyboardButton(button));
            }
            keyboard.add(row);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    protected String getStatusEmoji(Status status) {
        switch (status) {
            case CREATED: return "🆕";
            case IN_PROGRESS: return "⏳";
            case WAITING_FOR_REVIEW: return "📤";
            case REVIEW: return "🔍";
            case NEED_IMPROVEMENT: return "🔄";
            case DONE: return "✅";
            case CANCELED: return "❌";
            default: return "📌";
        }
    }
}