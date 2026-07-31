package com.t24extractor.tasklet;

/**
 * Résolution du préfixe de table T24.
 */
public interface IFileControlService {

    /**
     * Détermine le préfixe sous lequel une table T24 est stockée.
     *
     * @param recid nom de la table T24, avec ou sans suffixe d'historisation
     * @return {@code F} pour un fichier interne, {@code FBNK} sinon
     */
    String classification(String recid);

}
