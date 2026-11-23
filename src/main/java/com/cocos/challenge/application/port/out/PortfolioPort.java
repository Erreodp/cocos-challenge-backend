package com.cocos.challenge.application.port.out;

import java.math.BigDecimal;
import java.util.List;

public interface PortfolioPort {
    BigDecimal cashBalance(long userId);
    BigDecimal reservedCash(long userId);
    long filledQuantity(long userId, long instrumentId);
    long reservedQuantity(long userId, long instrumentId);
    BigDecimal filledBuyCost(long userId);
    BigDecimal filledSellProceeds(long userId);
    List<PositionRow> positions(long userId);

    record PositionRow(long instrumentId, String ticker, String name, long quantity,
                       BigDecimal totalBuys, BigDecimal totalSales, BigDecimal marketPrice) {
    }
}
