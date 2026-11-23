package com.cocos.challenge.adapter.out.sqlite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
public class SqliteDatabaseInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SqliteDatabaseInitializer.class);
    private static final Set<String> CHALLENGE_TABLES =
            Set.of("users", "instruments", "orders", "marketdata");

    private final DataSource dataSource;

    public SqliteDatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    void initialize() {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> existingTables = challengeTables(connection);
            if (existingTables.containsAll(CHALLENGE_TABLES)) {
                log.info("Se conserva la base SQLite existente; no se vuelven a cargar datos.");
                return;
            }
            if (!existingTables.isEmpty()) {
                Set<String> missingTables = new HashSet<>(CHALLENGE_TABLES);
                missingTables.removeAll(existingTables);
                throw new IllegalStateException(
                        "La base SQLite tiene un esquema parcial. Faltan tablas: " + missingTables);
            }

            initializeFromSeed(connection);
            log.info("Se creó el esquema SQLite y se cargaron los datos del challenge.");
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("No se pudo inicializar la base SQLite del challenge.", exception);
        }
    }

    private Set<String> challengeTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String table = result.getString("name");
                    if (CHALLENGE_TABLES.contains(table)) {
                        tables.add(table);
                    }
                }
            }
        }
        return tables;
    }

    private void initializeFromSeed(Connection connection) throws IOException, SQLException {
        String source = new ClassPathResource("db/cocos-db").getContentAsString(StandardCharsets.UTF_8);
        String sqliteScript = source.replaceAll(
                "(?i)\\bSERIAL\\s+PRIMARY\\s+KEY\\b", "INTEGER PRIMARY KEY AUTOINCREMENT");
        if (source.equals(sqliteScript)) {
            throw new IllegalStateException("El script db/cocos-db no contiene las declaraciones SERIAL esperadas.");
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ScriptUtils.executeSqlScript(connection,
                    new ByteArrayResource(sqliteScript.getBytes(StandardCharsets.UTF_8), "SQLite challenge seed"));
            connection.commit();
        } catch (RuntimeException | SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }
}
