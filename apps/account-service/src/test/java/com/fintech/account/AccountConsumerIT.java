package com.fintech.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AccountConsumerIT extends BaseIntegrationTest {

    private static final String TOPIC          = "transaction.events";
    private static final long   INITIAL_CENTS  = 1_000_000L;
    private static final long   AMOUNT_CENTS   = 99_900L;

    private static final UUID FROM_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID TO_ID =
            UUID.fromString("b2000000-0000-0000-0000-000000000002");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Limpa as tabelas antes de cada teste para garantir isolamento total
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM accounts");
    }

    // ── Cenário 1 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cenário 1: evento válido deve debitar/creditar saldos e registrar 1 linha em processed_events")
    void shouldProcessTransferEventExactlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = buildPayload(eventId, FROM_ID, TO_ID, AMOUNT_CENTS);

        kafkaTemplate.send(TOPIC, eventId.toString(), payload);

        // Aguarda o listener assíncrono processar (até 15s — inclui tempo de poll do consumer)
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Integer processedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                        Integer.class, eventId);
                assertThat(processedCount).isEqualTo(1);
            });

        // ── Assert: saldo da conta de origem ──────────────────────────────────
        Long fromBalance = jdbcTemplate.queryForObject(
                "SELECT balance_cents FROM accounts WHERE id = ?",
                Long.class, FROM_ID);
        assertThat(fromBalance)
            .as("Conta origem deve ter sido debitada exatamente 1×")
            .isEqualTo(INITIAL_CENTS - AMOUNT_CENTS);   // 900_100

        // ── Assert: saldo da conta de destino ─────────────────────────────────
        Long toBalance = jdbcTemplate.queryForObject(
                "SELECT balance_cents FROM accounts WHERE id = ?",
                Long.class, TO_ID);
        assertThat(toBalance)
            .as("Conta destino deve ter sido creditada exatamente 1×")
            .isEqualTo(INITIAL_CENTS + AMOUNT_CENTS);   // 1_099_900

        // ── Assert: exatamente 1 registro em processed_events ─────────────────
        Integer totalProcessed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(totalProcessed)
            .as("Deve existir exatamente 1 registro em processed_events para o eventId")
            .isEqualTo(1);
    }

    // ── Cenário 2 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cenário 2: evento duplicado com mesmo eventId deve ser ignorado — saldo alterado apenas 1×")
    void shouldIgnoreDuplicateTransferEventId() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = buildPayload(eventId, FROM_ID, TO_ID, AMOUNT_CENTS);

        // Publica o mesmo payload 2× consecutivos com o mesmo eventId
        kafkaTemplate.send(TOPIC, eventId.toString(), payload);
        kafkaTemplate.send(TOPIC, eventId.toString(), payload);

        // Aguarda o primeiro processamento ser confirmado em processed_events
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                        Integer.class, eventId);
                assertThat(count).isEqualTo(1);
            });

        // Aguarda mais 3s para garantir que o segundo evento foi consumido e descartado
        Thread.sleep(3_000);

        // ── Assert: barreira de idempotência — ainda apenas 1 linha ──────────
        Integer processedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(processedCount)
            .as("A barreira de idempotência deve garantir exatamente 1 linha em processed_events")
            .isEqualTo(1);

        // ── Assert: saldo alterado exatamente 1× (prova matemática) ──────────
        Long fromBalance = jdbcTemplate.queryForObject(
                "SELECT balance_cents FROM accounts WHERE id = ?",
                Long.class, FROM_ID);
        assertThat(fromBalance)
            .as("Conta origem deve ter sido debitada exatamente 1× mesmo com 2 entregas")
            .isEqualTo(INITIAL_CENTS - AMOUNT_CENTS);   // 900_100

        Long toBalance = jdbcTemplate.queryForObject(
                "SELECT balance_cents FROM accounts WHERE id = ?",
                Long.class, TO_ID);
        assertThat(toBalance)
            .as("Conta destino deve ter sido creditada exatamente 1× mesmo com 2 entregas")
            .isEqualTo(INITIAL_CENTS + AMOUNT_CENTS);   // 1_099_900
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildPayload(UUID eventId, UUID fromId, UUID toId, long amountCents)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id",            eventId.toString(),
                "fromAccountId", fromId.toString(),
                "toAccountId",   toId.toString(),
                "amountCents",   amountCents,
                "currency",      "BRL"
        ));
    }
}
