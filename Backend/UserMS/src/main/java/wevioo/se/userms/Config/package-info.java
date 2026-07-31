/**
 * Classes de configuration Spring du microservice.
 *
 * <p>On y trouve la configuration de la sécurité HTTP
 * ({@link wevioo.se.userms.Config.SecurityConfig}), qui fait du service un
 * resource server OAuth2 sans état, et celle de la persistance
 * ({@link wevioo.se.userms.Config.JpaConfig}), qui active l'audit JPA et la
 * détection des repositories.</p>
 *
 * <p>Le reste du paramétrage — port, source de données, URL du serveur
 * d'autorisation, Eureka — est déclaré dans {@code application.yml} et
 * surchargeable par variables d'environnement.</p>
 */
package wevioo.se.userms.Config;
