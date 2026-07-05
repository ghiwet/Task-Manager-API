package com.example.taskmanager.search;

import com.example.taskmanager.tenant.TenantContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskSearchController {

    private final TaskSearchService searchService;

    public TaskSearchController(TaskSearchService searchService) {
        this.searchService = searchService;
    }

    // GET /api/v1/tasks/search?q=milk&completed=false — scoped to the caller's tasks.
    @GetMapping("/search")
    public TaskSearchResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "completed", required = false) Boolean completed,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        return searchService.search(q, completed, authentication.getName(), TenantContext.getTenantId(), pageable);
    }
}
