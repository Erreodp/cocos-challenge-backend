package com.cocos.challenge.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderPolicyTest {
    @Test
    void marketOrdersAreFilledAndLimitOrdersAreNew() {
        assertThat(OrderPolicy.statusFor(null, Order.Type.MARKET)).isEqualTo(Order.Status.FILLED);
        assertThat(OrderPolicy.statusFor(null, Order.Type.LIMIT)).isEqualTo(Order.Status.NEW);
    }

    @Test
    void insufficientFundsRejectBuy() {
        assertThat(OrderPolicy.validateBuy(new BigDecimal("50"), new BigDecimal("100"), 1))
                .contains("Fondos insuficientes");
    }

    @Test
    void insufficientSharesRejectSell() {
        assertThat(OrderPolicy.validateSell(1, 2)).contains("Tenencia insuficiente");
    }
}
