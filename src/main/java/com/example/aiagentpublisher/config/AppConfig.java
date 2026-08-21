package com.example.aiagentpublisher.config;

import com.example.aiagentpublisher.sourcing.HtmlSearchSource;
import com.example.aiagentpublisher.sourcing.MarketplaceSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TelegramClient telegramClient(@Value("${app.telegram.token:}") String token) {
        return new OkHttpTelegramClient(StringUtils.defaultIfBlank(token, "disabled"));
    }

    @Bean("whatsAppRestClient")
    public RestClient whatsAppRestClient(@Value("${app.whatsapp.graph-base-url}") String graphBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(graphBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean("deepSeekRestClient")
    public RestClient deepSeekRestClient(@Value("${app.deepseek.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean("olxRestClient")
    public RestClient olxRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean("sourcingRestClient")
    public RestClient sourcingRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public MarketplaceSource aliExpressSource(@Qualifier("sourcingRestClient") RestClient restClient) {
        return new HtmlSearchSource("aliexpress", "AliExpress",
                "https://www.aliexpress.com/w/wholesale-%s.html", "USD", restClient);
    }

    @Bean
    public MarketplaceSource alibabaSource(@Qualifier("sourcingRestClient") RestClient restClient) {
        return new HtmlSearchSource("alibaba", "Alibaba",
                "https://www.alibaba.com/trade/search?SearchText=%s", "USD", restClient);
    }

    @Bean
    public MarketplaceSource ali1688Source(@Qualifier("sourcingRestClient") RestClient restClient) {
        return new HtmlSearchSource("1688", "1688",
                "https://s.1688.com/selloffer/offer_search.htm?keywords=%s", "CNY", restClient);
    }

    @Bean
    public MarketplaceSource amazonDeSource(@Qualifier("sourcingRestClient") RestClient restClient) {
        return new HtmlSearchSource("amazon-de", "Amazon.de",
                "https://www.amazon.de/s?k=%s", "EUR", restClient);
    }

    @Bean
    public MarketplaceSource ebayDeSource(@Qualifier("sourcingRestClient") RestClient restClient) {
        return new HtmlSearchSource("ebay-de", "eBay.de",
                "https://www.ebay.de/sch/i.html?_nkw=%s", "EUR", restClient);
    }
}
