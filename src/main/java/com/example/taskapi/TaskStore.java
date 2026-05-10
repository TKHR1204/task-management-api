package com.example.taskapi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TaskStore {
    private final ConcurrentHashMap<Integer, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public List<Task> findAll() {
        return findAll(new TaskQuery(null, null, null, null, null, null, "id", false));
    }

    public List<Task> findAll(TaskQuery query) {
        List<Task> result = tasks.values().stream()
                .filter(task -> matchesCompleted(task, query.completed()))
                .filter(task -> matchesSearch(task, query.q()))
                .filter(task -> matchesDueBefore(task, query.dueBefore()))
                .filter(task -> matchesDueAfter(task, query.dueAfter()))
                .filter(task -> query.priority() == null || task.priority() == query.priority())
                .filter(task -> matchesTag(task, query.tag()))
                .sorted(comparator(query))
                .toList();

        return new ArrayList<>(result);
    }

    public Optional<Task> findById(int id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public Task create(TaskRequest request) {
        int id = nextId.getAndIncrement();
        Task task = Task.create(id, request);
        tasks.put(id, task);
        return task;
    }

    public Optional<Task> update(int id, TaskRequest request) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (ignored, existing) -> existing.update(request)));
    }

    public Optional<Task> patch(int id, TaskPatchRequest request) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (ignored, existing) -> existing.patch(request)));
    }

    public Optional<Task> complete(int id) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (ignored, existing) -> existing.markCompleted()));
    }

    public Optional<Task> reopen(int id) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (ignored, existing) -> existing.reopen()));
    }

    public boolean delete(int id) {
        return tasks.remove(id) != null;
    }

    public TaskStats stats() {
        List<Task> currentTasks = findAll();
        int completed = 0;
        int overdue = 0;
        int highPriority = 0;

        for (Task task : currentTasks) {
            if (task.completed()) {
                completed++;
            }
            if (task.overdue(LocalDate.now())) {
                overdue++;
            }
            if (task.priority() == TaskPriority.HIGH) {
                highPriority++;
            }
        }

        return new TaskStats(
                currentTasks.size(),
                currentTasks.size() - completed,
                completed,
                overdue,
                highPriority
        );
    }

    private static boolean matchesCompleted(Task task, Boolean completed) {
        return completed == null || task.completed() == completed;
    }

    private static boolean matchesSearch(Task task, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        return task.title().toLowerCase(Locale.ROOT).contains(normalized)
                || task.description().toLowerCase(Locale.ROOT).contains(normalized)
                || task.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(normalized));
    }

    private static boolean matchesDueBefore(Task task, LocalDate dueBefore) {
        return dueBefore == null || (task.dueDate() != null && !task.dueDate().isAfter(dueBefore));
    }

    private static boolean matchesDueAfter(Task task, LocalDate dueAfter) {
        return dueAfter == null || (task.dueDate() != null && !task.dueDate().isBefore(dueAfter));
    }

    private static boolean matchesTag(Task task, String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }

        String normalized = tag.toLowerCase(Locale.ROOT);
        return task.tags().stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
    }

    private static Comparator<Task> comparator(TaskQuery query) {
        Comparator<Task> comparator = switch (query.sort()) {
            case "title" -> Comparator.comparing(Task::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Task::id);
            case "dueDate" -> Comparator.comparing(
                    Task::dueDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparingInt(Task::id);
            case "priority" -> Comparator.comparing(Task::priority).thenComparingInt(Task::id);
            case "createdAt" -> Comparator.comparing(Task::createdAt).thenComparingInt(Task::id);
            case "updatedAt" -> Comparator.comparing(Task::updatedAt).thenComparingInt(Task::id);
            default -> Comparator.comparingInt(Task::id);
        };

        return query.descending() ? comparator.reversed() : comparator;
    }
}
