/**
 * Microservice de gestion des utilisateurs de la plateforme bancaire.
 *
 * <p>Le service est organisé en couches, chacune isolée dans son propre
 * paquetage :</p>
 * <ul>
 *   <li>{@link wevioo.se.userms.Controller} — exposition HTTP ;</li>
 *   <li>{@link wevioo.se.userms.Services} — règles métier et transactions ;</li>
 *   <li>{@link wevioo.se.userms.Repositories} — accès aux données ;</li>
 *   <li>{@link wevioo.se.userms.Entities} — modèle persistant ;</li>
 *   <li>{@link wevioo.se.userms.Dto} — contrats d'échange de l'API ;</li>
 *   <li>{@link wevioo.se.userms.Mapper} — conversion entité vers DTO ;</li>
 *   <li>{@link wevioo.se.userms.Exceptions} — erreurs métier et leur traduction HTTP ;</li>
 *   <li>{@link wevioo.se.userms.Config} — sécurité et persistance.</li>
 * </ul>
 *
 * <p>Règle transversale : l'entité {@link wevioo.se.userms.Entities.User} ne
 * franchit jamais la couche service, et l'empreinte du mot de passe n'est
 * exposée que par les endpoints internes réservés au service
 * d'authentification.</p>
 */
package wevioo.se.userms;
