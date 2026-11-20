package com.example.taskmanager;

import org.springframework.test.context.TestPropertySource;

/**
 * Base for integration tests that don't exercise AI. Routes the chat/embedding models to "none"
 * and excludes the pgvector store (whose autoconfig requires an EmbeddingModel), so these tests
 * need no OpenAI key and download no embedding model. Declared via @TestPropertySource so it
 * applies before Spring AI's conditions evaluate; subclasses' own @TestPropertySource is merged in.
 */
@TestPropertySource(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        // @EmbeddedKafka runs unauthenticated; override the SASL default meant for the real broker.
        "spring.kafka.security.protocol=PLAINTEXT",
        // Never index into a locally running Elasticsearch; search tests bring their own via @ServiceConnection.
        "spring.elasticsearch.uris=http://localhost:1"
})
public abstract class AbstractIntegrationTest {
}
