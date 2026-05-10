package com.example.taskapi;

public record TaskStats(
        int total,
        int open,
        int completed,
        int overdue,
        int highPriority
) {
}
