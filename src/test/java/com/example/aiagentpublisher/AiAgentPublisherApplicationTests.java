package com.example.aiagentpublisher;

import com.example.aiagentpublisher.leads.SellerNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AiAgentPublisherApplicationTests {

    @MockitoBean
    private SellerNotifier sellerNotifier;

    @Test
    void contextLoads() {
    }

}
