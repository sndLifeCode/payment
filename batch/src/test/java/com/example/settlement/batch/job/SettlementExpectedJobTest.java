package com.example.settlement.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.settlement.batch.BatchApplication;
import com.example.settlement.core.persistence.repository.SettlementExpectedRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBatchTest
@SpringBootTest(classes = BatchApplication.class)
@ActiveProfiles("test")
class SettlementExpectedJobTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private SettlementExpectedRepository settlementExpectedRepository;

    @Test
    @DisplayName("동일 파라미터 재실행 시 결과 건수가 동일하다")
    void idempotencyTest() throws Exception {
        LocalDate baseDate = LocalDate.parse("2026-03-10");
        String pgCompany = "PG1";

        jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("baseDate", baseDate.toString())
                        .addString("pgCompany", pgCompany)
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters());

        long firstCount = settlementExpectedRepository.countByBaseDateAndPgCompany(baseDate, pgCompany);
        assertThat(firstCount).isGreaterThan(0);

        jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("baseDate", baseDate.toString())
                        .addString("pgCompany", pgCompany)
                        .addLong("run.id", System.currentTimeMillis() + 1)
                        .toJobParameters());

        long secondCount = settlementExpectedRepository.countByBaseDateAndPgCompany(baseDate, pgCompany);
        assertThat(secondCount).isEqualTo(firstCount);
    }
}
