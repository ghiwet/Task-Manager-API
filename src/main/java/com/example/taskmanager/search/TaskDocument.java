package com.example.taskmanager.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * A task as indexed for full-text search. Carries owner + tenantId so search can be scoped to the
 * caller (Elasticsearch has no row-level security). createIndex=false: the app boots without
 * Elasticsearch, and the index is created on first write (search is a rebuildable derived view).
 */
@Document(indexName = "tasks", createIndex = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String owner;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Boolean)
    private boolean completed;
}
