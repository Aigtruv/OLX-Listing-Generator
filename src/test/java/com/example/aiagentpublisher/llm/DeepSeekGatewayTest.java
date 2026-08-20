package com.example.aiagentpublisher.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekGatewayTest {

    @Test
    void blankKeyDoesNotCallHttp() {
        RestClient restClient = RestClient.builder().baseUrl("http://localhost").build();
        DeepSeekGateway gateway = new DeepSeekGateway("  ", "deepseek-chat", restClient);

        assertThatThrownBy(() -> gateway.generate("sys", "user", CategorySuggestion.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void parsesCategoryJsonFromChatCompletion() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = """
                {"choices":[{"message":{"content":"{\\"categoryPath\\":\\"Электроника → Ноутбуки\\"}"}}]}
                """;
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        DeepSeekGateway gateway = new DeepSeekGateway("sk-test", "deepseek-chat", builder.build());
        CategorySuggestion suggestion = gateway.generate("sys", "продаю ноутбуки", CategorySuggestion.class);

        assertThat(suggestion.categoryPath()).isEqualTo("Электроника → Ноутбуки");
        server.verify();
    }

    @Test
    void parsesGeneratedListingIncludingPhotoChecklist() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String inner = "{\"title\":\"Ноутбук Dell\",\"description\":\"Описание.\","
                + "\"priceAdvice\":\"150000 тг\",\"photoChecklist\":[\"экран\",\"клавиатура\"]}";
        String body = "{\"choices\":[{\"message\":{\"content\":" + quote(inner) + "}}]}";
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        DeepSeekGateway gateway = new DeepSeekGateway("sk-test", "deepseek-chat", builder.build());
        GeneratedListing listing = gateway.generate("sys", "user", GeneratedListing.class);

        assertThat(listing.title()).isEqualTo("Ноутбук Dell");
        assertThat(listing.photoChecklist()).isEqualTo(List.of("экран", "клавиатура"));
        server.verify();
    }

    @Test
    void unwrapsTypeNameWrapperForListingAnalysis() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String inner = "{\"ListingAnalysis\":{\"perExampleAnalysis\":[\"анализ\"],"
                + "\"winningTemplate\":\"шаблон\"}}";
        String body = "{\"choices\":[{\"message\":{\"content\":" + quote(inner) + "}}]}";
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        DeepSeekGateway gateway = new DeepSeekGateway("sk-test", "deepseek-chat", builder.build());
        ListingAnalysis analysis = gateway.generate("sys", "user", ListingAnalysis.class);

        assertThat(analysis.perExampleAnalysis()).isEqualTo(List.of("анализ"));
        assertThat(analysis.winningTemplate()).isEqualTo("шаблон");
        server.verify();
    }

    @Test
    void unwrapsTypeNameWrapperForGeneratedListing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String inner = "{\"GeneratedListing\":{\"title\":\"Ноутбук Dell\",\"description\":\"Описание.\","
                + "\"priceAdvice\":\"150000 тг\",\"photoChecklist\":[\"экран\"]}}";
        String body = "{\"choices\":[{\"message\":{\"content\":" + quote(inner) + "}}]}";
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        DeepSeekGateway gateway = new DeepSeekGateway("sk-test", "deepseek-chat", builder.build());
        GeneratedListing listing = gateway.generate("sys", "user", GeneratedListing.class);

        assertThat(listing.title()).isEqualTo("Ноутбук Dell");
        assertThat(listing.photoChecklist()).isEqualTo(List.of("экран"));
        server.verify();
    }

    private static String quote(String json) {
        return "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
