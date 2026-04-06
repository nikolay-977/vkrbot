package ru.skillfactory.vkrbot.handler;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.Status;
import ru.skillfactory.vkrbot.model.Task;
import ru.skillfactory.vkrbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class NavigationHandler extends BaseHandler {

    public boolean handleNavigation(long chatId, String messageText, User user) {
        if (messageText.equals("🏠Главное меню") || messageText.equals("/start")) {
            log.info("=== BACK TO MAIN MENU ===");
            clearAllStates(chatId);
            sendMainMenu(chatId, user);
            return true;
        }
        return false;
    }

    public void clearAllStates(long chatId) {
        botService.getUserStates().remove(chatId);
    }

    public void sendMainMenu(long chatId, User user) {
        sendMessageWithKeyboard(chatId, "🏠Главное меню:", getKeyboardForRole(user.getRole()));
    }

    public ReplyKeyboardMarkup getKeyboardForRole(Role role) {
        if (role == Role.STUDENT) {
            return getStudentMainKeyboard();
        } else if (role == Role.SUPERVISOR) {
            return getSupervisorMainKeyboard();
        }
        return getSupervisorMainKeyboard();
    }

    public ReplyKeyboardMarkup getSupervisorMainKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("📋 Мои студенты");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("ℹ️ Мои данные");
        buttons.add(row2);
        List<String> row3 = new ArrayList<>();
        row3.add("🚪 Выйти");
        buttons.add(row3);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getStudentMainKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("🎓 Диплом");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("ℹ️ Мои данные");
        row2.add("👨‍🏫 Научный руководитель");
        buttons.add(row2);
        List<String> row3 = new ArrayList<>();
        row3.add("🚪 Выйти");
        buttons.add(row3);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getStudentDiplomaKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("📋 Дедлайны");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("🏠Главное меню");
        buttons.add(row2);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getStudentDeadlinesKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("🏠Главное меню");
        buttons.add(row1);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getStudentTasksKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("🔙 Назад к дедлайнам");
        row1.add("🏠Главное меню");
        buttons.add(row1);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getSupervisorDeadlineKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("➕ Добавить дедлайн");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("🔙 Назад к студентам");
        row2.add("🏠Главное меню");
        buttons.add(row2);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getSupervisorTaskKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("➕ Добавить задачу");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("🔙 Назад к дедлайнам");
        row2.add("🏠Главное меню");
        buttons.add(row2);
        return createKeyboard(buttons);
    }

    public ReplyKeyboardMarkup getSupervisorTaskViewKeyboard() {
        List<List<String>> buttons = new ArrayList<>();
        List<String> row1 = new ArrayList<>();
        row1.add("✅ Принять");
        row1.add("🔄 Доработка");
        buttons.add(row1);
        List<String> row2 = new ArrayList<>();
        row2.add("✏️ Редактировать");
        row2.add("❌ Отменить");
        buttons.add(row2);
        List<String> row3 = new ArrayList<>();
        row3.add("💬 Комментарии");
        row3.add("📎 Файлы");
        buttons.add(row3);
        List<String> row4 = new ArrayList<>();
        row4.add("🔙 Назад");
        row4.add("🏠Главное меню");
        buttons.add(row4);
        return createKeyboard(buttons);
    }
}