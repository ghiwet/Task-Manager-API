package com.example.taskmanager.exception;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new SimpleMeterRegistry());

    @Test
    void generalHandlerReturnsGenericMessageAndDoesNotLeakInternals() {
        ProblemDetail problem = handler.handleGeneral(
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_users_secret\""));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("Internal server error", problem.getDetail());
        assertFalse(String.valueOf(problem.getDetail()).contains("constraint"),
                "internal exception text must never leak to the client");
    }

    @Test
    void optimisticLockFailureMapsToConflict() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Task", 1L));

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertNotNull(problem.getDetail());
    }
}
