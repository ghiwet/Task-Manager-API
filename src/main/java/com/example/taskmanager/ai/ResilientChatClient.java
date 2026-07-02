package com.example.taskmanager.ai;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Wraps the OpenAI chat call with resilience: {@code @Retry} (transient errors only) sits outside
 * {@code @CircuitBreaker} so retries flow through the breaker, and the fallback returns a degraded
 * message when retries are exhausted or the circuit is open — so a failing LLM degrades gracefully
 * instead of surfacing a 500. The ChatClient is built lazily (its builder is absent when AI is
 * disabled, e.g. in tests).
 */
@Component
public class ResilientChatClient {

    static final String UNAVAILABLE_MESSAGE =
            "The assistant is temporarily unavailable. Here are the tasks that matched your question.";

    private static final Logger log = LoggerFactory.getLogger(ResilientChatClient.class);

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private volatile ChatClient chatClient;

    public ResilientChatClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    @CircuitBreaker(name = "openai")
    @Retry(name = "openai", fallbackMethod = "generateFallback")
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient().prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    // Fires after retries are exhausted or when the circuit is open (CallNotPermittedException).
    String generateFallback(String systemPrompt, String userPrompt, Throwable t) {
        log.warn("OpenAI chat call failed; returning degraded response: {}", t.toString());
        return UNAVAILABLE_MESSAGE;
    }

    private ChatClient chatClient() {
        ChatClient client = this.chatClient;
        if (client == null) {
            ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
            if (builder == null) {
                throw new IllegalStateException("Chat model is not configured");
            }
            client = builder.build();
            this.chatClient = client;
        }
        return client;
    }
}
