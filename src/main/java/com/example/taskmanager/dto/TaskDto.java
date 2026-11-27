package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskDto {   // cached in Redis as JSON (CacheConfig)
    private Long id;
    private Integer version;

    @NotBlank
    private String title;

    private String description;

    private boolean completed;
    private String owner;
    private String createAt;
    private String updatedAt;
}