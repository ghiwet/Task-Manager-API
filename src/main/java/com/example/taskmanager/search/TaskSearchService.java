package com.example.taskmanager.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-text search over indexed tasks, always scoped to the caller's owner and tenant (Elasticsearch
 * has no row-level security, so isolation is enforced in the query). Matches are highlighted. Search
 * is best-effort: if Elasticsearch is unavailable or the index doesn't exist yet, it returns empty
 * rather than erroring.
 */
@Service
public class TaskSearchService {

    private static final Logger log = LoggerFactory.getLogger(TaskSearchService.class);

    private final ElasticsearchOperations operations;

    public TaskSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public TaskSearchResponse search(String query, Boolean completed, String owner, String tenantId, Pageable pageable) {
        // Exact-match filters use the .keyword sub-field; full-text uses the analyzed text fields.
        List<Query> filters = new ArrayList<>();
        filters.add(TermQuery.of(t -> t.field("owner.keyword").value(owner))._toQuery());
        if (tenantId != null && !tenantId.isBlank()) {
            filters.add(TermQuery.of(t -> t.field("tenantId.keyword").value(tenantId))._toQuery());
        }
        if (completed != null) {
            filters.add(TermQuery.of(t -> t.field("completed").value(completed))._toQuery());
        }

        Query matcher = (query == null || query.isBlank())
                ? MatchAllQuery.of(m -> m)._toQuery()
                : MultiMatchQuery.of(m -> m.query(query).fields("title", "description"))._toQuery();

        Query boolQuery = BoolQuery.of(b -> b.must(matcher).filter(filters))._toQuery();

        // encoder=html so Elasticsearch HTML-escapes the task text; only the <em> match tags stay live,
        // making the highlight safe to render as HTML on the client.
        HighlightParameters params = HighlightParameters.builder().withEncoder("html").build();
        Highlight highlight = new Highlight(params, List.of(new HighlightField("title"), new HighlightField("description")));
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(highlight, TaskDocument.class))
                .build();

        try {
            SearchHits<TaskDocument> hits = operations.search(nativeQuery, TaskDocument.class);
            List<TaskSearchResult> results = hits.getSearchHits().stream().map(this::toResult).toList();
            return new TaskSearchResponse(results, hits.getTotalHits());
        } catch (Exception ex) {
            log.warn("Search unavailable ({}); returning empty results", ex.getMessage());
            return new TaskSearchResponse(List.of(), 0);
        }
    }

    private TaskSearchResult toResult(SearchHit<TaskDocument> hit) {
        TaskDocument doc = hit.getContent();
        List<String> highlights = hit.getHighlightFields().values().stream().flatMap(List::stream).toList();
        return new TaskSearchResult(Long.valueOf(doc.getId()), doc.getTitle(), doc.getDescription(), doc.isCompleted(), highlights);
    }
}
