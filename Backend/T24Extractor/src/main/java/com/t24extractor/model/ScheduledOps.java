package com.t24extractor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import static com.t24extractor.commons.Constants.SCHEDULED_OPS;

/**
 * Opération de maintenance différée sur une table BI volumineuse.
 *
 * <p>Créer un index sur une table de plusieurs millions de lignes la bloquerait
 * pendant l'extraction. Au-delà du seuil, l'opération n'est pas exécutée mais
 * enregistrée ici, pour être reprise hors fenêtre de traitement.
 *
 * @see com.t24extractor.bi_sync.tasklet.BISyncTasklet#updateIndex(String, int, String)
 */
@Table(value = SCHEDULED_OPS)
@Data
@AllArgsConstructor
public class ScheduledOps {
    @Id
    Long id;
    String table;
    String column;
    String op;
}
