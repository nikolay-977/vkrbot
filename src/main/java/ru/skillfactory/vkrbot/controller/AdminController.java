package ru.skillfactory.vkrbot.controller;

import ru.skillfactory.vkrbot.dto.SearchDto;
import ru.skillfactory.vkrbot.dto.UserDto;
import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import ru.skillfactory.vkrbot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userService.getAllUsers().size());
        model.addAttribute("totalAdmins", userService.searchUsers(createSearchDtoWithRole(Role.ADMIN)).size());
        model.addAttribute("totalSupervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)).size());
        model.addAttribute("totalStudents", userService.searchUsers(createSearchDtoWithRole(Role.STUDENT)).size());
        model.addAttribute("blockedUsers", userService.searchUsers(createSearchDtoWithEnabled(false)).size());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        Page<User> usersPage = userService.getAllUsers(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("totalItems", usersPage.getTotalElements());
        model.addAttribute("searchDto", new SearchDto());
        model.addAttribute("searchPerformed", false);

        return "admin/users";
    }

    @PostMapping("/users/search")
    public String searchUsers(@ModelAttribute SearchDto searchDto, Model model) {
        List<User> users = userService.searchUsers(searchDto);
        model.addAttribute("users", users);
        model.addAttribute("searchDto", searchDto);
        model.addAttribute("searchPerformed", true);
        model.addAttribute("currentPage", 0);
        model.addAttribute("totalPages", 1);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String showCreateForm(Model model) {
        model.addAttribute("userDto", new UserDto());
        model.addAttribute("roles", Role.values());
        model.addAttribute("supervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)));
        return "admin/user-form";
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute UserDto userDto,
                             BindingResult result,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (result.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("supervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)));
            return "admin/user-form";
        }

        try {
            userService.createUser(userDto);
            redirectAttributes.addFlashAttribute("success", "Пользователь успешно создан");
            return "redirect:/admin/users";
        } catch (Exception e) {
            log.error("Ошибка при создании пользователя: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании пользователя: " + e.getMessage());
            return "redirect:/admin/users/create";
        }
    }

    @GetMapping("/users/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        User user = userService.getUserById(id);
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setFullName(user.getFullName());
        userDto.setDiplomaSubject(user.getDiplomaSubject());
        userDto.setEmail(user.getEmail());
        userDto.setPhone(user.getPhone());
        userDto.setTelegram(user.getTelegram());
        userDto.setRole(user.getRole());
        userDto.setEnabled(user.isEnabled());
        if (user.getSupervisor() != null) {
            userDto.setSupervisorId(user.getSupervisor().getId());
        }

        model.addAttribute("userDto", userDto);
        model.addAttribute("roles", Role.values());
        model.addAttribute("supervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)));
        return "admin/user-form";
    }

    @PostMapping("/users/edit/{id}")
    public String updateUser(@PathVariable Long id,
                             @Valid @ModelAttribute UserDto userDto,
                             BindingResult result,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (result.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("supervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)));
            return "admin/user-form";
        }

        try {
            userService.updateUser(id, userDto);
            redirectAttributes.addFlashAttribute("success", "Пользователь успешно обновлен");
            return "redirect:/admin/users";
        } catch (Exception e) {
            log.error("Ошибка при обновлении пользователя: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении пользователя: " + e.getMessage());
            return "redirect:/admin/users/edit/" + id;
        }
    }

    @PostMapping("/users/block/{id}")
    public String blockUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.blockUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь заблокирован");
        } catch (Exception e) {
            log.error("Ошибка при блокировке пользователя: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при блокировке пользователя: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/unblock/{id}")
    public String unblockUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.unblockUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь разблокирован");
        } catch (Exception e) {
            log.error("Ошибка при разблокировке пользователя: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при разблокировке пользователя: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/send-token/{id}")
    public String sendToken(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.sendTokenToUser(id);
            redirectAttributes.addFlashAttribute("success", "Токен отправлен на email пользователя");
        } catch (Exception e) {
            log.error("Ошибка при отправке токена: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при отправке токена: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/regenerate-token/{id}")
    public String regenerateToken(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.regenerateToken(id);
            redirectAttributes.addFlashAttribute("success", "Токен успешно перегенерирован и отправлен");
        } catch (Exception e) {
            log.error("Ошибка при перегенерации токена: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при перегенерации токена: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/users/assign-supervisor")
    public String showAssignSupervisorForm(Model model) {
        model.addAttribute("students", userService.searchUsers(createSearchDtoWithRole(Role.STUDENT)));
        model.addAttribute("supervisors", userService.searchUsers(createSearchDtoWithRole(Role.SUPERVISOR)));
        return "admin/assign-supervisor";
    }

    @PostMapping("/users/assign-supervisor")
    public String assignSupervisor(@RequestParam Long studentId,
                                   @RequestParam Long supervisorId,
                                   RedirectAttributes redirectAttributes) {
        try {
            userService.assignSupervisor(studentId, supervisorId);
            redirectAttributes.addFlashAttribute("success", "Научный руководитель успешно назначен");
        } catch (Exception e) {
            log.error("Ошибка при назначении руководителя: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при назначении руководителя: " + e.getMessage());
        }
        return "redirect:/admin/users/assign-supervisor";
    }

    private SearchDto createSearchDtoWithRole(Role role) {
        SearchDto dto = new SearchDto();
        dto.setRole(role);
        return dto;
    }

    private SearchDto createSearchDtoWithEnabled(boolean enabled) {
        SearchDto dto = new SearchDto();
        dto.setEnabled(enabled);
        return dto;
    }
}