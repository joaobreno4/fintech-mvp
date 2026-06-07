package com.fintech.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransferControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /transfers deve retornar 201 e persistir evento no Outbox atomicamente")
    void shouldCreateTransferAndPersistOutboxEventAtomically() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = """
                {
                    "fromAccountId": "a0000000-0000-0000-0000-000000000001",
                    "toAccountId":   "b0000000-0000-0000-0000-000000000002",
                    "amountCents":   150000,
                    "currency":      "BRL"
                }
                """;

        MvcResult result = mockMvc.perform(
                        post("/transfers")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amountCents").value(150000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andReturn();

        // Extrai o ID da transferência criada para validar o registro de Outbox correspondente
        String responseBody = result.getResponse().getContentAsString();
        String transferId = objectMapper.readTree(responseBody).get("id").asText();

        // Valida atomicidade: a transferência e o evento de Outbox foram persistidos na mesma transação.
        // Asserta event_type (imutável) em vez do status para evitar flakiness caso o relay
        // processe o evento antes desta linha e mude o status de PENDING para SENT.
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'TransferCreated'",
                Integer.class,
                transferId);

        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /transfers com chave idempotente duplicada deve retornar a mesma transferência sem inserir novo Outbox")
    void shouldReturnExistingTransferOnDuplicateIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = """
                {
                    "fromAccountId": "c0000000-0000-0000-0000-000000000003",
                    "toAccountId":   "d0000000-0000-0000-0000-000000000004",
                    "amountCents":   5000,
                    "currency":      "BRL"
                }
                """;

        // Primeira requisição — cria a transferência
        MvcResult firstResult = mockMvc.perform(
                        post("/transfers")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("id").asText();

        // Segunda requisição com a mesma chave — deve retornar a mesma transferência (idempotência)
        MvcResult secondResult = mockMvc.perform(
                        post("/transfers")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String secondId = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .get("id").asText();

        assertThat(secondId).isEqualTo(firstId);

        // Garante que NÃO houve inserção duplicada no Outbox
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'TransferCreated'",
                Integer.class,
                firstId);

        assertThat(outboxCount).isEqualTo(1);
    }
}
