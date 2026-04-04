package ru.skillfactory.vkrbot.dto;

import ru.skillfactory.vkrbot.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserDto {
    private Long id;

    @NotBlank(message = "ФИО обязательно")
    private String fullName;

    private String diplomaSubject;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;

    private String phone;

    private String telegram;

    @NotNull(message = "Роль обязательна")
    private Role role;

    private Long supervisorId;

    private boolean enabled = true;
}