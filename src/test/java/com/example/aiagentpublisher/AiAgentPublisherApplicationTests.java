package com.example.aiagentpublisher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiAgentPublisherApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void testsUseH2() throws Exception {
        assertThat(dataSource.getConnection().getMetaData().getURL()).contains("h2");
    }
}
