package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.port.in.GetPortfolioUseCase;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Position;
import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public PortfolioResponse get(@PathVariable long userId) {
        return PortfolioResponse.from(getPortfolio.getPortfolio(userId));
    }

    public record PortfolioResponse(long userId, String accountNumber, String currency,
                                    BigDecimal totalValue, BigDecimal cashBalance,
                                    BigDecimal availableCash, BigDecimal returnPercent,
                                    List<PositionResponse> positions) {
        static PortfolioResponse from(Portfolio p) {
            return new PortfolioResponse(p.userId(), p.accountNumber(), p.currency(),
                    p.totalValue(), p.cashBalance(), p.availableCash(), p.returnPercent(),
                    p.positions().stream().map(PositionResponse::from).toList());
        }
    }

    public record PositionResponse(long instrumentId, String ticker, String name,
                                   long quantity, long availableQuantity, BigDecimal marketPrice,
                                   BigDecimal marketValue, BigDecimal profitLoss, BigDecimal returnPercent,
                                   BigDecimal dailyReturnPercent) {
        static PositionResponse from(Position p) {
            return new PositionResponse(p.instrumentId(), p.ticker(), p.name(), p.quantity(),
                    p.availableQuantity(), p.marketPrice(), p.marketValue(),
                    p.profitLoss(), p.returnPercent(), p.dailyReturnPercent());
        }
    }
}
