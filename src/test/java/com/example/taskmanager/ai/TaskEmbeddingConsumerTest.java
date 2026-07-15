package com.example.taskmanager.ai;

import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskEmbeddingConsumerTest {

    private ConsumerRecord<String, TaskEvent> recordFor(TaskEvent event) {
        return new ConsumerRecord<>("task-events", 0, 0L, String.valueOf(event.getTaskId()), event);
    }

    @Test
    void failingEmbeddingDoesNotPropagateAndDoesNotStallPartition() {
        TaskEmbeddingService service = mock(TaskEmbeddingService.class);
        doThrow(new RuntimeException("pgvector down")).when(service).upsert(any());
        TaskEmbeddingConsumer consumer = new TaskEmbeddingConsumer(service);

        TaskEvent event = TaskEvent.builder()
                .taskId(1L).title("t").owner("u").tenantId("tenant-a")
                .eventType(TaskEventType.CREATED).build();

        // Fail open: the exception is swallowed so the offset can advance.
        assertDoesNotThrow(() -> consumer.onTaskEvent(recordFor(event)));
    }

    @Test
    void blankTenantIsSkippedWithoutTouchingTheVectorStore() {
        TaskEmbeddingService service = mock(TaskEmbeddingService.class);
        TaskEmbeddingConsumer consumer = new TaskEmbeddingConsumer(service);

        TaskEvent event = TaskEvent.builder()
                .taskId(2L).title("t").owner("u").tenantId("  ")
                .eventType(TaskEventType.CREATED).build();

        consumer.onTaskEvent(recordFor(event));

        verify(service, never()).upsert(any());
        verify(service, never()).delete(any());
    }
}
