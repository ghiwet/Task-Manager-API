package com.example.taskmanager.controller;

import com.example.taskmanager.ai.AssistantService;
import com.example.taskmanager.dto.AssistantQueryDto;
import com.example.taskmanager.dto.AssistantResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/query")
    public ResponseEntity<AssistantResponse> query(@RequestBody @Valid AssistantQueryDto query,
                                                   Authentication authentication) {
        return ResponseEntity.ok(assistantService.ask(query.question(), authentication.getName()));
    }
}
