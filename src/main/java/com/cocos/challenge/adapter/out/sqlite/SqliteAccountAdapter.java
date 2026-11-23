package com.cocos.challenge.adapter.out.sqlite;

import com.cocos.challenge.application.port.out.AccountPort;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteAccountAdapter implements AccountPort {
    private final JdbcTemplate jdbc;

    public SqliteAccountAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    @Override
    public Optional<String> accountNumber(long userId) {
        return jdbc.query("SELECT accountNumber FROM users WHERE id = ?",
                (rs, row) -> rs.getString("accountNumber"), userId).stream().findFirst();
    }
}
