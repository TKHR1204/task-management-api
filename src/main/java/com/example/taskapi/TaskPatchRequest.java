package com.example.taskapi;

import java.time.LocalDate;
import java.util.List;

public record TaskPatchRequest(
        boolean hasTitle,
        String title,
        boolean hasDescription,
        String description,
        boolean hasCompleted,
        boolean completed,
        boolean hasDueDate,
        LocalDate dueDate,
        boolean hasPriority,
        TaskPriority priority,
        boolean hasTags,
        List<String> tags
) {
}
