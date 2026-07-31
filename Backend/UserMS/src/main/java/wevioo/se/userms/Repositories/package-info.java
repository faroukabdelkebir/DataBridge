/**
 * Couche d'accès aux données.
 *
 * <p>{@link wevioo.se.userms.Repositories.UserRepository} étend
 * {@code JpaRepository} et complète les opérations CRUD par des méthodes
 * dérivées du nom — recherche et test d'existence par nom de connexion ou par
 * e-mail — ainsi que deux requêtes JPQL : la recherche multicritère paginée et
 * la mise à jour ciblée de la date de dernière connexion.</p>
 *
 * <p>Les implémentations sont générées au démarrage par Spring Data, activé dans
 * {@link wevioo.se.userms.Config.JpaConfig}.</p>
 */
package wevioo.se.userms.Repositories;
