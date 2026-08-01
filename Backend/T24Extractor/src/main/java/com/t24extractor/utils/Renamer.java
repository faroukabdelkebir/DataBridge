package com.t24extractor.utils;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Traduction des noms de tables entre les conventions T24, Oracle et SQL Server.
 *
 * <p>T24 emploie des points et le caractère {@code $}, qu'aucune des deux bases
 * cibles n'accepte dans un identifiant non échappé. Selon l'usage, le nom doit
 * être normalisé pour interroger TAFJ_VOC et F_FILE_CONTROL, ou converti pour
 * nommer la table BI.
 *
 */
public class Renamer {
    /**
     *
     * @param t24Name Le nom de la table T24 enregistre dans la table batch_config
     * @return nouveau format du nom de la table
     * Ex: ACCOUNT$HIS ==> ACCOUNT
     */
    public String ssRenamer(String t24Name){
        String t24name =  Optional.ofNullable(t24Name)
                .orElseThrow(() ->
                        new NoSuchElementException
                                (String.format("Veuillez verifier que la valeur %s est correcte", t24Name)));
        if (t24name.contains("$")){
            t24name = t24name.substring(0, t24name.indexOf("$"));
        }
        return t24name;
    }

    /**
     *
     * @param t24Name Le nom de la table T24 enregistre dans la table batch_config
     * @return nouveau format du nom de la table
     * Ex: ACCOUNT$HIS ==> ACCOUNT#HIS, FUNDS.TRANSFER ==> FUNDS_TRANSFER
     */
    public String biRenamer(String t24Name){
        return Optional.ofNullable(t24Name)
                .orElseThrow(() ->
                        new NoSuchElementException
                                (String.format("Veuillez verifier que la valeur %s est correcte", t24Name)))
                .replace(".", "_")
                .replace("$", "#");
    }
}
