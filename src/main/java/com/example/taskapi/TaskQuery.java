package com.example.taskapi;

import java.time.LocalDate;

public record TaskQuery(
        Boolean completed,
        String q,
        LocalDate dueBefore,
        LocalDate dueAfter,
        TaskPriority priority,
        String tag,
        String sort,
        boolean descending
) {
}
