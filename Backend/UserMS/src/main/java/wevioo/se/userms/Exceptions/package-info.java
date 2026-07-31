/**
 * Erreurs métier et traduction en réponses HTTP.
 *
 * <p>Deux exceptions non contrôlées expriment les échecs prévus par le domaine :
 * {@link wevioo.se.userms.Exceptions.ResourceNotFoundException} pour une
 * ressource absente et {@link wevioo.se.userms.Exceptions.ConflictException}
 * pour un nom de connexion ou un e-mail déjà pris.</p>
 *
 * <p>{@link wevioo.se.userms.Exceptions.GlobalExceptionHandler} les intercepte,
 * ainsi que les erreurs de validation et de sécurité, et produit des réponses
 * homogènes au format {@code application/problem+json} (RFC 7807).</p>
 */
package wevioo.se.userms.Exceptions;
