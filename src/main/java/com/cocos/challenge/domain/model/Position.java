package com.cocos.challenge.domain.model;

import java.math.BigDecimal;

public record Position(long instrumentId, String ticker, String name,
                       long quantity, long availableQuantity,
                       BigDecimal marketPrice, BigDecimal marketValue,
                       BigDecimal profitLoss, BigDecimal returnPercent,
                       BigDecimal dailyReturnPercent) {
}
