package com.example.taskmanager.ai;

import com.example.taskmanager.dto.AssistantResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Answers natural-language questions over the caller's tasks (RAG): retrieves the relevant
 * tenant/owner-scoped tasks, then asks the chat model to answer using only that context.
 * Retrieval (DB) and generation (external LLM call) are deliberately separate so the database
 * connection isn't held during the model call.
 */
@Service
public class AssistantService {

    private static final int TOP_K = 5;
    private static final String SYSTEM_PROMPT = """
            You are an assistant for a task manager. Answer the user's question using only the tasks
            provided as context. If the context does not contain the answer, say you don't have that
            information. Be concise.""";

    private final AssistantRetriever retriever;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    // Built lazily and cached: the builder bean is absent when AI is disabled (e.g. in tests),
    // so it can't be resolved in the constructor.
    private volatile ChatClient chatClient;

    public AssistantService(AssistantRetriever retriever, ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.retriever = retriever;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public AssistantResponse ask(String question, String owner) {
        List<Document> documents = retriever.retrieve(question, owner, TOP_K);

        String context = documents.isEmpty()
                ? "(no matching tasks)"
                : documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        String answer = chatClient().prompt()
                .system(SYSTEM_PROMPT)
                .user("Tasks:\n" + context + "\n\nQuestion: " + question)
                .call()
                .content();

        List<Long> retrievedTaskIds = documents.stream()
                .map(document -> ((Number) document.getMetadata().get("taskId")).longValue())
                .distinct()
                .toList();
        return new AssistantResponse(answer, retrievedTaskIds);
    }

    private ChatClient chatClient() {
        ChatClient client = this.chatClient;
        if (client == null) {
            // Spring AI's auto-configured builder (observation registry + defaults already wired).
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
