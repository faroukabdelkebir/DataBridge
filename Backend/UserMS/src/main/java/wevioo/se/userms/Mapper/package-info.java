/**
 * Conversion des entités vers les DTO exposés par l'API.
 *
 * <p>{@link wevioo.se.userms.Mapper.UserMapper} traduit
 * {@link wevioo.se.userms.Entities.User} en vue complète ou en résumé. Le
 * mapping est écrit à la main plutôt que généré, afin que la liste des champs
 * publiés reste explicite et vérifiable — en particulier l'omission du hash du
 * mot de passe.</p>
 *
 * <p>La conversion inverse relève de la couche service, car elle s'accompagne de
 * règles métier.</p>
 */
package wevioo.se.userms.Mapper;
