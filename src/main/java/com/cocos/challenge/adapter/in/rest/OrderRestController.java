package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.exception.ApplicationException;
import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.SubmitOrderUseCase;
import com.cocos.challenge.domain.model.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada. Puede estar FILLED, NEW o REJECTED.",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class), examples = {
                            @ExampleObject(name = "Orden ejecutada", value = """
                                    {"id":101,"instrumentId":47,"userId":1,"quantity":10,"price":925.85,"type":"MARKET","side":"BUY","status":"FILLED","datetime":"2026-11-23T16:30:00","rejectionReason":null}
                                    """),
                            @ExampleObject(name = "Orden rechazada", value = """
                                    {"id":102,"instrumentId":47,"userId":1,"quantity":10000,"price":925.85,"type":"MARKET","side":"BUY","status":"REJECTED","datetime":"2026-11-23T16:30:00","rejectionReason":"Fondos insuficientes"}
                                    """)
                    })),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Usuario o instrumento inexistente",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No hay una cotización válida",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    public OrderResponse submit(
            @Parameter(description = "Identificador del usuario", example = "1")
            @PathVariable long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Enviar quantity o amount, nunca ambos. LIMIT requiere price; MARKET no lo admite.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrderRequest.class),
                            examples = {
                                    @ExampleObject(name = "MARKET por cantidad", value = """
                                            {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":10}
                                            """),
                                    @ExampleObject(name = "MARKET por monto", value = """
                                            {"instrumentId":47,"side":"BUY","type":"MARKET","amount":10000}
                                            """),
                                    @ExampleObject(name = "LIMIT", value = """
                                            {"instrumentId":47,"side":"BUY","type":"LIMIT","quantity":10,"price":900}
                                            """)
                            }))
            @RequestBody OrderRequest request) {
        if (request.instrumentId() == null) throw new ApplicationException.BadRequest("instrumentId es obligatorio");
        return OrderResponse.from(submit.submit(userId, request.instrumentId(),
                parseSide(request.side()), parseType(request.type()), request.quantity(),
                request.amount(), request.price()));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancelar una orden", description = "Solo cancela órdenes en estado NEW.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden cancelada",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuario u orden inexistente",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "La orden no está NEW o cambió de estado",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    public OrderResponse cancel(
            @Parameter(description = "Identificador del usuario", example = "1")
            @PathVariable long userId,
            @Parameter(description = "Identificador de la orden NEW que se desea cancelar", example = "101")
            @PathVariable long orderId) {
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

    @Schema(description = "Datos de una orden. quantity y amount son excluyentes.")
    public record OrderRequest(
            @Schema(description = "ID de un instrumento que no sea MONEDA", example = "47", requiredMode = Schema.RequiredMode.REQUIRED)
            Long instrumentId,
            @Schema(description = "Lado de la orden", allowableValues = {"BUY", "SELL"}, example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
            String side,
            @Schema(description = "Tipo de orden", allowableValues = {"MARKET", "LIMIT"}, example = "MARKET", requiredMode = Schema.RequiredMode.REQUIRED)
            String type,
            @Schema(description = "Cantidad entera de acciones. Informar exactamente uno de quantity o amount.", example = "10")
            Long quantity,
            @Schema(description = "Monto a invertir en ARS; se convierte a acciones enteras redondeando hacia abajo.", example = "10000")
            BigDecimal amount,
            @Schema(description = "Precio límite positivo en ARS. Obligatorio para LIMIT y prohibido para MARKET.", example = "900")
            BigDecimal price) {
    }

    @Schema(description = "Orden persistida y resultado de su validación")
    public record OrderResponse(
            @Schema(description = "Identificador de la orden", example = "101")
            long id,
            @Schema(description = "Identificador del instrumento", example = "47")
            long instrumentId,
            @Schema(description = "Identificador del usuario", example = "1")
            long userId,
            @Schema(description = "Cantidad entera de acciones", example = "10")
            long quantity,
            @Schema(description = "Precio de la orden en ARS", example = "925.85")
            BigDecimal price,
            @Schema(description = "Tipo de orden", allowableValues = {"MARKET", "LIMIT"}, example = "MARKET")
            String type,
            @Schema(description = "Lado de la orden", allowableValues = {"BUY", "SELL"}, example = "BUY")
            String side,
            @Schema(description = "Estado: MARKET válida FILLED, LIMIT válida NEW; rechazo por fondos/acciones REJECTED",
                    allowableValues = {"NEW", "FILLED", "REJECTED", "CANCELLED"}, example = "FILLED")
            String status,
            @Schema(description = "Fecha y hora de creación de la orden")
            LocalDateTime datetime,
            @Schema(description = "Motivo cuando status es REJECTED; de lo contrario null", nullable = true,
                    example = "Fondos insuficientes")
            String rejectionReason) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.id(), o.instrumentId(), o.userId(), o.quantity(),
                    o.price(), o.type().name(), o.side().name(), o.status().name(),
                    o.datetime(), o.rejectionReason());
        }
    }
}
