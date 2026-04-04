package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.dto.SearchDto;
import ru.skillfactory.vkrbot.dto.UserDto;
import ru.skillfactory.vkrbot.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    User createUser(UserDto userDto);
    User updateUser(Long id, UserDto userDto);
    void deleteUser(Long id);
    void blockUser(Long id);
    void unblockUser(Long id);
    User getUserById(Long id);
    User getUserByEmail(String email);
    User getUserByToken(String token);  // Новый метод
    List<User> getStudentsBySupervisor(Long supervisorId);  // Новый метод
    List<User> getAllUsers();
    Page<User> getAllUsers(Pageable pageable);
    List<User> searchUsers(SearchDto searchDto);
    void assignSupervisor(Long studentId, Long supervisorId);
    void sendTokenToUser(Long userId);
    void regenerateToken(Long userId);
    boolean existsByEmail(String email);
}