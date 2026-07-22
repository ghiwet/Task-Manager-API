package com.example.taskmanager.search;

import java.util.List;

/** A single search hit: the task fields plus highlighted snippets of where the query matched. */
public record TaskSearchResult(
        Long id,
        String title,
        String description,
        boolean completed,
        String owner,
        List<String> highlights) {
}
