package com.example.taskmanager.security;

import jakarta.servlet.http.HttpServletRequest;

/** Request path helpers shared by the security filters. */
public final class RequestPaths {

    private RequestPaths() {
    }

    /** The request URI without the servlet context path, e.g. {@code /api/v1/tasks} under {@code /tm}. */
    public static String withinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
