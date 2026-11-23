package com.cocos.challenge.domain.model;

import java.math.BigDecimal;

public record Instrument(long id, String ticker, String name, String type,
                         BigDecimal lastPrice, BigDecimal previousClose) {
    public boolean isCurrency() {
        return "MONEDA".equalsIgnoreCase(type);
    }
}
