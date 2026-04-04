package ru.skillfactory.vkrbot.repository;

import ru.skillfactory.vkrbot.model.Comment;
import ru.skillfactory.vkrbot.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTaskOrderByCreatedAtDesc(Task task);

    List<Comment> findTop3ByTaskOrderByCreatedAtDesc(Task task);
}