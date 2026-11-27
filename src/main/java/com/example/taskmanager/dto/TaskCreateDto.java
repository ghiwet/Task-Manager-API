package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskCreateDto {
    // Also enforced by V11; bounds outbox/Kafka/ES/embedding/prompt size (threat #54).
    public static final int DESCRIPTION_MAX = 4000;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = DESCRIPTION_MAX)
    private String description;

    private boolean completed;
}
