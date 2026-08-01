package com.t24extractor.processor;


import org.jspecify.annotations.Nullable;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Équivalent de {@link XMLFilterProcessor} pour les tables lues en mode BLOB.
 *
 * <p>Écarte les lignes dont l'identifiant figure déjà dans la clé {@code IDS}
 * du contexte de job.
 */
public class BlobFilterProcessor implements ItemProcessor<HashMap<String, Object>, HashMap<String, Object>>
{
    @Value("#{jobExecutionContext['IDS']}")
    HashSet<String> idsList;

    /**
     * {@inheritDoc}
     *
     * @return la ligne, ou {@code null} pour l'exclure du chunk
     */
    @Override
    public  @Nullable HashMap< String, Object> process(HashMap<String, Object> item) throws Exception
    {
        if (idsList.contains(item.get("_id")))
        {
            return null;
        } 
        else 
        {
            return item;
        }
    }
}
