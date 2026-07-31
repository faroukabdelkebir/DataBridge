package wevioo.se.monitoringms.config;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wevioo.se.monitoringms.service.AnalyticsService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Déclaration des caches Caffeine, avec une durée de vie propre à chaque
 * famille de données.
 *
 * <p>Les caches ne sont pas créés à la demande : chacun est déclaré
 * explicitement afin de lui associer sa propre durée de rétention, choisie
 * selon la fraîcheur attendue.</p>
 *
 * <table border="1">
 *   <caption>Durées de rétention par cache</caption>
 *   <tr><th>Cache</th><th>Durée</th><th>Justification</th></tr>
 *   <tr><td>{@value #CACHE_LIVE_JOBS}</td><td>20 secondes</td><td>données quasi temps réel</td></tr>
 *   <tr><td>{@value #CACHE_DASHBOARD}</td><td>30 secondes</td><td>vue d'ensemble rafraîchie souvent</td></tr>
 *   <tr><td>{@value #CACHE_KPIS}</td><td>2 minutes</td><td>agrégats tolérant un léger décalage</td></tr>
 *   <tr><td>{@value #CACHE_SCHEMA}</td><td>10 minutes</td><td>dictionnaire rarement modifié</td></tr>
 *   <tr><td>{@value #CACHE_CONFIG}</td><td>5 minutes</td><td>paramétrage d'extraction</td></tr>
 *   <tr><td>{@value #CACHE_REPORTS}</td><td>15 minutes</td><td>rapports coûteux à produire</td></tr>
 *   <tr><td>{@code analytics}</td><td>5 minutes</td><td>indicateurs métier</td></tr>
 * </table>
 *
 * <p>Les statistiques Caffeine sont activées sur tous les caches et
 * consultables via les endpoints Actuator.</p>
 */
@Configuration
public class CacheConfig {

    /** Cache du résumé du tableau de bord. */
    public static final String CACHE_DASHBOARD   = "dashboard";

    /** Cache des indicateurs de performance et de la réconciliation. */
    public static final String CACHE_KPIS        = "kpis";

    /** Cache du dictionnaire de schéma T24. */
    public static final String CACHE_SCHEMA      = "schema";

    /** Cache du paramétrage des extractions. */
    public static final String CACHE_CONFIG      = "batchConfig";

    /** Cache des rapports journaliers et hebdomadaires. */
    public static final String CACHE_REPORTS     = "reports";

    /** Cache de la liste des traitements en cours. */
    public static final String CACHE_LIVE_JOBS   = "liveJobs";

    /**
     * Construit le gestionnaire de caches de l'application.
     *
     * <p>Un cache absent de cette liste provoquerait une erreur à la première
     * invocation d'une méthode {@code @Cacheable} le référençant : toute
     * nouvelle constante de cache doit donc être ajoutée ici.</p>
     *
     * @return le gestionnaire renseigné avec l'ensemble des caches déclarés
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build(CACHE_LIVE_JOBS, 20, TimeUnit.SECONDS,  200),
                build(CACHE_DASHBOARD, 30, TimeUnit.SECONDS,  200),
                build(CACHE_KPIS,       2, TimeUnit.MINUTES,  500),
                build(CACHE_SCHEMA,    10, TimeUnit.MINUTES,  1000),
                build(CACHE_CONFIG,     5, TimeUnit.MINUTES,  200),
                build(CACHE_REPORTS,   15, TimeUnit.MINUTES,  100),
                build(AnalyticsService.CACHE_ANALYTICS, 5, TimeUnit.MINUTES, 500)
        ));
        return manager;
    }

    /**
     * Crée un cache Caffeine expirant à durée fixe après écriture.
     *
     * @param name     nom du cache, tel que référencé par {@code @Cacheable}
     * @param duration durée de rétention après écriture
     * @param unit     unité de la durée
     * @param maxSize  nombre maximal d'entrées avant éviction
     * @return le cache configuré, avec collecte des statistiques activée
     */
    private CaffeineCache build(String name, long duration, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(duration, unit)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }

}
