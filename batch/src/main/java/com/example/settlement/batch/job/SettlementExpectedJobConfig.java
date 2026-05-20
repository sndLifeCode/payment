package com.example.settlement.batch.job;

import com.example.settlement.core.domain.service.SettlementCalculationService;
import com.example.settlement.core.domain.exception.InvalidTransactionException;
import com.example.settlement.core.domain.exception.PolicyNotFoundException;
import com.example.settlement.core.persistence.entity.PaymentTransaction;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import com.example.settlement.core.persistence.repository.SettlementExpectedRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SettlementExpectedJobConfig {

    private static final Logger log = LoggerFactory.getLogger(SettlementExpectedJobConfig.class);
    private static final int CHUNK_SIZE = 500;

    @Bean
    public Job settlementExpectedJob(
            JobRepository jobRepository,
            Step settlementCleanupStep,
            Step settlementExpectedStep) {
        return new JobBuilder("settlementExpectedJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(settlementCleanupStep)
                .next(settlementExpectedStep)
                .build();
    }

    @Bean
    public Step settlementCleanupStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            org.springframework.batch.core.step.tasklet.Tasklet cleanupTasklet) {
        return new StepBuilder("settlementCleanupStep", jobRepository)
                .tasklet(cleanupTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public org.springframework.batch.core.step.tasklet.Tasklet cleanupTasklet(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{jobParameters['pgCompany']}") String pgCompany,
            SettlementExpectedRepository settlementExpectedRepository) {
        return (contribution, chunkContext) -> {
            LocalDate parsedBaseDate = LocalDate.parse(baseDate);
            settlementExpectedRepository.deleteByBaseDateAndPgCompany(parsedBaseDate, pgCompany);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step settlementExpectedStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<PaymentTransaction> paymentTransactionReader,
            ItemProcessor<PaymentTransaction, SettlementExpected> settlementProcessor,
            JpaItemWriter<SettlementExpected> settlementWriter) {
        return new StepBuilder("settlementExpectedStep", jobRepository)
                .<PaymentTransaction, SettlementExpected>chunk(CHUNK_SIZE, transactionManager)
                .reader(paymentTransactionReader)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<PaymentTransaction> paymentTransactionReader(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{jobParameters['pgCompany']}") String pgCompany,
            EntityManagerFactory entityManagerFactory) {
        JpaCursorItemReader<PaymentTransaction> reader = new JpaCursorItemReader<>();
        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setQueryString(
                """
                select pt from PaymentTransaction pt
                where pt.pgCompany = :pgCompany
                  and pt.transactionDate = :baseDate
                order by pt.id asc
                """);
        reader.setParameterValues(
                Map.of("pgCompany", pgCompany, "baseDate", LocalDate.parse(baseDate)));
        return reader;
    }

    @Bean
    @StepScope
    public ItemProcessor<PaymentTransaction, SettlementExpected> settlementProcessor(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            SettlementCalculationService settlementCalculationService) {
        return transaction -> {
            try {
                return settlementCalculationService.calculateExpected(
                        LocalDate.parse(baseDate), transaction);
            } catch (PolicyNotFoundException | InvalidTransactionException exception) {
                log.warn(
                        "정산 예정 계산 skip. transactionId={}, reason={}",
                        transaction.getTransactionId(),
                        exception.getMessage());
                return null;
            }
        };
    }

    @Bean
    public JpaItemWriter<SettlementExpected> settlementWriter(EntityManagerFactory emf) {
        JpaItemWriter<SettlementExpected> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(emf);
        return writer;
    }
}
