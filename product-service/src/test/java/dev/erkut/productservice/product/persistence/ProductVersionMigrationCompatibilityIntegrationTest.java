package dev.erkut.productservice.product.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
class ProductVersionMigrationCompatibilityIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradingExistingProductAndPendingOutboxRowsPreservesDataAndInitializesVersion() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1001"))
                .load()
                .migrate();

        UUID productId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO products (id, name, price, status, created_at, updated_at)
                VALUES (?, 'Existing product', 25.00, 'ACTIVE', ?, ?)
                """, productId, Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO outbox_messages (id, status, message_type, aggregate_id, payload, created_at)
                VALUES (?, 'PENDING', 'PRODUCT_CREATED_EVENT', ?, ?::jsonb, ?)
                """, messageId, productId,
                "{\"productId\":\"" + productId + "\",\"name\":\"Existing product\"}",
                Timestamp.from(createdAt));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals(0L, jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, productId));
        assertEquals("Existing product", jdbc.queryForObject(
                "SELECT name FROM products WHERE id = ?", String.class, productId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM outbox_messages WHERE id = ?", String.class, messageId));
        assertEquals("Existing product", jdbc.queryForObject(
                "SELECT payload->>'name' FROM outbox_messages WHERE id = ?", String.class, messageId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT attempt_count FROM outbox_messages WHERE id = ?", Integer.class, messageId));
        assertFalse(jdbc.queryForObject(
                "SELECT next_attempt_at IS NULL FROM outbox_messages WHERE id = ?", Boolean.class, messageId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1002' AND success", Integer.class));
    }
}
