package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;

public interface SubmitOrderUseCase {
    Order submit(long userId, Long instrumentId, Order.Side side, Order.Type type,
                 Long quantity, BigDecimal amount, BigDecimal price);
}
