package com.example.taskmanager.search;

import java.util.List;

public record TaskSearchResponse(List<TaskSearchResult> results, long total) {
}
