package com.example.taskmanager.ai;

import com.example.taskmanager.tenant.TenantContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tenant- and owner-scoped similarity search over task embeddings ("personal" RAG retrieval):
 * tenant isolation comes from RLS ({@code SET LOCAL app.current_tenant} on the same transactional
 * connection as the search), and owner scoping from a metadata filter — together matching the
 * per-user visibility of the task API. The vector store is optional (absent when AI is disabled).
 */
@Component
public class AssistantRetriever {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;

    public AssistantRetriever(ObjectProvider<VectorStore> vectorStoreProvider, JdbcTemplate jdbcTemplate) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<Document> retrieve(String question, String owner, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("Vector store is not configured");
        }
        jdbcTemplate.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class,
                TenantContext.getTenantId());
        var ownerFilter = new FilterExpressionBuilder().eq("owner", owner).build();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(ownerFilter)
                .build());
    }
}
