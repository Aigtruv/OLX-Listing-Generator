package com.example.aiagentpublisher.sourcing;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class FxRates {

    private final double usdKzt;
    private final double eurKzt;
    private final double cnyKzt;

    public FxRates(double usdKzt, double eurKzt, double cnyKzt) {
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
