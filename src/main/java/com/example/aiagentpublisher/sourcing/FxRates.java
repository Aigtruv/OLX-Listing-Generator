package com.example.aiagentpublisher.sourcing;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FxRates {

    private final double usdKzt;
    private final double eurKzt;
    private final double cnyKzt;

    public FxRates(@Value("${app.fx.usd-kzt}") double usdKzt,
                   @Value("${app.fx.eur-kzt}") double eurKzt,
                   @Value("${app.fx.cny-kzt}") double cnyKzt) {
        this.usdKzt = usdKzt;
        this.eurKzt = eurKzt;
        this.cnyKzt = cnyKzt;
    }

    public Optional<Long> toKzt(double amount, String currency) {
        if (StringUtils.isBlank(currency)) {
            return Optional.empty();
        }
        double rate;
        if (StringUtils.equalsIgnoreCase(currency, "KZT") || StringUtils.equalsIgnoreCase(currency, "тг")) {
            rate = 1;
        } else if (StringUtils.equalsIgnoreCase(currency, "USD")) {
            rate = usdKzt;
        } else if (StringUtils.equalsIgnoreCase(currency, "EUR")) {
            rate = eurKzt;
        } else if (StringUtils.equalsIgnoreCase(currency, "CNY") || StringUtils.equalsIgnoreCase(currency, "RMB")) {
            rate = cnyKzt;
        } else {
            return Optional.empty();
        }
        if (rate <= 0) {
            return Optional.empty();
        }
        return Optional.of(Math.round(amount * rate));
    }
}
