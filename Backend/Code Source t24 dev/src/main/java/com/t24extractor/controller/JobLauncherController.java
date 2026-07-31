package com.t24extractor.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import static com.t24extractor.commons.Constants.RUN_ID;

/**
 * Lancement et arrêt manuels des extractions.
 *
 * <p>Chaque endpoint démarre un job de façon asynchrone et rend la main
 * immédiatement : la réponse signale la prise en compte, pas la fin du
 * traitement. Le suivi se fait par les métadonnées Spring Batch.
 *
 * <p>Chaque lancement reçoit un {@code run.id} unique, sans quoi Spring Batch
 * rejetterait une seconde exécution aux mêmes paramètres.
 *
 * <p>Tous ces endpoints exigent l'autorité {@code SCOPE_batch:execute}.
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class JobLauncherController {

    JobOperator jobOperator;
    JobRepository jobRepository;
    Job loaderJob;
    Job mainJob;
    Job cusJob;
    Job ftJob;
    Job billDetailsJob;

    @Autowired
    public JobLauncherController(JobOperator jobOperator, JobRepository jobRepository,
                                 @Qualifier("loadSchema") Job loaderJob,
                                 @Qualifier("t24Job") Job mainJob,
                                 @Qualifier("customer") Job cusJob,
                                 @Qualifier("ftHis") Job ftJob,
                                 @Qualifier("billDetails") Job billDetailsJob
                                 ) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.loaderJob = loaderJob;
        this.mainJob = mainJob;
        this.cusJob = cusJob;
        this.ftJob = ftJob;
        this.billDetailsJob = billDetailsJob;
    }

    /**
     * Lance l'extraction complète des trois tables.
     *
     * @throws IllegalStateException si le job ne peut pas démarrer : instance
     *         déjà terminée, exécution en cours ou paramètres invalides
     */
    @RequestMapping("/start")
    public void handleMain()  {

        JobParameters jobParameters = new JobParametersBuilder()
                .addString(RUN_ID, UUID.randomUUID().toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        try {
            jobOperator.start(mainJob, jobParameters);
        } catch (JobInstanceAlreadyCompleteException
                 | JobExecutionAlreadyRunningException
                 | InvalidJobParametersException
                 | JobRestartException e) {
            throw new IllegalStateException("Unable to start main job", e);
        }
    }

    /**
     * Lance la seule extraction CUSTOMER.
     *
     * @throws IllegalStateException si le job ne peut pas démarrer
     */
    @RequestMapping("/customer/start")
    public void handleCustomer()  {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString(RUN_ID, UUID.randomUUID().toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        try {
            jobOperator.start(cusJob, jobParameters);
        } catch (JobInstanceAlreadyCompleteException
                 | JobExecutionAlreadyRunningException
                 | InvalidJobParametersException
                 | JobRestartException e) {
            throw new IllegalStateException("Unable to start customer job", e);
        }
    }

    /**
     * Lance la seule extraction FUNDS.TRANSFER$HIS.
     *
     * @throws IllegalStateException si le job ne peut pas démarrer
     */
    @RequestMapping("/ft-his/start")
    public void handleFt() {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString(RUN_ID, UUID.randomUUID().toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        try {
            jobOperator.start(ftJob, jobParameters);
        } catch (JobInstanceAlreadyCompleteException
                 | JobExecutionAlreadyRunningException
                 | InvalidJobParametersException
                 | JobRestartException e) {
            throw new IllegalStateException("Unable to start ft job", e);
        }
    }

    /**
     * Lance la seule extraction AA.BILL.DETAILS.
     *
     * <p>Une exécution déjà en cours est signalée dans les journaux mais ne
     * bloque pas l'appel : c'est Spring Batch qui tranche.
     *
     * @throws Exception si le job ne peut pas démarrer
     */
    @RequestMapping("/billdetails/start")
    public void handleBillDetails() throws Exception {
        String jobName = billDetailsJob.getName();
        if (!jobRepository.findRunningJobExecutions(jobName).isEmpty()) {
            log.warn("{} est déja en cours", jobName);
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addString(RUN_ID, UUID.randomUUID().toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        jobOperator.start(billDetailsJob, jobParameters);
    }

    /**
     * Demande l'arrêt de l'extraction globale en cours.
     *
     * <p>Seule la plus récente des exécutions du job {@code T24} est arrêtée.
     * L'arrêt est coopératif : Spring Batch interrompt à la fin du chunk courant.
     *
     * @throws IllegalArgumentException si l'arrêt échoue
     */
    @RequestMapping("/stop")
    public void handleStoppingAllJobs()  {
        Set<JobExecution> executions = jobRepository.findRunningJobExecutions("T24");
        log.info("Executions: {}", executions);
        executions.stream()
                .max(Comparator.comparing(JobExecution::getCreateTime))
                .ifPresent(jobExecution -> {
                    try {
                        jobOperator.stop(jobExecution);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to stop running execution " + jobExecution.getId(), e);
                    }
                });
    }



}
