package com.example.aiagentpublisher.sourcing;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SourcingScout {

    private static final Logger log = LoggerFactory.getLogger(SourcingScout.class);
    private static final int TOP = 3;

    private final List<MarketplaceSource> sources;
    private final FxRates fx;

    public SourcingScout(List<MarketplaceSource> sources, FxRates fx) {
        this.sources = List.copyOf(sources);
        this.fx = fx;
    }

    public List<SourcingOffer> search(String query) {
        List<SourcingOffer> all = new ArrayList<>();
        for (MarketplaceSource source : sources) {
            List<RawSourcingOffer> rawOffers;
            try {
                rawOffers = source.search(query);
            } catch (RuntimeException e) {
                log.warn("Sourcing source failed: {}", source.displayName(), e);
                continue;
            }
            if (rawOffers == null) {
                continue;
            }
            for (RawSourcingOffer raw : rawOffers) {
                if (raw == null || StringUtils.isBlank(raw.title()) || StringUtils.isBlank(raw.url())
                        || raw.amount() <= 0) {
                    continue;
                }
                fx.toKzt(raw.amount(), raw.currency()).ifPresent(kzt -> all.add(
                        new SourcingOffer(raw.siteId(), raw.siteName(), raw.title(), raw.url(), kzt)));
            }
        }
        all.sort(Comparator.comparingLong(SourcingOffer::priceKzt));
        int limit = Math.min(TOP, all.size());
        return List.copyOf(all.subList(0, limit));
    }
}
