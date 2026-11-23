package com.cocos.challenge.adapter.out.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SqliteDatabaseInitializerTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesANewDatabaseAndDoesNotDuplicateSeedData() {
        DriverManagerDataSource dataSource = dataSource(tempDir.resolve("fresh.db"));
        SqliteDatabaseInitializer initializer = new SqliteDatabaseInitializer(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        initializer.initialize();
        initializer.initialize();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM instruments WHERE id=66 AND ticker='ARS'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsAPartialChallengeSchemaInsteadOfRunningTheSeedOverIt() {
        DriverManagerDataSource dataSource = dataSource(tempDir.resolve("partial.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE users (id INTEGER PRIMARY KEY)");

        assertThatThrownBy(() -> new SqliteDatabaseInitializer(dataSource).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("esquema parcial");
    }

    private DriverManagerDataSource dataSource(Path path) {
        return new DriverManagerDataSource("jdbc:sqlite:" + path);
    }
}
