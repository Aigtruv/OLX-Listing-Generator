package com.example.aiagentpublisher.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListingStatusCheckAlignerTest {

    @Test
    void checkSqlAllowsEveryListingStatusIncludingDraft() {
        String sql = ListingStatusCheckAligner.checkConstraintSql();

        assertThat(sql).contains("listing_case_status_check");
        assertThat(sql).contains("'DRAFT'");
        assertThat(List.of(ListingStatus.values()))
                .allSatisfy(status -> assertThat(sql).contains("'" + status.name() + "'"));
    }
}
