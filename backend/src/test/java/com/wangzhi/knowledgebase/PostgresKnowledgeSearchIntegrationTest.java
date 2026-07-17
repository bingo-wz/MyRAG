package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo-data=false",
        "app.import.worker-enabled=false",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
class PostgresKnowledgeSearchIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("myrag")
            .withUsername("myrag")
            .withPassword("myrag-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KnowledgeService knowledgeService;

    @Test
    void searchesTextAndOptionalFiltersOnPostgres() {
        knowledgeService.create(new CreateRequest(
                "退换货规则", "签收后七日内且商品完好时，可以申请无理由退货。",
                "售后服务", "政策中心", "退货", "测试用户"));

        assertThat(knowledgeService.search(null, null, null, 0, 10).totalElements()).isEqualTo(1);
        assertThat(knowledgeService.search("七日", null, "售后服务", 0, 10).items())
                .singleElement()
                .satisfies(view -> assertThat(view.title()).isEqualTo("退换货规则"));
        assertThat(knowledgeService.search("%_", null, null, 0, 10).items()).isEmpty();
    }
}
