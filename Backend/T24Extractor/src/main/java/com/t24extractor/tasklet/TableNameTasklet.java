package com.t24extractor.tasklet;

import com.t24extractor.model.TafjVoc;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Résout le nom physique Oracle d'une table T24 et son mode de stockage.
 *
 * <p>T24 ne stocke pas ses tables sous leur nom fonctionnel. La résolution se
 * fait en deux temps :
 * <ol>
 *   <li>la classification (F_FILE_CONTROL) donne le préfixe, {@code F} ou
 *       {@code FBNK} — d'où {@code CUSTOMER} → {@code FBNK.CUSTOMER} ;</li>
 *   <li>TAFJ_VOC traduit ce nom qualifié en table Oracle réelle
 *       ({@code FBNK_CUSTOMER}) et indique son mode de stockage.</li>
 * </ol>
 *
 * <p>Écrit dans le contexte de job {@code Table_Name} et {@code Type} ; ce
 * dernier détermine si la suite du flow lira du XML ou du BLOB.
 *
 * @see com.t24extractor.model.TafjVoc
 */
@Slf4j
@Component
public class TableNameTasklet implements Tasklet {

    private final TafjVocRepository tafjVocRepository;

    private final FileControlServiceImpl fileControlService;

    public TableNameTasklet(TafjVocRepository tafjVocRepository, FileControlServiceImpl fileControlService) {
        this.tafjVocRepository = tafjVocRepository;
        this.fileControlService = fileControlService;
    }

    @Setter
    String t24TableName;

    /**
     * {@inheritDoc}
     *
     * @return toujours {@link RepeatStatus#FINISHED}
     * @throws NoSuchElementException si TAFJ_VOC ne connaît pas la table :
     *         poursuivre reviendrait à interroger une table Oracle inexistante
     */
    @Override
    public RepeatStatus execute(@NonNull StepContribution contribution, ChunkContext chunkContext) throws Exception {

        log.debug("T24 Name {}", this.t24TableName);

        /*
            Concatenation entre la classification & le nom de la table T24
            Ex: Classification= FBNK. + t24Name= ACCOUNT ==> FBNK.ACCOUNT
         */

        String classification = fileControlService.classification(this.t24TableName);
        log.debug("Le retour de Service Classification est {}", String.join(".", classification, this.t24TableName));
        String t24SelectionName = String.join(".", classification, this.t24TableName);


        /*
            Le correspondant de la table T24 sur Oracle
         */
       TafjVoc tafjVoc =  tafjVocRepository.findTafjVocByRecid(t24SelectionName)
                .orElseThrow(() -> new NoSuchElementException(String.format("Veuillez verifier que la table %s existe.", t24SelectionName)));

        /*
            Le singleMult des donnees T24 sur Oracle(Blob, XML, Clob)
         */

        log.info(String.valueOf(tafjVoc));
        String dbFileName = tafjVoc.orclFileName();
        char isBlob = tafjVoc.isBlob().charAt(0);


        /*
          Passer les variables aux autres steps à travers le contexte.
         */
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("Table_Name", dbFileName);

        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("Type", isBlob);

        return RepeatStatus.FINISHED;
    }

}
