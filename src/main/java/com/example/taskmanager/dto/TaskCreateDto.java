package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskCreateDto {
    @NotBlank
    private String title;

    private String description;

    private boolean completed;
}
