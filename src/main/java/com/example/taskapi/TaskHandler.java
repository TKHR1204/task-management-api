package com.example.taskapi;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TaskHandler implements HttpHandler {
    private static final Set<String> TASK_FIELDS = Set.of(
            "title",
            "description",
            "completed",
            "dueDate",
            "priority",
            "tags"
    );

    private final TaskStore store;

    public TaskHandler(TaskStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        try {
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            route(exchange);
        } catch (ApiException e) {
            sendJson(exchange, e.statusCode(), Json.error(e.getMessage()));
        } catch (RuntimeException e) {
            sendJson(exchange, 500, Json.error("Unexpected server error."));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        List<String> path = pathSegments(exchange);

        if (path.isEmpty()) {
            sendJson(exchange, 200, Json.object(Map.of(
                    "name", "task-api",
                    "endpoints", "/health, /tasks, /tasks/stats"
            )));
            return;
        }

        if (path.size() == 1 && path.getFirst().equals("health")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, Json.object(Map.of("status", "ok")));
            return;
        }

        if (!path.getFirst().equals("tasks")) {
            throw new ApiException(404, "Endpoint not found.");
        }

        if (path.size() == 2 && path.get(1).equals("stats")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, Json.stats(store.stats()));
            return;
        }

        if (path.size() == 1) {
            handleTasks(exchange, method);
            return;
        }

        int id = parseId(path.get(1));
        if (path.size() == 2) {
            handleTaskById(exchange, method, id);
            return;
        }

        if (path.size() == 3 && path.get(2).equals("complete")) {
            requireMethod(method, "PATCH");
            Task task = store.complete(id).orElseThrow(() -> new ApiException(404, "Task not found."));
            sendJson(exchange, 200, Json.task(task));
            return;
        }

        if (path.size() == 3 && path.get(2).equals("reopen")) {
            requireMethod(method, "PATCH");
            Task task = store.reopen(id).orElseThrow(() -> new ApiException(404, "Task not found."));
            sendJson(exchange, 200, Json.task(task));
            return;
        }

        throw new ApiException(404, "Endpoint not found.");
    }

    private void handleTasks(HttpExchange exchange, String method) throws IOException {
        if (method.equals("GET")) {
            sendJson(exchange, 200, Json.tasks(store.findAll(readTaskQuery(exchange))));
            return;
        }

        if (method.equals("POST")) {
            TaskRequest request = readTaskRequest(exchange);
            Task task = store.create(request);
            sendJson(exchange, 201, Json.task(task));
            return;
        }

        throw methodNotAllowed("GET, POST");
    }

    private void handleTaskById(HttpExchange exchange, String method, int id) throws IOException {
        if (method.equals("GET")) {
            Optional<Task> task = store.findById(id);
            sendJson(exchange, 200, Json.task(task.orElseThrow(() -> new ApiException(404, "Task not found."))));
            return;
        }

        if (method.equals("PUT")) {
            TaskRequest request = readTaskRequest(exchange);
            Task task = store.update(id, request).orElseThrow(() -> new ApiException(404, "Task not found."));
            sendJson(exchange, 200, Json.task(task));
            return;
        }

        if (method.equals("PATCH")) {
            TaskPatchRequest request = readTaskPatchRequest(exchange);
            Task task = store.patch(id, request).orElseThrow(() -> new ApiException(404, "Task not found."));
            sendJson(exchange, 200, Json.task(task));
            return;
        }

        if (method.equals("DELETE")) {
            if (!store.delete(id)) {
                throw new ApiException(404, "Task not found.");
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        throw methodNotAllowed("GET, PUT, PATCH, DELETE");
    }

    private TaskRequest readTaskRequest(HttpExchange exchange) throws IOException {
        String body = readBody(exchange.getRequestBody());
        Map<String, Object> values = Json.parseObject(body);
        rejectUnknownFields(values, TASK_FIELDS);

        String title = readTitle(values.get("title"));
        String description = readDescription(values.get("description"));
        Boolean completed = booleanValue(values.get("completed"));
        LocalDate dueDate = dateValue(values.get("dueDate"));
        TaskPriority priority = TaskPriority.parse(stringValue(values.get("priority")));
        List<String> tags = stringListValue(values.get("tags"));

        return new TaskRequest(title, description, completed, dueDate, priority, tags);
    }

    private TaskPatchRequest readTaskPatchRequest(HttpExchange exchange) throws IOException {
        String body = readBody(exchange.getRequestBody());
        Map<String, Object> values = Json.parseObject(body);
        rejectUnknownFields(values, TASK_FIELDS);

        if (values.isEmpty()) {
            throw new ApiException(400, "At least one task field is required.");
        }

        boolean hasTitle = values.containsKey("title");
        boolean hasDescription = values.containsKey("description");
        boolean hasCompleted = values.containsKey("completed");
        boolean hasDueDate = values.containsKey("dueDate");
        boolean hasPriority = values.containsKey("priority");
        boolean hasTags = values.containsKey("tags");

        Boolean completed = hasCompleted ? booleanValue(values.get("completed")) : null;
        if (hasCompleted && completed == null) {
            throw new ApiException(400, "completed cannot be null.");
        }

        TaskPriority priority = hasPriority ? TaskPriority.parse(stringValue(values.get("priority"))) : null;
        if (hasPriority && priority == null) {
            throw new ApiException(400, "priority cannot be blank.");
        }

        return new TaskPatchRequest(
                hasTitle,
                hasTitle ? readTitle(values.get("title")) : null,
                hasDescription,
                hasDescription ? readDescription(values.get("description")) : null,
                hasCompleted,
                completed != null && completed,
                hasDueDate,
                hasDueDate ? dateValue(values.get("dueDate")) : null,
                hasPriority,
                priority,
                hasTags,
                hasTags ? stringListValue(values.get("tags")) : List.of()
        );
    }

    private TaskQuery readTaskQuery(HttpExchange exchange) {
        Map<String, String> query = queryParameters(exchange);
        Boolean completed = booleanQuery(query.get("completed"));
        String text = blankToNull(query.get("q"));
        LocalDate dueBefore = queryDateValue(query.get("dueBefore"), "dueBefore");
        LocalDate dueAfter = queryDateValue(query.get("dueAfter"), "dueAfter");
        TaskPriority priority = TaskPriority.parse(query.get("priority"));
        String tag = blankToNull(query.get("tag"));
        String sort = sortValue(query.get("sort"));
        boolean descending = orderDescending(query.get("order"));

        return new TaskQuery(completed, text, dueBefore, dueAfter, priority, tag, sort, descending);
    }

    private static String readBody(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ApiException(400, "String value expected.");
        }
        return text;
    }

    private static String readTitle(Object value) {
        String title = stringValue(value);
        if (title == null || title.isBlank()) {
            throw new ApiException(400, "title is required.");
        }
        if (title.strip().length() > 120) {
            throw new ApiException(400, "title must be 120 characters or fewer.");
        }
        return title.strip();
    }

    private static String readDescription(Object value) {
        String description = stringValue(value);
        if (description == null) {
            return "";
        }
        if (description.strip().length() > 1000) {
            throw new ApiException(400, "description must be 1000 characters or fewer.");
        }
        return description.strip();
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean bool)) {
            throw new ApiException(400, "Boolean value expected.");
        }
        return bool;
    }

    private static Boolean booleanQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ApiException(400, "completed must be true or false.");
    }

    private static LocalDate dateValue(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "dueDate must be YYYY-MM-DD.");
        }
    }

    private static LocalDate queryDateValue(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, field + " must be YYYY-MM-DD.");
        }
    }

    private static List<String> stringListValue(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawValues)) {
            throw new ApiException(400, "tags must be an array of strings.");
        }

        List<String> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object rawValue : rawValues) {
            String tag = stringValue(rawValue);
            if (tag == null || tag.isBlank()) {
                throw new ApiException(400, "tags cannot include blank values.");
            }

            String normalized = tag.strip();
            if (normalized.length() > 30) {
                throw new ApiException(400, "each tag must be 30 characters or fewer.");
            }
            if (seen.add(normalized.toLowerCase(Locale.ROOT))) {
                tags.add(normalized);
            }
        }

        if (tags.size() > 10) {
            throw new ApiException(400, "tags can include up to 10 values.");
        }

        return tags;
    }

    private static String sortValue(String value) {
        if (value == null || value.isBlank()) {
            return "id";
        }

        return switch (value) {
            case "id", "title", "dueDate", "priority", "createdAt", "updatedAt" -> value;
            default -> throw new ApiException(400, "sort must be id, title, dueDate, priority, createdAt, or updatedAt.");
        };
    }

    private static boolean orderDescending(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("asc")) {
            return false;
        }
        if (value.equalsIgnoreCase("desc")) {
            return true;
        }
        throw new ApiException(400, "order must be asc or desc.");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static void rejectUnknownFields(Map<String, Object> values, Set<String> allowedFields) {
        for (String field : values.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new ApiException(400, "Unknown field: " + field + ".");
            }
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw methodNotAllowed(expected);
        }
    }

    private static ApiException methodNotAllowed(String allowed) {
        return new ApiException(405, "Method not allowed. Allowed: " + allowed + ".");
    }

    private static int parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id < 1) {
                throw new ApiException(400, "Task id must be positive.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Task id must be a number.");
        }
    }

    private static List<String> pathSegments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return List.of(path.split("/")).stream()
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }

        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }

            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            values.putIfAbsent(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
