package com.cocos.challenge.adapter.out.sqlite;

import com.cocos.challenge.application.port.out.PortfolioPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqlitePortfolioAdapter implements PortfolioPort {
    private final JdbcTemplate jdbc;

    public SqlitePortfolioAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal cashBalance(long userId) {
        return decimal("""
                SELECT COALESCE(SUM(CASE
                  WHEN o.side='CASH_IN' THEN o.size
                  WHEN o.side='CASH_OUT' THEN -o.size
                  WHEN o.side='BUY' THEN -(o.size*o.price)
                  WHEN o.side='SELL' THEN o.size*o.price ELSE 0 END),0)
                FROM orders o JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED'
                  AND (i.type='MONEDA' OR o.side IN ('BUY','SELL'))
                """, userId);
    }

    @Override
    public BigDecimal reservedCash(long userId) {
        return decimal("""
                SELECT COALESCE(SUM(o.size*o.price),0) FROM orders o
                JOIN instruments i ON i.id=o.instrumentId
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
        return decimal("""
                SELECT COALESCE(SUM(o.size*o.price),0) FROM orders o
                JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED' AND o.side='BUY' AND i.type<>'MONEDA'
                """, userId);
    }

    @Override
    public BigDecimal filledSellProceeds(long userId) {
        return decimal("""
                SELECT COALESCE(SUM(o.size*o.price),0) FROM orders o
                JOIN instruments i ON i.id=o.instrumentId
                WHERE o.userId=? AND o.status='FILLED' AND o.side='SELL' AND i.type<>'MONEDA'
                """, userId);
    }

    @Override
    public List<PositionRow> positions(long userId) {
        return jdbc.query("""
                SELECT i.id,i.ticker,i.name,
                  SUM(CASE WHEN o.side='BUY' THEN o.size ELSE -o.size END) AS quantity,
                  SUM(CASE WHEN o.side='BUY' THEN o.size*o.price ELSE 0 END) AS totalBuys,
                  SUM(CASE WHEN o.side='SELL' THEN o.size*o.price ELSE 0 END) AS totalSales,
                  m.close AS marketPrice,m.previousClose AS previousClose
                FROM orders o JOIN instruments i ON i.id=o.instrumentId
                LEFT JOIN marketdata m ON m.id=(
                  SELECT latest.id FROM marketdata latest WHERE latest.instrumentId=i.id
                  ORDER BY latest.date DESC,latest.id DESC LIMIT 1)
                WHERE o.userId=? AND o.status='FILLED' AND o.side IN ('BUY','SELL')
                  AND i.type<>'MONEDA'
                GROUP BY i.id,i.ticker,i.name,m.close
                HAVING SUM(CASE WHEN o.side='BUY' THEN o.size ELSE -o.size END)>0
                ORDER BY i.ticker
                """, (rs, row) -> new PositionRow(rs.getLong("id"), rs.getString("ticker"),
                rs.getString("name"), rs.getLong("quantity"), rs.getBigDecimal("totalBuys"),
                rs.getBigDecimal("totalSales"), rs.getBigDecimal("marketPrice"),
                rs.getBigDecimal("previousClose")), userId);
    }

    private BigDecimal decimal(String sql, long userId) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, userId);
        return value == null ? BigDecimal.ZERO : value;
    }
}
