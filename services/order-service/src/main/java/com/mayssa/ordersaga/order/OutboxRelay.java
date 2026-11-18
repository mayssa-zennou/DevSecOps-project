package com.mayssa.ordersaga.order;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling relay of the transactional outbox: publishes pending outbox rows to Kafka.
 * At-least-once delivery, so consumers must be idempotent.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional // DB only (not Kafka): keeps the FOR UPDATE locks until the end of the batch
    public void relay() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, topic, msg_key, payload FROM outbox "
                + "WHERE published_at IS NULL ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED");

        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            try {
                // .get() waits for the broker ack, so we never mark an unconfirmed send
                kafkaTemplate.send((String) row.get("topic"), (String) row.get("msg_key"), (String) row.get("payload"))
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Stop at the first failure to preserve ordering. Return (not rethrow) so rows already marked are committed, whatever the exception type.
                log.warn("Outbox row {} not published, will retry: {}", id, e.toString());
                return;
            }
            // Must stay after the send: marking first would lose the event on a crash
            jdbc.update("UPDATE outbox SET published_at = now() WHERE id = ?", id);
        }
    }
}