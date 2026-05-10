package com.example.taskapi;

import java.util.Locale;

public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH;

    public static TaskPriority parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TaskPriority.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "priority must be LOW, MEDIUM, or HIGH.");
        }
    }
}
