package wevioo.se.monitoringms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du microservice de supervision de l'extraction T24.
 *
 * <p>Le service expose, en lecture seule sur la base décisionnelle SQL Server,
 * le suivi des traitements Spring Batch, les indicateurs de performance, la
 * réconciliation Oracle ↔ SQL Server et les indicateurs analytiques métier.</p>
 *
 * <p>Trois mécanismes transverses sont activés au démarrage :</p>
 * <ul>
 *   <li>l'enregistrement auprès de l'annuaire de services ;</li>
 *   <li>la mise en cache, configurée par
 *       {@link wevioo.se.monitoringms.config.CacheConfig} — un cache non
 *       déclaré y provoquerait une erreur à la première utilisation ;</li>
 *   <li>la planification, qui pilote l'évaluation des alertes et la diffusion
 *       du flux temps réel.</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableScheduling
public class MonitoringMsApplication {

    /**
     * Démarre le microservice.
     *
     * @param args arguments de ligne de commande transmis à Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MonitoringMsApplication.class, args);
    }

}
