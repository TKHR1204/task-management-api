package com.example.taskapi;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Task {
    private final int id;
    private final String title;
    private final String description;
    private final boolean completed;
    private final LocalDate dueDate;
    private final TaskPriority priority;
    private final List<String> tags;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Task(
            int id,
            String title,
            String description,
            boolean completed,
            LocalDate dueDate,
            TaskPriority priority,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.dueDate = dueDate;
        this.priority = priority;
        this.tags = List.copyOf(tags);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(int id, TaskRequest request) {
        Instant now = Instant.now();
        return new Task(
                id,
                request.title(),
                request.description(),
                request.completed() != null && request.completed(),
                request.dueDate(),
                request.normalizedPriority(),
                request.normalizedTags(),
                now,
                now
        );
    }

    public Task update(TaskRequest request) {
        return new Task(
                id,
                request.title(),
                request.description(),
                request.completed() != null && request.completed(),
                request.dueDate(),
                request.normalizedPriority(),
                request.normalizedTags(),
                createdAt,
                Instant.now()
        );
    }

    public Task patch(TaskPatchRequest request) {
        return new Task(
                id,
                request.hasTitle() ? request.title() : title,
                request.hasDescription() ? request.description() : description,
                request.hasCompleted() ? request.completed() : completed,
                request.hasDueDate() ? request.dueDate() : dueDate,
                request.hasPriority() ? request.priority() : priority,
                request.hasTags() ? request.tags() : tags,
                createdAt,
                Instant.now()
        );
    }

    public Task markCompleted() {
        return new Task(id, title, description, true, dueDate, priority, tags, createdAt, Instant.now());
    }

    public Task reopen() {
        return new Task(id, title, description, false, dueDate, priority, tags, createdAt, Instant.now());
    }

    public boolean overdue(LocalDate today) {
        return dueDate != null && !completed && dueDate.isBefore(today);
    }

    public int id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean completed() {
        return completed;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public TaskPriority priority() {
        return priority;
    }

    public List<String> tags() {
        return tags;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
