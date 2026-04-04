package ru.skillfactory.vkrbot.dto;

import ru.skillfactory.vkrbot.model.Role;
import lombok.Data;

@Data
public class SearchDto {
    private String query;
    private String searchBy = "all";
    private Role role;
    private Boolean enabled;
}