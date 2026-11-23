package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;

public final class OrderPolicy {
    private OrderPolicy() {
    }

    public static String validateBuy(BigDecimal available, BigDecimal price, long quantity) {
        BigDecimal required = price.multiply(BigDecimal.valueOf(quantity));
        return available.compareTo(required) < 0
                ? "Fondos insuficientes: disponibles " + available + ", requeridos " + required
                : null;
    }

    public static String validateSell(long available, long requested) {
        return available < requested
                ? "Tenencia insuficiente: disponibles " + available + ", requeridos " + requested
                : null;
    }

    public static Order.Status statusFor(String rejection, Order.Type type) {
        if (rejection != null) return Order.Status.REJECTED;
        return type == Order.Type.MARKET ? Order.Status.FILLED : Order.Status.NEW;
    }
}
