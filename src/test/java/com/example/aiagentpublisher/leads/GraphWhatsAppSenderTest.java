package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphWhatsAppSenderTest {

    @Test
    void noOpsWhenTokenBlank() {
        RestClient restClient = RestClient.builder().baseUrl("http://localhost").build();
        GraphWhatsAppSender sender = new GraphWhatsAppSender("  ", "123", restClient);
        sender.sendText("7701", "hi");
    }

    @Test
    void postsMessageAndRetriesOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(2), requestTo("http://localhost/phone-id/messages"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost/phone-id/messages"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        GraphWhatsAppSender sender = new GraphWhatsAppSender("token", "phone-id", builder.build());
        sender.sendText("7701", "привет");
        server.verify();
    }
}
