package ru.skillfactory.vkrbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class StateService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public StateService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Сохранить состояние с TTL (по умолчанию 30 минут)
    public void saveState(String key, Object state) {
        saveState(key, state, 30, TimeUnit.MINUTES);
    }

    public void saveState(String key, Object state, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, state, timeout, unit);
    }

    // Получить состояние
    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    // Удалить состояние
    public void removeState(String key) {
        redisTemplate.delete(key);
    }

    // Проверить наличие
    public boolean hasState(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}