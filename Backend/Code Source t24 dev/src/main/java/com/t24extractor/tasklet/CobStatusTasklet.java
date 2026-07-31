package com.t24extractor.tasklet;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.repository.t24_repo.CobRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.t24extractor.commons.Constants.*;

/**
 * Première étape de tout job : vérifie que la clôture journalière T24 est
 * terminée avant d'autoriser l'extraction.
 *
 * <p>Pendant le COB (<i>Close Of Business</i>), T24 réécrit massivement ses
 * tables : les lire à ce moment produirait un instantané incohérent. L'étape
 * lit le champ {@code SERVICE_CONTROL} de F_TSA_SERVICE ; seule la valeur
 * {@code STOP} laisse le job continuer.
 *
 * <p>Si le COB tourne encore, l'étape n'échoue pas : elle positionne le statut
 * de sortie {@code COB_RUNNING}, ce qui interrompt proprement le flow. Le job
 * sera relancé au créneau suivant.
 *
 * @see com.t24extractor.model.CobStatus
 */
@Component
@Slf4j
public class CobStatusTasklet implements Tasklet {
    /**
     * CobRepository permettant de requeter la table TSA_SERVICE
     */
    CobRepository cobRepository;



    /**
     * @param cobRepository accès en lecture à l'état du service T24
     */
    @Autowired
    public CobStatusTasklet(CobRepository cobRepository) {
        this.cobRepository = cobRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @return toujours {@link RepeatStatus#FINISHED} ; le refus de poursuivre
     *         passe par le statut de sortie, pas par une exception
     * @throws com.t24extractor.exception.DataNotFoundException si la table
     *         F_TSA_SERVICE ne contient pas la ligne COB, signe que la base T24
     *         n'est pas correctement chargée
     */
    @Override
    public RepeatStatus execute(@NonNull StepContribution stepContribution, @NonNull ChunkContext chunkContext) throws Exception {
        log.info("*-----------------* Vérification si le COB est en état d'arrêt *-----------------*");


        log.info("*-----------------* Lecture du champ 'SERVICE_CONTROL' de la table TSA_SERVICE *-----------------*");
        String serviceControl = cobRepository.findById(COB_RECID)
                .orElseThrow(() ->
                        new DataNotFoundException("Le champ 'SERVICE_CONTROL' est vide. Prière de vérifier si la base T24 est bien chargée.")
                )
                .serviceControl();
        /*
         * Si la variable serviceControl est "STOP", l'extracteur sera déclenché.
         * Sinon, le job se quittera
         */
        if (COB_STOPPED.equals(serviceControl)) {
            log.info("*-----------------* Le COB est en état d'arrêt, procéder à l'étape  *-----------------*");
        } else {
            stepContribution.setExitStatus(new ExitStatus(COB_RUNNING));
            stepContribution.getStepExecution().getJobExecution().setExitStatus(new ExitStatus(COB_RUNNING));
            log.info("*-----------------* Le COB est en état d'exécution, l'extracteur s'arrêtera en quelques mins  *-----------------*");
        }
        return RepeatStatus.FINISHED;
    }
}
