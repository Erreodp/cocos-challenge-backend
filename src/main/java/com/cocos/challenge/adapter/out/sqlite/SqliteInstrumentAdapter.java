package com.cocos.challenge.adapter.out.sqlite;

import com.cocos.challenge.application.port.out.InstrumentCatalogPort;
import com.cocos.challenge.domain.model.Instrument;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteInstrumentAdapter implements InstrumentCatalogPort {
    private static final String LATEST_PRICE_JOIN = """
            LEFT JOIN marketdata m ON m.id = (
                SELECT latest.id FROM marketdata latest
                WHERE latest.instrumentId = i.id
                ORDER BY latest.date DESC, latest.id DESC LIMIT 1
            )
            """;
    private final JdbcTemplate jdbc;

    public SqliteInstrumentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Instrument> search(String query) {
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbc.query("SELECT i.id, i.ticker, i.name, i.type, m.close, m.previousClose "
                        + "FROM instruments i " + LATEST_PRICE_JOIN
                        + "WHERE LOWER(i.ticker) LIKE ? OR LOWER(i.name) LIKE ? ORDER BY i.ticker",
                this::map, pattern, pattern);
    }

    @Override
    public Optional<Instrument> findById(long instrumentId) {
        return jdbc.query("SELECT i.id, i.ticker, i.name, i.type, m.close, m.previousClose "
                        + "FROM instruments i " + LATEST_PRICE_JOIN + "WHERE i.id = ?",
                this::map, instrumentId).stream().findFirst();
    }

    private Instrument map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Instrument(rs.getLong("id"), rs.getString("ticker"), rs.getString("name"),
                rs.getString("type"), rs.getBigDecimal("close"), rs.getBigDecimal("previousClose"));
    }
}
