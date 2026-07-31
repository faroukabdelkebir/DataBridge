package wevioo.se.authms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du microservice d'authentification (AuthMS).
 *
 * <p>Ce service centralise l'authentification et la gestion des jetons de l'écosystème :
 * connexion par identifiants, émission et rafraîchissement de JWT, réinitialisation de mot
 * de passe, journalisation des évènements d'authentification et exposition d'un serveur
 * d'autorisation OAuth2.</p>
 *
 * <p>Les annotations activent respectivement :</p>
 * <ul>
 *   <li>{@link EnableDiscoveryClient} — enregistrement auprès du registre de services ;</li>
 *   <li>{@link EnableJpaAuditing} — renseignement automatique des champs d'audit JPA ;</li>
 *   <li>{@link EnableAsync} — exécution asynchrone (envoi de mails, écriture des logs d'audit) ;</li>
 *   <li>{@link EnableScheduling} — tâches planifiées (purge des jetons expirés) ;</li>
 *   <li>{@link ConfigurationPropertiesScan} — détection des classes de configuration typée
 *       telles que {@link wevioo.se.authms.Config.AppProperties}.</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "wevioo.se.authms")
public class AuthMsApplication {

    /** Constructeur par défaut, utilisé par Spring Boot pour instancier la classe de démarrage. */
    public AuthMsApplication() {
    }

    /**
     * Démarre le contexte Spring Boot du microservice.
     *
     * @param args arguments de la ligne de commande transmis à Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthMsApplication.class, args);
    }

}
