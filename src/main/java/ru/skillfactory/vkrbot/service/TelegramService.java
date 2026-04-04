package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.model.TelegramChat;
import ru.skillfactory.vkrbot.model.User;
import ru.skillfactory.vkrbot.repository.TelegramChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final TelegramChatRepository telegramChatRepository;

    @Transactional
    public void connectUserToChat(Long chatId, User user) {
        Optional<TelegramChat> existingChat = telegramChatRepository.findByChatId(chatId);
        if (existingChat.isPresent()) {
            TelegramChat chat = existingChat.get();
            if (!chat.getUser().equals(user)) {
                chat.setUser(user);
                chat.setLastActivity(LocalDateTime.now());
                telegramChatRepository.save(chat);
                log.info("Chat {} reconnected to user {}", chatId, user.getEmail());
            }
        } else {
            TelegramChat telegramChat = new TelegramChat();
            telegramChat.setChatId(chatId);
            telegramChat.setUser(user);
            telegramChatRepository.save(telegramChat);
            log.info("Chat {} connected to user {}", chatId, user.getEmail());
        }
    }

    @Transactional
    public void disconnectChat(Long chatId) {
        telegramChatRepository.findByChatId(chatId).ifPresent(chat -> {
            telegramChatRepository.delete(chat);
            log.info("Chat {} disconnected", chatId);
        });
    }

    public Optional<User> getUserByChatId(Long chatId) {
        return telegramChatRepository.findByChatId(chatId)
                .map(TelegramChat::getUser);
    }

    public Optional<Long> getChatIdByUser(User user) {
        return telegramChatRepository.findByUser(user)
                .map(TelegramChat::getChatId);
    }

    public boolean isChatConnected(Long chatId) {
        return telegramChatRepository.findByChatId(chatId).isPresent();
    }

    public List<TelegramChat> getAllConnectedChats() {
        return telegramChatRepository.findAllByUserIsNotNull();
    }
}