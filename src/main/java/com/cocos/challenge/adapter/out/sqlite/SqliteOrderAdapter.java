package com.cocos.challenge.adapter.out.sqlite;

import com.cocos.challenge.application.port.out.OrderPort;
import com.cocos.challenge.domain.model.Order;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteOrderAdapter implements OrderPort {
    private final JdbcTemplate jdbc;

    public SqliteOrderAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockOrderSubmissions() {
        jdbc.execute("PRAGMA busy_timeout = 10000");
        jdbc.update("UPDATE orders SET status = status WHERE id = -1");
    }

    @Override
    public long insert(long instrumentId, long userId, long quantity, BigDecimal price,
                       Order.Type type, Order.Side side, Order.Status status, LocalDateTime datetime) {
        jdbc.update("""
                INSERT INTO orders (instrumentId,userId,size,price,type,side,status,datetime)
                VALUES (?,?,?,?,?,?,?,?)
                """, instrumentId, userId, quantity, price, type.name(), side.name(),
                status.name(), Timestamp.valueOf(datetime));
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        if (id == null) throw new IllegalStateException("SQLite no devolvió el id de la orden");
        return id;
    }

    @Override
    public Optional<Order> findByIdAndUserId(long orderId, long userId) {
        List<Order> result = jdbc.query("""
                SELECT id,instrumentId,userId,size,price,type,side,status,datetime
                FROM orders WHERE id=? AND userId=?
                """, (rs, row) -> new Order(rs.getLong("id"), rs.getLong("instrumentId"),
                rs.getLong("userId"), rs.getLong("size"), rs.getBigDecimal("price"),
                Order.Type.valueOf(rs.getString("type")), Order.Side.valueOf(rs.getString("side")),
                Order.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("datetime").toLocalDateTime(), null), orderId, userId);
        return result.stream().findFirst();
    }

    @Override
    public boolean cancel(long orderId, long userId) {
        return jdbc.update("""
                UPDATE orders SET status='CANCELLED'
                WHERE id=? AND userId=? AND status='NEW'
                """, orderId, userId) == 1;
    }
}
