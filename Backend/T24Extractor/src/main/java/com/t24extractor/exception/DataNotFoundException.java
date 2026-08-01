package com.t24extractor.exception;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Donnée attendue introuvable, côté API comme côté batch.
 *
 * <p>Annotée {@code @ResponseStatus} : remontée depuis un contrôleur, elle est
 * rendue en 404 sans traitement supplémentaire. Levée dans un tasklet, elle
 * fait échouer le step — l'absence de la donnée y traduit une base T24 mal
 * chargée.
 */
@RequiredArgsConstructor
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DataNotFoundException extends Exception{

    /**
     * @param message description de la donnée manquante
     */
    public DataNotFoundException(String message) {
        super(message);
    }
}
