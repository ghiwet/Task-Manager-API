package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class TaskDto implements Serializable {   // cached in Redis (cache-aside)
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