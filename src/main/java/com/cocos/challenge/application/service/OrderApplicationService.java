package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.SubmitOrderUseCase;
import com.cocos.challenge.application.port.out.AccountPort;
import com.cocos.challenge.application.port.out.InstrumentCatalogPort;
import com.cocos.challenge.application.port.out.OrderPort;
import com.cocos.challenge.application.port.out.PortfolioPort;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.service.OrderPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

public class OrderApplicationService implements SubmitOrderUseCase, CancelOrderUseCase {
    private final AccountPort accounts;
    private final InstrumentCatalogPort instruments;
    private final PortfolioPort portfolios;
    private final OrderPort orders;

    public OrderApplicationService(AccountPort accounts, InstrumentCatalogPort instruments,
                                   PortfolioPort portfolios, OrderPort orders) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.portfolios = portfolios;
        this.orders = orders;
    }

    @Override
    public Order submit(long userId, Long instrumentId, Order.Side side, Order.Type type,
                        Long quantity, BigDecimal amount, BigDecimal price) {
        if (!accounts.exists(userId)) throw new ApplicationException.NotFound("No existe el usuario " + userId);
        if (instrumentId == null) throw new ApplicationException.BadRequest("instrumentId es obligatorio");
        if (side == null || type == null) throw new ApplicationException.BadRequest("side y type son obligatorios");
        boolean hasQuantity = quantity != null;
        boolean hasAmount = amount != null;
        if (hasQuantity == hasAmount) {
            throw new ApplicationException.BadRequest("Debe informar exactamente uno de quantity o amount");
        }
        if (hasQuantity && quantity <= 0) throw new ApplicationException.BadRequest("quantity debe ser mayor que cero");
        if (hasAmount && amount.signum() <= 0) throw new ApplicationException.BadRequest("amount debe ser mayor que cero");
        if (type == Order.Type.LIMIT && (price == null || price.signum() <= 0)) {
            throw new ApplicationException.BadRequest("price es obligatorio y positivo para órdenes LIMIT");
        }
        if (type == Order.Type.MARKET && price != null) {
            throw new ApplicationException.BadRequest("No se debe informar price para órdenes MARKET");
        }

        Instrument instrument = instruments.findById(instrumentId)
                .orElseThrow(() -> new ApplicationException.NotFound("No existe el instrumento " + instrumentId));
        if (instrument.isCurrency()) throw new ApplicationException.BadRequest("No se admiten órdenes sobre MONEDA");
        BigDecimal executionPrice = type == Order.Type.MARKET ? instrument.lastPrice() : price;
        if (executionPrice == null || executionPrice.signum() <= 0) {
            throw new ApplicationException.Conflict("No hay una cotización válida para el instrumento " + instrumentId);
        }
        long shares;
        try {
            shares = quantity != null ? quantity
                    : amount.divide(executionPrice, 0, RoundingMode.DOWN).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ApplicationException.BadRequest("La cantidad calculada excede el máximo permitido");
        }
        if (shares <= 0) throw new ApplicationException.BadRequest("El monto no alcanza para una acción");

        String rejection = side == Order.Side.BUY
                ? OrderPolicy.validateBuy(portfolios.cashBalance(userId)
                        .subtract(portfolios.reservedCash(userId)), executionPrice, shares)
                : OrderPolicy.validateSell(portfolios.filledQuantity(userId, instrumentId)
                        - portfolios.reservedQuantity(userId, instrumentId), shares);
        Order.Status status = OrderPolicy.statusFor(rejection, type);
        LocalDateTime now = LocalDateTime.now();
        long id = orders.insert(instrumentId, userId, shares, executionPrice, type, side, status, now);
        return new Order(id, instrumentId, userId, shares,
                executionPrice.setScale(2, RoundingMode.HALF_UP),
                type, side, status, now, rejection);
    }

    @Override
    public Order cancel(long userId, long orderId) {
        if (!accounts.exists(userId)) throw new ApplicationException.NotFound("No existe el usuario " + userId);
        Order order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApplicationException.NotFound("No existe la orden " + orderId));
        if (order.status() != Order.Status.NEW) {
            throw new ApplicationException.Conflict("Solo se pueden cancelar órdenes NEW");
        }
        if (!orders.cancel(orderId, userId)) {
            throw new ApplicationException.Conflict("La orden cambió de estado y no pudo cancelarse");
        }
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new IllegalStateException("La orden cancelada no se encontró"));
    }
}
