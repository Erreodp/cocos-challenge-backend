package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record Portfolio(long userId, String accountNumber, String currency,
                        BigDecimal totalValue, BigDecimal cashBalance,
                        BigDecimal availableCash, BigDecimal returnPercent,
                        List<Position> positions) {
}
