package com.example.taskmanager.dto;

import java.util.List;

public record AssistantResponse(
        String answer,
        List<Long> retrievedTaskIds) {
}
