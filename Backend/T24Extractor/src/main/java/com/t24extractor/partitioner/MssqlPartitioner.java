package com.t24extractor.partitioner;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Attribue un simple index à chacun des workers.
 *
 * <p>Aucune borne n'est calculée ici : la répartition des lignes est déléguée à
 * Oracle, via la clause {@code MOD(ORA_HASH(ROWID), gridSize) = index} posée
 * par {@link com.t24extractor.tasklet.QueryTasklet}. Chaque worker reçoit donc
 * uniquement son index, sous la clé {@code value}.
 *
 * <p>L'avantage sur un découpage par plages est qu'aucun comptage préalable
 * n'est nécessaire et que la répartition reste équilibrée même si les
 * identifiants sont irrégulièrement distribués.
 */
public class MssqlPartitioner implements Partitioner {
    /**
     * {@inheritDoc}
     *
     * @param gridSize nombre de workers
     * @return une partition {@code partition<n>} par worker, portant son index
     */
    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {

        Map<String, ExecutionContext> result = new HashMap<>();

        int min = 0;


        while (min < gridSize)
        {
            ExecutionContext value = new ExecutionContext();

            result.put("partition" + min, value);

            value.putInt("value", min);

            min++;
        }
        return result;
    }
}
