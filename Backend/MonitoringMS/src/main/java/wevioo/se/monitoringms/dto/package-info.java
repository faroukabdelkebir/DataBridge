/**
 * Objets de transfert du contrat HTTP de supervision technique.
 *
 * <p>Ces classes forment le contrat exposé au front : leurs noms de champs se
 * retrouvent tels quels dans le JSON produit. Les renommer constitue une
 * rupture de compatibilité.</p>
 *
 * <p>Ils se distinguent des modèles du paquet
 * {@link wevioo.se.monitoringms.model} sur deux points : les agrégats calculés
 * y sont matérialisés en champs, et les compteurs absents en base sont
 * normalisés à zéro pour simplifier l'affichage.</p>
 *
 * <p><strong>Internationalisation.</strong> Les messages destinés à
 * l'utilisateur sont transmis sous forme de clés accompagnées de leurs
 * paramètres d'interpolation, le rendu final revenant au client. Quelques
 * champs de texte brut subsistent pour les clients antérieurs à cette
 * évolution.</p>
 *
 * <p>Les indicateurs métier disposent de leur propre paquet,
 * {@link wevioo.se.monitoringms.dto.analytics}.</p>
 */
package wevioo.se.monitoringms.dto;
