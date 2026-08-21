package com.example.aiagentpublisher.sourcing;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HtmlSearchSourceTest {

    @Test
    void parsesArticleOffersFromSearchHtml() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String html = """
                <article class="offer"><a href="https://www.ebay.de/itm/1">Mini GPS</a>
                <span class="price">EUR 8.50</span></article>
                """;
        server.expect(requestTo("http://localhost/sch/i.html?_nkw=gps"))
                .andRespond(withSuccess(html, MediaType.TEXT_HTML));

        HtmlSearchSource source = new HtmlSearchSource(
                "ebay", "eBay.de", "http://localhost/sch/i.html?_nkw=%s", "EUR", builder.build());
        List<RawSourcingOffer> offers = source.search("gps");

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).title()).isEqualTo("Mini GPS");
        assertThat(offers.get(0).amount()).isEqualTo(8.5);
        assertThat(offers.get(0).currency()).isEqualTo("EUR");
        server.verify();
    }
}
