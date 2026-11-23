package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Order;

public interface CancelOrderUseCase {
    Order cancel(long userId, long orderId);
}
