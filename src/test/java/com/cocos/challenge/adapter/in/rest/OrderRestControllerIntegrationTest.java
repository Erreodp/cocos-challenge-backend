package com.cocos.challenge.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderRestControllerIntegrationTest {
    private static final Path DATABASE = Path.of(
            System.getProperty("java.io.tmpdir"),
            "challenge-order-api-" + UUID.randomUUID() + ".db");
    private static final long USER_ID = 1;
    private static final long INSTRUMENT_ID = 47;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE.toAbsolutePath());
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        Files.deleteIfExists(DATABASE);
        Files.deleteIfExists(Path.of(DATABASE + "-journal"));
        Files.deleteIfExists(Path.of(DATABASE + "-wal"));
        Files.deleteIfExists(Path.of(DATABASE + "-shm"));
    }

    @BeforeEach
    void resetUserOrders() {
        jdbc.update("DELETE FROM orders WHERE userId=?", USER_ID);
        insertCash(1_000_000);
    }

    @Test
    void marketBuyIsFilledAndAppearsInPortfolio() throws Exception {
        BigDecimal latestPrice = latestPrice();

        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "MARKET", 1, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.price").value(latestPrice.doubleValue()));

        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].instrumentId").value(INSTRUMENT_ID))
                .andExpect(jsonPath("$.positions[0].quantity").value(1))
                .andExpect(jsonPath("$.positions[0].dailyReturnPercent").value(
                        dailyReturnPercent().doubleValue()))
                .andExpect(jsonPath("$.cashBalance").value(
                        BigDecimal.valueOf(1_000_000).subtract(latestPrice).doubleValue()));

        assertOrderStatus("FILLED");
    }

    @Test
    void openApiAndSwaggerUiAreAvailable() throws Exception {
        MvcResult openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Cocos Challenge API"))
                .andExpect(jsonPath("$.paths['/api/users/{userId}/orders']").exists())
                .andReturn();

        assertThat(openApi.getResponse().getContentAsString())
                .contains("dailyReturnPercent", "MARKET por monto", "ApiError", "\"400\"");

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void marketBuyAcceptsAnAmountAndRoundsDownToWholeShares() throws Exception {
        BigDecimal amount = latestPrice().multiply(BigDecimal.valueOf(2)).add(BigDecimal.ONE);

        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("""
                                {"instrumentId":%d,"side":"BUY","type":"MARKET","amount":%s}
                                """.formatted(INSTRUMENT_ID, amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.quantity").value(2));

        assertOrderStatus("FILLED");
    }

    @Test
    void filledBuyThenSellUpdatesCashAndOpenPosition() throws Exception {
        BigDecimal price = latestPrice();

        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "MARKET", 5, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("SELL", "MARKET", 2, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].quantity").value(3))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(3))
                .andExpect(jsonPath("$.cashBalance").value(
                        BigDecimal.valueOf(1_000_000).subtract(price.multiply(BigDecimal.valueOf(3))).doubleValue()));
    }

    @Test
    void concurrentMarketBuysCannotSpendTheSameCashTwice() throws Exception {
        jdbc.update("DELETE FROM orders WHERE userId=?", USER_ID);
        BigDecimal price = latestPrice();
        insertCash(price.setScale(0, java.math.RoundingMode.CEILING).longValueExact());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<MvcResult>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent order requests");
                    }
                    return mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                                    .contentType("application/json")
                                    .content(orderRequest("BUY", "MARKET", 1, null)))
                            .andReturn();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> statuses = new ArrayList<>();
            for (Future<MvcResult> request : requests) {
                MvcResult result = request.get(15, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                statuses.add(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("status").asText());
            }

            assertThat(statuses).containsExactlyInAnyOrder("FILLED", "REJECTED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE userId=? AND side='BUY' AND status='FILLED'",
                    Integer.class, USER_ID)).isEqualTo(1);
        }
    }

    @Test
    void marketBuyWithoutEnoughCashIsPersistedAsRejected() throws Exception {
        jdbc.update("DELETE FROM orders WHERE userId=? AND side='CASH_IN'", USER_ID);
        insertCash(1);

        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "MARKET", 1, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("Fondos insuficientes")));

        assertOrderStatus("REJECTED");
    }

    @Test
    void marketSellWithoutEnoughSharesIsPersistedAsRejected() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("SELL", "MARKET", 1, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("Tenencia insuficiente")));

        assertOrderStatus("REJECTED");
    }

    @Test
    void limitOrderCanBeCancelledWhileNew() throws Exception {
        String response = mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "LIMIT", 1, BigDecimal.valueOf(100))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long orderId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/users/{userId}/orders/{orderId}/cancel", USER_ID, orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertOrderStatus("CANCELLED");
    }

    @Test
    void searchMatchesTickerAndNameWithoutCaseSensitivity() throws Exception {
        mockMvc.perform(get("/api/instruments").param("query", "ggal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("GGAL"));
        mockMvc.perform(get("/api/instruments").param("query", "Grupo Financiero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("GGAL"));
    }

    @Test
    void limitBuyReservesCashAndCancellationReleasesIt() throws Exception {
        String response = mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":2,\"price\":100.25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash").value(999799.5));
        mockMvc.perform(post("/api/users/{userId}/orders/{orderId}/cancel", USER_ID, orderId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash").value(1_000_000));
    }

    @Test
    void limitSellReservesSharesAndRejectsAnExcessSale() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "MARKET", 3, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FILLED"));
        String response = mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"SELL\",\"type\":\"LIMIT\",\"quantity\":2,\"price\":1000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].quantity").value(3))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(1));
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("SELL", "MARKET", 2, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/users/{userId}/orders/{orderId}/cancel", USER_ID, orderId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(3));
    }

    @Test
    void limitOrdersWithoutAvailableCashOrSharesArePersistedAsRejected() throws Exception {
        jdbc.update("DELETE FROM orders WHERE userId=? AND side='CASH_IN'", USER_ID);
        insertCash(1);
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"price\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("Fondos insuficientes")));
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"SELL\",\"type\":\"LIMIT\",\"quantity\":1,\"price\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason", containsString("Tenencia insuficiente")));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE userId=? AND status='REJECTED'", Integer.class, USER_ID))
                .isEqualTo(2);
    }

    @Test
    void marketSellCanBeSubmittedByAmountUsingWholeShares() throws Exception {
        BigDecimal price = latestPrice();
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(orderRequest("BUY", "MARKET", 3, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FILLED"));

        BigDecimal amount = price.multiply(BigDecimal.valueOf(2)).add(BigDecimal.ONE);
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentId", INSTRUMENT_ID,
                                "side", "SELL",
                                "type", "MARKET",
                                "amount", amount))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.quantity").value(2));
        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].quantity").value(1));
    }

    @Test
    void invalidAmountAndLimitPricePrecisionReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":1.001}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"price\":1.001}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/{userId}/orders", USER_ID)
                        .contentType("application/json")
                        .content("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"price\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monetaryTotalsRemainExactAcrossRepeatedFractionalPriceRows() throws Exception {
        jdbc.update("DELETE FROM orders WHERE userId=?", USER_ID);
        insertCash(1_000_000);
        for (int i = 0; i < 10; i++) {
            jdbc.update("""
                    INSERT INTO orders (instrumentId,userId,size,price,type,side,status,datetime)
                    VALUES (47,?,1,0.10,'MARKET','BUY','FILLED',?)
                    """, USER_ID, Timestamp.valueOf(LocalDateTime.now()));
        }
        mockMvc.perform(get("/api/users/{userId}/portfolio", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(999999.0))
                .andExpect(jsonPath("$.positions[0].quantity").value(10))
                .andExpect(jsonPath("$.positions[0].profitLoss").value(9257.5));
    }

    private BigDecimal latestPrice() {
        return jdbc.queryForObject("""
                SELECT m.close
                FROM marketdata m
                WHERE m.instrumentId=?
                ORDER BY m.date DESC,m.id DESC
                LIMIT 1
                """, BigDecimal.class, INSTRUMENT_ID);
    }

    private BigDecimal dailyReturnPercent() {
        return jdbc.queryForObject("""
                SELECT m.close,m.previousClose
                FROM marketdata m
                WHERE m.instrumentId=?
                ORDER BY m.date DESC,m.id DESC
                LIMIT 1
                """, (rs, row) -> rs.getBigDecimal("close")
                        .subtract(rs.getBigDecimal("previousClose"))
                        .multiply(BigDecimal.valueOf(100))
                        .divide(rs.getBigDecimal("previousClose"), 2, java.math.RoundingMode.HALF_UP),
                INSTRUMENT_ID);
    }

    private void insertCash(long amount) {
        jdbc.update("""
                INSERT INTO orders (instrumentId,userId,size,price,type,side,status,datetime)
                VALUES (66,?, ?,1,'MARKET','CASH_IN','FILLED',?)
                """, USER_ID, amount, Timestamp.valueOf(LocalDateTime.now()));
    }

    private String orderRequest(String side, String type, long quantity, BigDecimal price) {
        return """
                {"instrumentId":%d,"side":"%s","type":"%s","quantity":%d%s}
                """.formatted(INSTRUMENT_ID, side, type, quantity,
                price == null ? "" : ",\"price\":" + price);
    }

    private void assertOrderStatus(String expectedStatus) {
        String actualStatus = jdbc.queryForObject(
                "SELECT status FROM orders WHERE userId=? AND side IN ('BUY','SELL') ORDER BY id DESC LIMIT 1",
                String.class, USER_ID);
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }
}
