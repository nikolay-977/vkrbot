package ru.skillfactory.vkrbot.repository;

import ru.skillfactory.vkrbot.model.Role;
import ru.skillfactory.vkrbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByToken(String token);

    List<User> findByRole(Role role);

    List<User> findByEnabled(Boolean enabled);

    List<User> findBySupervisor(User supervisor);

    @Query("SELECT u FROM User u WHERE " +
            "(:query IS NULL OR :query = '' OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.telegram) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:enabled IS NULL OR u.enabled = :enabled)")
    List<User> searchUsers(@Param("query") String query,
                           @Param("role") Role role,
                           @Param("enabled") Boolean enabled);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByFullName(@Param("query") String query);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByEmail(@Param("query") String query);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByPhone(@Param("query") String query);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.telegram) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByTelegram(@Param("query") String query);

    boolean existsByEmail(String email);
}