package com.example.aiagentpublisher.sourcing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourcingScoutTest {

    @Test
    void sortsByKztAndKeepsThreeCheapestSkippingBrokenSource() {
        MarketplaceSource cheap = query -> List.of(
                new RawSourcingOffer("ebay", "eBay.de", "B", "https://e/b", 10, "EUR"));
        MarketplaceSource cheaper = query -> List.of(
                new RawSourcingOffer("ali", "AliExpress", "A", "https://a/a", 5, "USD"),
                new RawSourcingOffer("ali", "AliExpress", "C", "https://a/c", 20, "USD"));
        MarketplaceSource broken = query -> {
            throw new IllegalStateException("blocked");
        };
        SourcingScout scout = new SourcingScout(
                List.of(cheap, cheaper, broken), new FxRates(500, 540, 70));

        List<SourcingOffer> top = scout.search("gps трекеры");

        assertThat(top).extracting(SourcingOffer::title).containsExactly("A", "B", "C");
        assertThat(top.get(0).priceKzt()).isEqualTo(2500L);
        assertThat(top.get(1).priceKzt()).isEqualTo(5400L);
    }
}
