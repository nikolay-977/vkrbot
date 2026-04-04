package ru.skillfactory.vkrbot.repository;

import ru.skillfactory.vkrbot.model.Task;
import ru.skillfactory.vkrbot.model.Deadline;
import ru.skillfactory.vkrbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStudent(User student);
    List<Task> findBySupervisor(User supervisor);
    List<Task> findByStudentAndSupervisor(User student, User supervisor);
    List<Task> findByDeadline(Deadline deadline);
}