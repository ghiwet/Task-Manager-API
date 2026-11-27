package com.example.taskmanager.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

// Threat #54: retrieved context is capped per task before it reaches the prompt.
class AssistantServiceTest {

    @Test
    void shortTextIsKeptAsIs() {
        assertThat(AssistantService.truncate(new Document("buy milk"))).isEqualTo("buy milk");
    }

    @Test
    void longTextIsCapped() {
        String text = "a".repeat(AssistantService.MAX_CHARS_PER_TASK + 500);
        String truncated = AssistantService.truncate(new Document(text));
        assertThat(truncated).hasSize(AssistantService.MAX_CHARS_PER_TASK + 1).endsWith("…");
    }
}
