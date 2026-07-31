package wevioo.se.userms;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


/**
 * Point d'entrée du microservice de gestion des utilisateurs (UserMS).
 *
 * <p>Ce service expose le référentiel des utilisateurs de la plateforme bancaire :
 * création, consultation, mise à jour, activation/désactivation et suppression.
 * Il joue également le rôle de source de vérité des identifiants (hash de mot de
 * passe) pour le service d'authentification, via les endpoints internes.</p>
 *
 * <p>L'annotation {@link EnableDiscoveryClient} enregistre l'instance auprès du
 * serveur Eureka afin qu'elle soit joignable par les autres microservices sans
 * adresse codée en dur.</p>
 *
 * @see wevioo.se.userms.Controller.UserController
 * @see wevioo.se.userms.Controller.InternalUserController
 */
@SpringBootApplication
@EnableDiscoveryClient
public class UserMsApplication {

    /**
     * Démarre le contexte Spring Boot du microservice.
     *
     * @param args arguments de la ligne de commande transmis à Spring Boot
     *             (par exemple {@code --server.port=8085})
     */
    public static void main(String[] args) { SpringApplication.run(UserMsApplication.class, args);}


}
