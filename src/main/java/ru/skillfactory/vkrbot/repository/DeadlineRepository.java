package ru.skillfactory.vkrbot.repository;

import ru.skillfactory.vkrbot.model.Deadline;
import ru.skillfactory.vkrbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findByStudent(User student);

    List<Deadline> findBySupervisor(User supervisor);

    @Query("SELECT DISTINCT d FROM Deadline d LEFT JOIN FETCH d.tasks WHERE d.student = :student AND d.supervisor = :supervisor ORDER BY d.deadlineDate")
    List<Deadline> findByStudentAndSupervisorWithTasks(@Param("student") User student, @Param("supervisor") User supervisor);

    @Query("SELECT d FROM Deadline d LEFT JOIN FETCH d.tasks WHERE d.id = :id")
    Optional<Deadline> findByIdWithTasks(@Param("id") Long id);
}