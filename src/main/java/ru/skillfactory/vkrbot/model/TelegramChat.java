package ru.skillfactory.vkrbot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_chats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelegramChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", unique = true, nullable = false)
    private Long chatId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @PrePersist
    protected void onCreate() {
        connectedAt = LocalDateTime.now();
        lastActivity = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivity = LocalDateTime.now();
    }
}