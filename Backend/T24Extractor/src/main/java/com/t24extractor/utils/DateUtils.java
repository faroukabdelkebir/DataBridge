package com.t24extractor.utils;

import java.util.Date;

/**
 * Formatage des durées d'exécution pour les journaux.
 */
public  class DateUtils {

  private DateUtils() {
    /* This utility class should not be instantiated */
  }

/**
 * Écart entre deux instants, au format {@code HH:mm:ss}.
 *
 * <p>L'ordre des arguments est indifférent : la valeur absolue est retournée.
 * Au-delà de 24 heures, le champ des heures dépasse 24 plutôt que de repartir
 * de zéro.
 *
 * @param d1 première date
 * @param d2 seconde date
 * @return la durée écoulée, par exemple {@code 02:03:04}
 */
public static String differenceBetween(Date d1, Date d2) {
	
	long diff;

    if (d1.getTime() > d2.getTime()) {
        diff = (int) (d1.getTime() - d2.getTime());

    } else
        diff = (int) (d2.getTime() - d1.getTime());
	long timeDifference = diff / 1000;
    int h = (int) (timeDifference / (3600));
    int m = (int) ((timeDifference - (h * 3600)) / 60);
    int s = (int) (timeDifference - (h * 3600) - m * 60);

    return String.format("%02d:%02d:%02d", h, m, s);
}
}
