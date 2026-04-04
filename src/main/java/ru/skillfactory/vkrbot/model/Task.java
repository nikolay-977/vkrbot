package ru.skillfactory.vkrbot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "completion_criteria", length = 2000)
    private String completionCriteria;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "criteria_status")
    private String criteriaStatus;

    @ManyToOne
    @JoinColumn(name = "deadline_id", nullable = false)
    private Deadline deadline;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = Status.CREATED;
    }

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    @Column(name = "attached_files", length = 2000)
    private String attachedFiles;
}