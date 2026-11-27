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
    // Rows older than V11 may be longer; cap prompt size and token cost (threat #54).
    static final int MAX_CHARS_PER_TASK = 4500;
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
                : documents.stream().map(AssistantService::truncate).collect(Collectors.joining("\n\n"));
        String answer = chatClient.generate(SYSTEM_PROMPT, "Tasks:\n" + context + "\n\nQuestion: " + question);

        List<Long> retrievedTaskIds = documents.stream()
                .map(document -> ((Number) document.getMetadata().get("taskId")).longValue())
                .distinct()
                .toList();
        return new AssistantResponse(answer, retrievedTaskIds);
    }

    static String truncate(Document document) {
        String text = document.getText() == null ? "" : document.getText();
        return text.length() <= MAX_CHARS_PER_TASK ? text : text.substring(0, MAX_CHARS_PER_TASK) + "…";
    }
}
