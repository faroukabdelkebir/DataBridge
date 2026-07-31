package com.t24extractor.processor;

import com.t24extractor.model.T24Table;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;


/**
 * Écarte les enregistrements XML déjà présents en BI.
 *
 * <p>Utilisé lors d'une reprise : les identifiants déjà extraits sont chargés
 * dans le contexte sous la clé {@code IDS}, et les lignes correspondantes sont
 * filtrées plutôt que réinsérées.
 */
public class XMLFilterProcessor implements ItemProcessor<T24Table, T24Table>
{
    @Value("#{jobExecutionContext['IDS']}")
    HashSet<String> idsList;

    /**
     * {@inheritDoc}
     *
     * @return l'enregistrement, ou {@code null} pour l'exclure du chunk
     */
    @Override
    public T24Table process(T24Table item) throws Exception
    {
        if (idsList.contains(item.getRecid()))
        {
            return null;
        } 
        else 
        {
            return item;
        }
    }
}