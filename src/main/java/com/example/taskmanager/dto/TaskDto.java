package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskDto {
    private Long id;
    private int version;

    @NotBlank
    private String title;

    private String description;

    private boolean completed;
    private String createAt;
    private String updatedAt;
}