package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(long id, long instrumentId, long userId, long quantity,
                    BigDecimal price, Type type, Side side, Status status,
                    LocalDateTime datetime, String rejectionReason) {
    public enum Type { MARKET, LIMIT }
    public enum Side { BUY, SELL }
    public enum Status { NEW, FILLED, REJECTED, CANCELLED }
}
