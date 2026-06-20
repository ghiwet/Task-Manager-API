package com.example.taskmanager.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskEvent {
    private Long taskId;
    private String title;
    private String description;
    private boolean completed;
    private TaskEventType eventType;
    private String timestamp;
}
