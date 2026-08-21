package com.example.aiagentpublisher.sourcing;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlSearchSource implements MarketplaceSource {

    private static final Logger log = LoggerFactory.getLogger(HtmlSearchSource.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_OFFERS = 5;
    private static final Pattern ARTICLE = Pattern.compile(
            "<article\\s+class=\"offer\">(.*?)</article>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile(
            "<a\\s+href=\"([^\"]+)\">([^<]*)</a>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile(
            "(EUR|USD|CNY)\\s*([0-9]+(?:[.,][0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    private final String id;
    private final String displayName;
    private final String urlTemplate;
    private final String defaultCurrency;
    private final RestClient restClient;

    public HtmlSearchSource(String id, String displayName, String urlTemplate, String defaultCurrency,
                            RestClient restClient) {
        this.id = id;
        this.displayName = displayName;
        this.urlTemplate = urlTemplate;
        this.defaultCurrency = defaultCurrency;
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<RawSourcingOffer> search(String query) {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        String encoded = URLEncoder.encode(StringUtils.trim(query), StandardCharsets.UTF_8);
        String target = urlTemplate.formatted(encoded);
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                byte[] bytes = restClient.get()
                        .uri(target)
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                        .retrieve()
                        .body(byte[].class);
                String html = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                return parse(html);
            } catch (RestClientResponseException e) {
                log.warn("Sourcing HTTP {} for {}", e.getStatusCode().value(), displayName);
                return List.of();
            } catch (RestClientException e) {
                last = e;
                log.warn("Sourcing attempt {}/{} failed for {}", attempt, MAX_ATTEMPTS, displayName);
            }
        }
        if (last != null) {
            log.warn("Sourcing failed after retries for {}", displayName, last);
        }
        return List.of();
    }

    List<RawSourcingOffer> parse(String html) {
        if (StringUtils.isBlank(html)) {
            return List.of();
        }
        List<RawSourcingOffer> offers = new ArrayList<>();
        Matcher article = ARTICLE.matcher(html);
        while (article.find() && offers.size() < MAX_OFFERS) {
            String block = article.group(1);
            Matcher link = LINK.matcher(block);
            if (!link.find()) {
                continue;
            }
            String url = StringUtils.trim(link.group(1));
            String title = StringUtils.trim(link.group(2));
            Matcher price = PRICE.matcher(block);
            String currency = defaultCurrency;
            double amount = 0;
            if (price.find()) {
                currency = StringUtils.upperCase(price.group(1));
                amount = Double.parseDouble(StringUtils.replace(price.group(2), ",", "."));
            }
            if (StringUtils.isBlank(title) || StringUtils.isBlank(url) || amount <= 0) {
                continue;
            }
            offers.add(new RawSourcingOffer(id, displayName, title, url, amount, currency));
        }
        return List.copyOf(offers);
    }
}
