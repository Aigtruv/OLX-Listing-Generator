package com.example.aiagentpublisher.olx;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OlxListingFetcher {

    private static final Logger log = LoggerFactory.getLogger(OlxListingFetcher.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_DESCRIPTION_CHARS = 2000;
    private static final Pattern OG_TITLE = Pattern.compile(
            "property\\s*=\\s*\"og:title\"\\s+content\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_TITLE_REV = Pattern.compile(
            "content\\s*=\\s*\"([^\"]+)\"\\s+property\\s*=\\s*\"og:title\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_DESC = Pattern.compile(
            "property\\s*=\\s*\"og:description\"\\s+content\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_DESC_REV = Pattern.compile(
            "content\\s*=\\s*\"([^\"]+)\"\\s+property\\s*=\\s*\"og:description\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("[0-9][0-9\\s\\u00a0]*тг");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private final RestClient restClient;

    public OlxListingFetcher(@Qualifier("olxRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean isListingUrl(String raw) {
        if (StringUtils.isBlank(raw)) {
            return false;
        }
        try {
            URI uri = URI.create(StringUtils.trim(raw));
            String host = StringUtils.lowerCase(uri.getHost());
            String path = StringUtils.defaultString(uri.getPath());
            boolean hostOk = StringUtils.equals(host, "olx.kz") || StringUtils.equals(host, "www.olx.kz");
            boolean pathOk = StringUtils.startsWith(path, "/d/obyavlenie/");
            boolean schemeOk = StringUtils.equalsIgnoreCase(uri.getScheme(), "https")
                    || StringUtils.equalsIgnoreCase(uri.getScheme(), "http");
            return hostOk && pathOk && schemeOk;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<OlxListing> fetch(String url) {
        if (!isListingUrl(url)) {
            return Optional.empty();
        }
        String target = StringUtils.trim(url);
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                byte[] bytes = restClient.get()
                        .uri(target)
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                        .retrieve()
                        .body(byte[].class);
                String html = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                return parse(target, html);
            } catch (RestClientResponseException e) {
                log.warn("OLX fetch HTTP {} for {}", e.getStatusCode().value(), target);
                return Optional.empty();
            } catch (RestClientException e) {
                last = e;
                log.warn("OLX fetch attempt {}/{} failed", attempt, MAX_ATTEMPTS);
            }
        }
        if (last != null) {
            log.warn("OLX fetch failed after retries", last);
        }
        return Optional.empty();
    }

    Optional<OlxListing> parse(String url, String html) {
        if (StringUtils.isBlank(html)) {
            return Optional.empty();
        }
        String title = unescape(firstGroup(html, OG_TITLE, OG_TITLE_REV));
        if (StringUtils.isBlank(title)) {
            return Optional.empty();
        }
        String description = unescape(firstGroup(html, OG_DESC, OG_DESC_REV));
        String stripped = StringUtils.normalizeSpace(TAGS.matcher(html).replaceAll(" "));
        if (StringUtils.isBlank(description)) {
            description = StringUtils.abbreviate(stripped, MAX_DESCRIPTION_CHARS);
        }
        String price = "";
        Matcher priceMatcher = PRICE.matcher(stripped);
        if (priceMatcher.find()) {
            price = StringUtils.normalizeSpace(priceMatcher.group());
        }
        return Optional.of(new OlxListing(url, title, price, description));
    }

    private static String firstGroup(String html, Pattern first, Pattern second) {
        Matcher matcher = first.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = second.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String unescape(String value) {
        String result = StringUtils.defaultString(value);
        result = StringUtils.replace(result, "&nbsp;", " ");
        result = StringUtils.replace(result, "&amp;", "&");
        result = StringUtils.replace(result, "&quot;", "\"");
        result = StringUtils.replace(result, "&#39;", "'");
        return StringUtils.trim(result);
    }
}
