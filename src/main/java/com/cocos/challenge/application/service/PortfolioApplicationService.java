package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetPortfolioUseCase;
import com.cocos.challenge.application.port.out.AccountPort;
import com.cocos.challenge.application.port.out.PortfolioPort;
import com.cocos.challenge.application.exception.ApplicationException;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Position;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PortfolioApplicationService implements GetPortfolioUseCase {
    private final AccountPort accounts;
    private final PortfolioPort portfolios;

    public PortfolioApplicationService(AccountPort accounts, PortfolioPort portfolios) {
        this.accounts = accounts;
        this.portfolios = portfolios;
    }

    @Override
    public Portfolio getPortfolio(long userId) {
        String accountNumber = accounts.accountNumber(userId)
                .orElseThrow(() -> new ApplicationException.NotFound("No existe el usuario " + userId));
        BigDecimal cash = portfolios.cashBalance(userId);
        BigDecimal availableCash = cash.subtract(portfolios.reservedCash(userId));
        List<Position> positions = portfolios.positions(userId).stream()
                .map(row -> toPosition(userId, row))
                .toList();
        BigDecimal marketValue = positions.stream().map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = marketValue.add(portfolios.filledSellProceeds(userId))
                .subtract(portfolios.filledBuyCost(userId));
        return new Portfolio(userId, accountNumber, "ARS",
                cash.add(marketValue).setScale(2, RoundingMode.HALF_UP),
                cash.setScale(2, RoundingMode.HALF_UP),
                availableCash.setScale(2, RoundingMode.HALF_UP),
                percent(profit, portfolios.filledBuyCost(userId)), positions);
    }

    private Position toPosition(long userId, PortfolioPort.PositionRow row) {
        if (row.marketPrice() == null) {
            throw new ApplicationException.Conflict(
                    "No hay una cotización disponible para el instrumento " + row.instrumentId());
        }
        BigDecimal marketValue = row.marketPrice().multiply(BigDecimal.valueOf(row.quantity()));
        BigDecimal profit = marketValue.add(row.totalSales()).subtract(row.totalBuys());
        return new Position(row.instrumentId(), row.ticker(), row.name(), row.quantity(),
                row.quantity() - portfolios.reservedQuantity(userId, row.instrumentId()),
                row.marketPrice().setScale(2, RoundingMode.HALF_UP),
                marketValue.setScale(2, RoundingMode.HALF_UP),
                profit.setScale(2, RoundingMode.HALF_UP), percent(profit, row.totalBuys()),
                dailyReturnPercent(row.marketPrice(), row.previousClose()));
    }

    private BigDecimal dailyReturnPercent(BigDecimal marketPrice, BigDecimal previousClose) {
        if (previousClose == null || previousClose.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return percent(marketPrice.subtract(previousClose), previousClose);
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal base) {
        if (base == null || base.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return amount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
