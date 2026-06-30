package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantQueryDto(
        @NotBlank @Size(max = 1000) String question) {
}
