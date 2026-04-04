package ru.skillfactory.vkrbot.repository;

import ru.skillfactory.vkrbot.model.TelegramChat;
import ru.skillfactory.vkrbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramChatRepository extends JpaRepository<TelegramChat, Long> {
    Optional<TelegramChat> findByChatId(Long chatId);
    Optional<TelegramChat> findByUser(User user);
    List<TelegramChat> findAllByUserIsNotNull();
    void deleteByChatId(Long chatId);
}