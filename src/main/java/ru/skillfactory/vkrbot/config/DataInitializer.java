package ru.skillfactory.vkrbot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import ru.skillfactory.vkrbot.repository.UserRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Создаем администратора по умолчанию, если его нет
        if (!userRepository.existsByEmail("admin@admin.com")) {
            User admin = new User();
            admin.setFullName("Администратор");
            admin.setEmail("admin@admin.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            System.out.println("Администратор создан: admin@admin.com / admin123");
        }
    }
}
