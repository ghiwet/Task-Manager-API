package com.example.taskmanager.event;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.config.KafkaConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.service.TaskService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@EmbeddedKafka(
        partitions = 3,
        topics = {KafkaConfig.TASK_EVENTS_TOPIC, KafkaConfig.TASK_EVENTS_TOPIC + ".DLT"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "rate-limit.enabled=false",
        // Fast outbox relay so staged events publish promptly in this test.
        "outbox.relay.poll-interval-ms=200"
})
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskEventIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_OWNER = "test-user";

    @Autowired
    private TaskService taskService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaMessageListenerContainer<String, TaskEvent> container;
    private BlockingQueue<ConsumerRecord<String, TaskEvent>> records;

    @BeforeAll
    void setUp() {
        JsonDeserializer<TaskEvent> deserializer = new JsonDeserializer<>(TaskEvent.class);
        deserializer.addTrustedPackages("com.example.taskmanager.event");

        DefaultKafkaConsumerFactory<String, TaskEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                        ConsumerConfig.GROUP_ID_CONFIG, "test-consumer",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                ),
                new StringDeserializer(),
                deserializer
        );

        ContainerProperties containerProperties = new ContainerProperties(KafkaConfig.TASK_EVENTS_TOPIC);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, TaskEvent>) records::add);
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @AfterAll
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    void whenTaskCreated_thenCreatedEventPublished() throws Exception {
        TaskCreateDto dto = new TaskCreateDto("Integration Test Task", "Description", false);

        taskService.createTask(dto, TEST_OWNER);

        ConsumerRecord<String, TaskEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThat(record.value().getTitle()).isEqualTo("Integration Test Task");
        assertThat(record.value().isCompleted()).isFalse();
    }

    @Test
    @Order(2)
    void whenTaskCompleted_thenCompletedEventPublished() throws Exception {
        TaskDto created = taskService.createTask(new TaskCreateDto("Complete Me", "Desc", false), TEST_OWNER);
        records.poll(10, TimeUnit.SECONDS);

        taskService.updateTask(created.getId(), TEST_OWNER, new TaskCreateDto("Complete Me", "Desc", true));

        ConsumerRecord<String, TaskEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().getEventType()).isEqualTo(TaskEventType.COMPLETED);
        assertThat(record.value().isCompleted()).isTrue();
    }

    @Test
    @Order(3)
    void whenTaskUpdated_thenUpdatedEventPublished() throws Exception {
        TaskDto created = taskService.createTask(new TaskCreateDto("Update Me", "Desc", false), TEST_OWNER);
        records.poll(10, TimeUnit.SECONDS);

        taskService.updateTask(created.getId(), TEST_OWNER, new TaskCreateDto("Updated Title", "New Desc", false));

        ConsumerRecord<String, TaskEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().getEventType()).isEqualTo(TaskEventType.UPDATED);
        assertThat(record.value().getTitle()).isEqualTo("Updated Title");
    }

    @Test
    @Order(4)
    void whenTaskDeleted_thenDeletedEventPublished() throws Exception {
        TaskDto created = taskService.createTask(new TaskCreateDto("Delete Me", "Desc", false), TEST_OWNER);
        records.poll(10, TimeUnit.SECONDS);

        taskService.deleteTask(created.getId(), TEST_OWNER, false);

        ConsumerRecord<String, TaskEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().getEventType()).isEqualTo(TaskEventType.DELETED);
        assertThat(record.value().getTitle()).isEqualTo("Delete Me");
    }

    @Test
    @Order(5)
    void whenTaskCreated_thenKeyIsTaskId() throws Exception {
        TaskDto created = taskService.createTask(new TaskCreateDto("Key Test", "Desc", false), TEST_OWNER);

        ConsumerRecord<String, TaskEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(String.valueOf(created.getId()));
    }
}
