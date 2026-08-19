package com.example.aiagentpublisher.olx;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OlxListingFetcherTest {

    private static final String URL =
            "https://www.olx.kz/d/obyavlenie/noutbuk-dell-inspiron-3162-IDqzxSX.html";

    private static final String HTML = """
            <html><head>
            <meta property="og:title" content="Ноутбук dell inspiron 3162">
            <meta property="og:description" content="Ноутбук рабочий, подходит для учёбы.">
            </head><body>
            <h1>Ноутбук dell inspiron 3162</h1>
            <p>8 000 тг.</p>
            </body></html>
            """;

    @Test
    void acceptsOlxListingUrls() {
        OlxListingFetcher fetcher = new OlxListingFetcher(RestClient.create());
        assertThat(fetcher.isListingUrl(URL)).isTrue();
        assertThat(fetcher.isListingUrl("https://olx.kz/d/obyavlenie/foo.html?foo=1")).isTrue();
        assertThat(fetcher.isListingUrl("https://example.com/d/obyavlenie/foo.html")).isFalse();
        assertThat(fetcher.isListingUrl("просто текст")).isFalse();
        assertThat(fetcher.isListingUrl("  ")).isFalse();
    }

    @Test
    void extractsTitlePriceDescription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(HTML, MediaType.TEXT_HTML));

        Optional<OlxListing> listing = new OlxListingFetcher(builder.build()).fetch(URL);

        assertThat(listing).isPresent();
        assertThat(listing.get().title()).isEqualTo("Ноутбук dell inspiron 3162");
        assertThat(listing.get().description()).contains("учёбы");
        assertThat(listing.get().formatForPipeline()).contains(URL).contains("8 000 тг");
        server.verify();
    }

    @Test
    void http404ReturnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(new OlxListingFetcher(builder.build()).fetch(URL)).isEmpty();
        server.verify();
    }
}
