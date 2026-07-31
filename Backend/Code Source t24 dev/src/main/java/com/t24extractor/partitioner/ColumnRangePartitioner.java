package com.t24extractor.partitioner;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.Map;


/**
 * Découpe la lecture en plages contiguës de numéros de ligne.
 *
 * <p>Utilisé pour les tables lues au travers d'une vue, où chaque ligne porte un
 * {@code rowNumber} : chaque worker reçoit les bornes {@code minValue} et
 * {@code maxValue} de sa plage.
 *
 * <p>Suppose que le nombre total de lignes ait été publié sous la clé
 * {@code COUNT}.
 */
public class ColumnRangePartitioner implements Partitioner
{

    @Value("#{jobExecutionContext['COUNT']}")
    int count;

    /**
     * {@inheritDoc}
     *
     * @param gridSize nombre de workers souhaité
     * @return une partition par plage ; la dernière est tronquée au nombre réel
     *         de lignes
     */
    @Override
    public Map<String, ExecutionContext> partition(int gridSize)
    {
        int min = 1;
        int max = count;
        int targetSize = (max - min) / gridSize + 1;

        Map<String, ExecutionContext> result = new HashMap<>();
        
        int number = 0;
        int start = min;
        int end = start + targetSize - 1;

        while (start <= max)
        {
            ExecutionContext value = new ExecutionContext();
            
            result.put("partition" + number, value);

            if (end >= max)
            {
                end = max;
            }
            
            value.putInt("minValue", start);
            value.putInt("maxValue", end);
            
            start += targetSize;
            end += targetSize;
            
            number++;
        }

        return result;
    }
}
