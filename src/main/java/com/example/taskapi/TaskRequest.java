package com.example.taskapi;

import java.time.LocalDate;
import java.util.List;

public record TaskRequest(
        String title,
        String description,
        Boolean completed,
        LocalDate dueDate,
        TaskPriority priority,
        List<String> tags
) {
    public TaskPriority normalizedPriority() {
        return priority == null ? TaskPriority.MEDIUM : priority;
    }

    public List<String> normalizedTags() {
        return tags == null ? List.of() : List.copyOf(tags);
    }
}
