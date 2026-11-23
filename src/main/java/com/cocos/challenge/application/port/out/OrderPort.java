package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderPort {
    long insert(long instrumentId, long userId, long quantity, BigDecimal price,
                Order.Type type, Order.Side side, Order.Status status, LocalDateTime datetime);
    Optional<Order> findByIdAndUserId(long orderId, long userId);
    boolean cancel(long orderId, long userId);
}
