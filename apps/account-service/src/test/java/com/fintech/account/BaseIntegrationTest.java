package com.fintech.account;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("fintech")
                    .withUsername("fintech_admin")
                    .withPassword("fintech_pass");

    // KafkaContainer embeds its own ZooKeeper — espelha a versão do docker-compose.yml
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",            POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",       POSTGRES::getUsername);
        registry.add("spring.datasource.password",       POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers",   KAFKA::getBootstrapServers);
    }
}
