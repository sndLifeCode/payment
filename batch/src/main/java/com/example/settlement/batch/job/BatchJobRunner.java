package com.example.settlement.batch.job;

import java.util.Map;
import java.util.Objects;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final Job settlementExpectedJob;

    public BatchJobRunner(JobLauncher jobLauncher, Job settlementExpectedJob) {
        this.jobLauncher = jobLauncher;
        this.settlementExpectedJob = settlementExpectedJob;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, String> options =
                args.getOptionNames().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        name -> name,
                                        name ->
                                                args.getOptionValues(name) == null
                                                                || args.getOptionValues(name).isEmpty()
                                                        ? ""
                                                        : Objects.requireNonNull(
                                                                args.getOptionValues(name)).get(0)));

        if (!options.containsKey("baseDate") || !options.containsKey("pgCompany")) {
            return;
        }

        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString("baseDate", options.get("baseDate"))
                        .addString("pgCompany", options.get("pgCompany"))
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters();

        jobLauncher.run(settlementExpectedJob, jobParameters);
    }
}
