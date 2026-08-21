package com.example.aiagentpublisher.sourcing;

import java.util.List;

public interface MarketplaceSource {

    default String id() {
        return "";
    }

    default String displayName() {
        return "";
    }

    List<RawSourcingOffer> search(String query);
}
