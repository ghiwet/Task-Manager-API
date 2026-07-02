package com.example.taskmanager.outbox;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Proves the outbox write is atomic with the business change: if the task transaction fails after the
 * event is staged, the event rolls back too (no orphan event). deleteTask stages the DELETED event
 * before taskRepository.delete(...), so making the delete throw exercises exactly this path.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false",
        // Effectively disable the relay so it doesn't touch outbox rows during the test.
        "outbox.relay.poll-interval-ms=3600000"
})
class OutboxAtomicityTest extends AbstractIntegrationTest {

    @Autowired
    private TaskService taskService;

    // Spy so we can make delete fail; TaskService uses this same bean.
    @MockitoSpyBean
    private TaskRepository taskRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void stagedEventRollsBackWhenTaskTransactionFails() {
        TaskDto created = taskService.createTask(new TaskCreateDto("atomic", "desc", false), "owner-1");
        outboxRepository.deleteAll(); // clear the CREATED row so we assert only on the delete attempt

        // The delete fails after the DELETED event has been staged in the same transaction.
        doThrow(new RuntimeException("simulated DB failure")).when(taskRepository).delete(any());

        assertThrows(RuntimeException.class,
                () -> taskService.deleteTask(created.getId(), "owner-1", false));

        // The transaction rolled back: the staged event did not persist, and the task still exists.
        assertEquals(0, outboxRepository.count(), "staged DELETED event must not persist when the task tx rolls back");
        assertTrue(taskRepository.findById(created.getId()).isPresent(), "task must remain after the failed delete");
    }
}
