package com.example.taskmanager.ai;

import com.example.taskmanager.dto.AssistantResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Answers natural-language questions over the caller's tasks (RAG): retrieves the relevant
 * tenant/owner-scoped tasks, then asks the chat model to answer using only that context.
 * Retrieval (DB) and generation (external LLM call) are separate; the LLM call is wrapped with
 * resilience in {@link ResilientChatClient}, so a failing model degrades gracefully.
 */
@Service
public class AssistantService {

    private static final int TOP_K = 5;
    private static final String SYSTEM_PROMPT = """
            You are an assistant for a task manager. Answer the user's question using only the tasks
            provided as context. If the context does not contain the answer, say you don't have that
            information. Be concise.""";

    private final AssistantRetriever retriever;
    private final ResilientChatClient chatClient;

    public AssistantService(AssistantRetriever retriever, ResilientChatClient chatClient) {
        this.retriever = retriever;
        this.chatClient = chatClient;
    }

    public AssistantResponse ask(String question, String owner) {
        List<Document> documents = retriever.retrieve(question, owner, TOP_K);

        String context = documents.isEmpty()
                ? "(no matching tasks)"
                : documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        String answer = chatClient.generate(SYSTEM_PROMPT, "Tasks:\n" + context + "\n\nQuestion: " + question);

        List<Long> retrievedTaskIds = documents.stream()
                .map(document -> ((Number) document.getMetadata().get("taskId")).longValue())
                .distinct()
                .toList();
        return new AssistantResponse(answer, retrievedTaskIds);
    }
}
