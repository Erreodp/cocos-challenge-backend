package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.SubmitOrderUseCase;
import com.cocos.challenge.application.exception.ApplicationException;
import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/orders")
@Tag(name = "Órdenes", description = "Envío y cancelación de órdenes de inversión")
public class OrderRestController {
    private final SubmitOrderUseCase submit;
    private final CancelOrderUseCase cancel;

    public OrderRestController(SubmitOrderUseCase submit, CancelOrderUseCase cancel) {
        this.submit = submit;
        this.cancel = cancel;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enviar una orden", description = "Informar exactamente uno de quantity o amount. "
            + "MARKET se ejecuta al último cierre; LIMIT se registra NEW.")
    public OrderResponse submit(@PathVariable long userId, @RequestBody OrderRequest request) {
        if (request.instrumentId() == null) throw new ApplicationException.BadRequest("instrumentId es obligatorio");
        return OrderResponse.from(submit.submit(userId, request.instrumentId(),
                parseSide(request.side()), parseType(request.type()), request.quantity(),
                request.amount(), request.price()));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancelar una orden", description = "Solo cancela órdenes en estado NEW.")
    public OrderResponse cancel(@PathVariable long userId, @PathVariable long orderId) {
        return OrderResponse.from(cancel.cancel(userId, orderId));
    }

    private Order.Side parseSide(String value) {
        try {
            return Order.Side.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ApplicationException.BadRequest("side debe ser BUY o SELL");
        }
    }

    private Order.Type parseType(String value) {
        try {
            return Order.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ApplicationException.BadRequest("type debe ser MARKET o LIMIT");
        }
    }

    public record OrderRequest(Long instrumentId, String side, String type,
                               Long quantity, BigDecimal amount, BigDecimal price) {
    }

    public record OrderResponse(long id, long instrumentId, long userId, long quantity,
                                BigDecimal price, String type, String side, String status,
                                LocalDateTime datetime, String rejectionReason) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.id(), o.instrumentId(), o.userId(), o.quantity(),
                    o.price(), o.type().name(), o.side().name(), o.status().name(),
                    o.datetime(), o.rejectionReason());
        }
    }
}
