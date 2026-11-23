package com.cocos.challenge.adapter.out.sqlite;

import com.cocos.challenge.application.port.out.PortfolioPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqlitePortfolioAdapter implements PortfolioPort {
    private final JdbcTemplate jdbc;

    public SqlitePortfolioAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public BigDecimal cashBalance(long userId) {
        return jdbc.query("""
                SELECT o.side,o.size,o.price FROM orders o
                JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED'
                  AND (i.type='MONEDA' OR o.side IN ('BUY','SELL'))
                """, rs -> {
            BigDecimal total = BigDecimal.ZERO;
            while (rs.next()) {
                BigDecimal size = BigDecimal.valueOf(rs.getLong("size"));
                BigDecimal value = size.multiply(decimal(rs.getBigDecimal("price")));
                total = switch (rs.getString("side")) {
                    case "CASH_IN", "SELL" -> total.add(value);
                    case "CASH_OUT", "BUY" -> total.subtract(value);
                    default -> total;
                };
            }
            return total;
        }, userId);
    }

    @Override
    public BigDecimal reservedCash(long userId) {
        return sumMoney("""
                SELECT o.size,o.price FROM orders o JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.side='BUY' AND o.type='LIMIT'
                  AND o.status='NEW' AND i.type<>'MONEDA'
                """, userId);
    }

    @Override
    public long filledQuantity(long userId, long instrumentId) {
        Long result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN side='BUY' THEN size ELSE -size END),0)
                FROM orders WHERE userId=? AND instrumentId=? AND status='FILLED'
                  AND side IN ('BUY','SELL')
                """, Long.class, userId, instrumentId);
        return result == null ? 0 : result;
    }

    @Override
    public long reservedQuantity(long userId, long instrumentId) {
        Long result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(size),0) FROM orders
                WHERE userId=? AND instrumentId=? AND side='SELL'
                  AND type='LIMIT' AND status='NEW'
                """, Long.class, userId, instrumentId);
        return result == null ? 0 : result;
    }

    @Override
    public BigDecimal filledBuyCost(long userId) {
        return sumMoney("""
                SELECT o.size,o.price FROM orders o JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED' AND o.side='BUY' AND i.type<>'MONEDA'
                """, userId);
    }

    @Override
    public BigDecimal filledSellProceeds(long userId) {
        return sumMoney("""
                SELECT o.size,o.price FROM orders o JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED' AND o.side='SELL' AND i.type<>'MONEDA'
                """, userId);
    }

    @Override
    public List<PositionRow> positions(long userId) {
        List<OrderPosition> rows = jdbc.query("""
                SELECT i.id,i.ticker,i.name,o.side,o.size,o.price,
                  m.close AS marketPrice,m.previousClose AS previousClose
                FROM orders o JOIN instruments i ON i.id=o.instrumentId
                LEFT JOIN marketdata m ON m.id=(
                  SELECT latest.id FROM marketdata latest WHERE latest.instrumentId=i.id
                  ORDER BY latest.date DESC,latest.id DESC LIMIT 1)
                WHERE o.userId=? AND o.status='FILLED' AND o.side IN ('BUY','SELL')
                  AND i.type<>'MONEDA'
                ORDER BY i.ticker,o.id
                """, (rs, row) -> new OrderPosition(rs.getLong("id"), rs.getString("ticker"),
                rs.getString("name"), rs.getString("side"), rs.getLong("size"),
                decimal(rs.getBigDecimal("price")), rs.getBigDecimal("marketPrice"),
                rs.getBigDecimal("previousClose")), userId);

        Map<Long, PositionAccumulator> grouped = new LinkedHashMap<>();
        for (OrderPosition order : rows) {
            PositionAccumulator position = grouped.computeIfAbsent(order.id(), ignored ->
                    new PositionAccumulator(order));
            if ("BUY".equals(order.side())) {
                position.quantity += order.quantity();
                position.buys = position.buys.add(order.price().multiply(BigDecimal.valueOf(order.quantity())));
            } else {
                position.quantity -= order.quantity();
                position.sales = position.sales.add(order.price().multiply(BigDecimal.valueOf(order.quantity())));
            }
        }
        List<PositionRow> positions = new ArrayList<>();
        grouped.values().stream().filter(position -> position.quantity > 0).forEach(position ->
                positions.add(new PositionRow(position.id, position.ticker, position.name,
                        position.quantity, position.buys, position.sales,
                        position.marketPrice, position.previousClose)));
        return positions;
    }

    private BigDecimal sumMoney(String sql, long userId) {
        return jdbc.query(sql, rs -> {
            BigDecimal total = BigDecimal.ZERO;
            while (rs.next()) {
                total = total.add(decimal(rs.getBigDecimal("price"))
                        .multiply(BigDecimal.valueOf(rs.getLong("size"))));
            }
            return total;
        }, userId);
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record OrderPosition(long id, String ticker, String name, String side, long quantity,
                                 BigDecimal price, BigDecimal marketPrice, BigDecimal previousClose) { }

    private static final class PositionAccumulator {
        private final long id;
        private final String ticker;
        private final String name;
        private final BigDecimal marketPrice;
        private final BigDecimal previousClose;
        private long quantity;
        private BigDecimal buys = BigDecimal.ZERO;
        private BigDecimal sales = BigDecimal.ZERO;

        private PositionAccumulator(OrderPosition order) {
            id = order.id(); ticker = order.ticker(); name = order.name();
            marketPrice = order.marketPrice(); previousClose = order.previousClose();
        }
    }
}
