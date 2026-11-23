package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.port.in.GetPortfolioUseCase;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/portfolio")
@Tag(name = "Portfolio", description = "Consulta de efectivo y posiciones de un usuario")
public class PortfolioRestController {
    private final GetPortfolioUseCase getPortfolio;

    public PortfolioRestController(GetPortfolioUseCase getPortfolio) {
        this.getPortfolio = getPortfolio;
    }

    @GetMapping
    @Operation(summary = "Consultar portfolio", description = "Incluye efectivo, posiciones y rendimiento diario por activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portfolio del usuario"),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    public PortfolioResponse get(
            @Parameter(description = "Identificador del usuario", example = "1")
            @PathVariable long userId) {
        return PortfolioResponse.from(getPortfolio.getPortfolio(userId));
    }

    @Schema(description = "Resumen del portfolio del usuario")
    public record PortfolioResponse(
            @Schema(description = "Identificador del usuario", example = "1")
            long userId,
            @Schema(description = "Número de cuenta", example = "10001")
            String accountNumber,
            @Schema(description = "Moneda de los importes", example = "ARS")
            String currency,
            @Schema(description = "Valor total: efectivo más valor de mercado de posiciones abiertas", example = "124997.00")
            BigDecimal totalValue,
            @Schema(description = "Efectivo calculado desde movimientos FILLED", example = "106480.00")
            BigDecimal cashBalance,
            @Schema(description = "Efectivo disponible después de reservas de órdenes LIMIT NEW", example = "101880.00")
            BigDecimal availableCash,
            @Schema(description = "Rendimiento acumulado aproximado en porcentaje; no es TIR", example = "2.87")
            BigDecimal returnPercent,
            @Schema(description = "Posiciones abiertas del usuario")
            List<PositionResponse> positions) {
        static PortfolioResponse from(Portfolio p) {
            return new PortfolioResponse(p.userId(), p.accountNumber(), p.currency(),
                    p.totalValue(), p.cashBalance(), p.availableCash(), p.returnPercent(),
                    p.positions().stream().map(PositionResponse::from).toList());
        }
    }

    @Schema(description = "Posición abierta de un instrumento")
    public record PositionResponse(
            @Schema(description = "Identificador del instrumento", example = "47")
            long instrumentId,
            @Schema(description = "Ticker", example = "GGAL")
            String ticker,
            @Schema(description = "Nombre del instrumento", example = "Grupo Financiero Galicia")
            String name,
            @Schema(description = "Acciones abiertas basadas en órdenes FILLED", example = "20")
            long quantity,
            @Schema(description = "Acciones no reservadas por órdenes de venta LIMIT NEW", example = "20")
            long availableQuantity,
            @Schema(description = "Último cierre en ARS", example = "925.85")
            BigDecimal marketPrice,
            @Schema(description = "Valor de mercado: quantity * marketPrice", example = "18517.00")
            BigDecimal marketValue,
            @Schema(description = "Resultado aproximado: valor de mercado + ventas históricas - compras históricas", example = "517.00")
            BigDecimal profitLoss,
            @Schema(description = "Rendimiento acumulado aproximado respecto de compras históricas, en porcentaje", example = "2.87")
            BigDecimal returnPercent,
            @Schema(description = "Variación porcentual diaria: (close - previousClose) / previousClose * 100", example = "0.44")
            BigDecimal dailyReturnPercent) {
        static PositionResponse from(Position p) {
            return new PositionResponse(p.instrumentId(), p.ticker(), p.name(), p.quantity(),
                    p.availableQuantity(), p.marketPrice(), p.marketValue(),
                    p.profitLoss(), p.returnPercent(), p.dailyReturnPercent());
        }
    }
}
