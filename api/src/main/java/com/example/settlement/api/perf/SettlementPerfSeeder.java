package com.example.settlement.api.perf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Profile("perf")
@Component
public class SettlementPerfSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SettlementPerfSeeder.class);
    private static final int BATCH_SIZE = 5000;

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.perf.seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.perf.seed.rows:500000}")
    private int seedRows;

    @Value("${app.perf.seed.marker-file:/tmp/settlement_perf_seed_done}")
    private String markerFile;

    public SettlementPerfSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }

        deleteMarker();

        Long currentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_expected", Long.class);

        if (currentCount != null && currentCount >= seedRows) {
            log.info("[PERF] seed skip. currentCount={} >= targetRows={}", currentCount, seedRows);
            writeMarker();
            return;
        }

        log.info("[PERF] seed start. targetRows={}", seedRows);

        jdbcTemplate.update("DELETE FROM settlement_expected");

        String sql =
                """
                INSERT INTO settlement_expected (
                  base_date,
                  pg_company,
                  transaction_id,
                  merchant_id,
                  transaction_date,
                  transaction_type,
                  amount,
                  fee_amount,
                  vat_amount,
                  expected_settlement_amount,
                  expected_settlement_date,
                  created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDateTime now = LocalDateTime.now();

        for (int offset = 0; offset < seedRows; offset += BATCH_SIZE) {
            int limit = Math.min(offset + BATCH_SIZE, seedRows);
            List<Object[]> params = new ArrayList<>(limit - offset);

            for (int i = offset; i < limit; i++) {
                LocalDate transactionDate = startDate.plusDays(i % 730L);
                String pgCompany = switch (i % 3) {
                    case 0 -> "PG1";
                    case 1 -> "PG2";
                    default -> "PG3";
                };
                String merchantId = "merchant" + ((i % 5) + 1);
                String transactionId = "perf-tx-" + i;
                String transactionType = (i % 10 == 0) ? "CANCEL" : "APPROVAL";

                BigDecimal amount = BigDecimal.valueOf(10_000L + (i % 190_000L));
                BigDecimal fee = amount.multiply(new BigDecimal("0.02")).setScale(0, RoundingMode.DOWN);
                BigDecimal vat = fee.multiply(new BigDecimal("0.1")).setScale(0, RoundingMode.DOWN);

                BigDecimal expected =
                        "APPROVAL".equals(transactionType)
                                ? amount.subtract(fee).subtract(vat)
                                : amount.negate().add(fee).add(vat);

                LocalDate expectedDate = transactionDate.plusDays(2);

                params.add(new Object[] {
                    Date.valueOf(transactionDate),
                    pgCompany,
                    transactionId,
                    merchantId,
                    Date.valueOf(transactionDate),
                    transactionType,
                    amount,
                    "APPROVAL".equals(transactionType) ? fee : fee.negate(),
                    "APPROVAL".equals(transactionType) ? vat : vat.negate(),
                    expected,
                    Date.valueOf(expectedDate),
                    Timestamp.valueOf(now)
                });
            }

            jdbcTemplate.batchUpdate(sql, params);

            if (limit % 50_000 == 0 || limit == seedRows) {
                log.info("[PERF] seed progress: {}/{}", limit, seedRows);
            }
        }

        log.info("[PERF] seed completed. rows={}", seedRows);
        writeMarker();
    }

    private void deleteMarker() {
        try {
            Files.deleteIfExists(Path.of(markerFile));
        } catch (Exception exception) {
            log.warn("[PERF] marker delete failed. markerFile={}", markerFile, exception);
        }
    }

    private void writeMarker() {
        try {
            Files.writeString(Path.of(markerFile), "done:" + LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("[PERF] marker write failed. markerFile={}", markerFile, exception);
        }
    }
}
