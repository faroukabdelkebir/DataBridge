package wevioo.se.userms.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration de la couche de persistance JPA.
 *
 * <p>Cette classe active deux mécanismes :</p>
 * <ul>
 *   <li>l'audit JPA, qui alimente automatiquement les champs
 *       {@code createdAt} et {@code updatedAt} de
 *       {@link wevioo.se.userms.Entities.User} à chaque insertion ou mise à jour ;</li>
 *   <li>la détection des interfaces {@code Repository} dans le paquetage
 *       {@code wevioo.se.userms.Repositories}, pour lesquelles Spring Data
 *       génère les implémentations au démarrage.</li>
 * </ul>
 *
 * <p>Elle ne contient aucun bean : tout est déclaratif via les annotations.</p>
 *
 * @see wevioo.se.userms.Repositories.UserRepository
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "wevioo.se.userms.Repositories")
public class JpaConfig {
}
