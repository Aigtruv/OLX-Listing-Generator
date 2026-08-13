package com.example.aiagentpublisher.leads;

public interface SellerNotifier {

    void notifyNewLead(long chatId, String message);
}
