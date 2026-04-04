package ru.skillfactory.vkrbot.service;

import ru.skillfactory.vkrbot.dto.SearchDto;
import ru.skillfactory.vkrbot.dto.UserDto;
import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import ru.skillfactory.vkrbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Override
    public User createUser(UserDto userDto) {
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new RuntimeException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setFullName(userDto.getFullName());
        user.setDiplomaSubject(userDto.getDiplomaSubject());
        user.setEmail(userDto.getEmail());
        user.setPhone(userDto.getPhone());
        user.setTelegram(userDto.getTelegram());
        user.setRole(userDto.getRole());
        user.setEnabled(true);

        String password = UUID.randomUUID().toString().substring(0, 8);
        user.setPassword(passwordEncoder.encode(password));

        if (user.getRole() != Role.ADMIN) {
            user.setToken(tokenService.generateToken());
        }

        if (user.getRole() == Role.STUDENT && userDto.getSupervisorId() != null) {
            User supervisor = userRepository.findById(userDto.getSupervisorId())
                    .orElseThrow(() -> new RuntimeException("Научный руководитель не найден"));
            user.setSupervisor(supervisor);
        }

        User savedUser = userRepository.save(user);

        notificationService.notifyUserCreated(savedUser);

        return savedUser;
    }

    @Override
    public User updateUser(Long id, UserDto userDto) {
        User user = getUserById(id);
        String oldRole = user.getRole().name();

        user.setFullName(userDto.getFullName());
        user.setDiplomaSubject(userDto.getDiplomaSubject());
        user.setEmail(userDto.getEmail());
        user.setPhone(userDto.getPhone());
        user.setTelegram(userDto.getTelegram());
        user.setEnabled(userDto.isEnabled());

        if (!user.getRole().equals(userDto.getRole())) {
            String newRole = userDto.getRole().name();
            user.setRole(userDto.getRole());

            notificationService.notifyRoleChanged(user, oldRole, newRole);
        }

        if (user.getRole() == Role.STUDENT) {
            if (userDto.getSupervisorId() != null) {
                User supervisor = userRepository.findById(userDto.getSupervisorId())
                        .orElseThrow(() -> new RuntimeException("Научный руководитель не найден"));

                if (user.getSupervisor() == null || !user.getSupervisor().getId().equals(supervisor.getId())) {
                    user.setSupervisor(supervisor);
                    notificationService.notifySupervisorAssigned(user, supervisor);
                }
            } else {
                user.setSupervisor(null);
            }
        }

        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public void blockUser(Long id) {
        User user = getUserById(id);
        user.setEnabled(false);
        userRepository.save(user);

        notificationService.notifyUserBlocked(user);
    }

    @Override
    public void unblockUser(Long id) {
        User user = getUserById(id);
        user.setEnabled(true);
        userRepository.save(user);

        notificationService.notifyUserUnblocked(user);
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @Override
    public User getUserByToken(String token) {
        return userRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Пользователь с таким токеном не найден"));
    }

    @Override
    public List<User> getStudentsBySupervisor(Long supervisorId) {
        User supervisor = getUserById(supervisorId);
        return userRepository.findBySupervisor(supervisor);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public List<User> searchUsers(SearchDto searchDto) {
        Role role = searchDto.getRole();
        Boolean enabled = searchDto.getEnabled();
        String query = searchDto.getQuery();

        if (role != null) {
            return userRepository.findByRole(role);
        }

        if (enabled != null) {
            return userRepository.findByEnabled(enabled);
        }

        if (query == null || query.trim().isEmpty()) {
            return userRepository.findAll();
        }

        return switch (searchDto.getSearchBy()) {
            case "fullName" -> userRepository.searchByFullName(query);
            case "email" -> userRepository.searchByEmail(query);
            case "phone" -> userRepository.searchByPhone(query);
            case "telegram" -> userRepository.searchByTelegram(query);
            default -> userRepository.searchUsers(query, role, enabled);
        };
    }

    @Override
    public void assignSupervisor(Long studentId, Long supervisorId) {
        User student = getUserById(studentId);
        if (student.getRole() != Role.STUDENT) {
            throw new RuntimeException("Пользователь не является студентом");
        }

        User supervisor = getUserById(supervisorId);
        if (supervisor.getRole() != Role.SUPERVISOR) {
            throw new RuntimeException("Пользователь не является научным руководителем");
        }

        student.setSupervisor(supervisor);
        userRepository.save(student);

        notificationService.notifySupervisorAssigned(student, supervisor);
    }

    @Override
    public void sendTokenToUser(Long userId) {
        User user = getUserById(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new RuntimeException("Администратор не имеет токена");
        }

        if (user.getToken() == null) {
            user.setToken(tokenService.generateToken());
            userRepository.save(user);
        }

        emailService.sendTokenToUser(user);
    }

    @Override
    public void regenerateToken(Long userId) {
        User user = getUserById(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new RuntimeException("Администратор не имеет токена");
        }

        user.setToken(tokenService.generateToken());
        userRepository.save(user);

        notificationService.notifyTokenRegenerated(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}