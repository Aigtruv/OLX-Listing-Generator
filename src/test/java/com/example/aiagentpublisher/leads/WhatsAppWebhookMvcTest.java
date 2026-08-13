package com.example.aiagentpublisher.leads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.whatsapp.verify-token=verify-me",
        "app.whatsapp.app-secret="
})
@AutoConfigureMockMvc
class WhatsAppWebhookMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QualificationHandler handler;

    @MockitoBean
    private WhatsAppSender sender;

    @Test
    void getReturnsRawChallenge() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-me")
                        .param("hub.challenge", "challenge-9"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-9"));
    }

    @Test
    void postJsonInvokesHandler() throws Exception {
        when(handler.handle("77011234567", "привет")).thenReturn(List.of());
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"77011234567","id":"wamid.mvc","type":"text","text":{"body":"привет"}}]
                }}]}]}
                """;

        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(handler).handle("77011234567", "привет");
    }
}
