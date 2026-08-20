package com.example.aiagentpublisher.domain;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ListingStatusCheckAligner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public ListingStatusCheckAligner(JdbcTemplate jdbcTemplate,
                                     @Value("${spring.datasource.url}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
    }

    static String checkConstraintSql() {
        String allowed = List.of(ListingStatus.values()).stream()
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        return "ALTER TABLE listing_case ADD CONSTRAINT listing_case_status_check CHECK (status IN ("
                + allowed + "))";
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.contains(datasourceUrl, "postgresql")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE listing_case DROP CONSTRAINT IF EXISTS listing_case_status_check");
        jdbcTemplate.execute(checkConstraintSql());
    }
}
