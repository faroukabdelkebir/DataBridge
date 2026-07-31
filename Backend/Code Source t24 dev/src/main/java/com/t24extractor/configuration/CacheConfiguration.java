package com.t24extractor.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache mémoire des dictionnaires T24.
 *
 * <p>TAFJ_VOC et STANDARD.SELECTION sont relus pour chaque table traitée alors
 * qu'ils ne changent pas pendant une extraction. Les mettre en cache évite
 * autant d'allers-retours vers Oracle.
 *
 * <p>Aucune expiration n'est configurée : le cache vit le temps du processus,
 * et une évolution de dictionnaire T24 suppose un redémarrage.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {
    /**
     * @return un constructeur Caffeine aux réglages par défaut
     */
    @Bean
    public Caffeine<Object, Object> ssCache() {
        return Caffeine.newBuilder();
    }

    /**
     * @param caffeine constructeur de caches
     * @return un gestionnaire créant les caches à la demande
     */
    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}
