package com.t24extractor.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

/**
 * Le cache Caffeine porte les lectures répétées du STANDARD.SELECTION pendant
 * une extraction : sans gestionnaire de cache enregistré, les annotations
 * {@code @Cacheable} deviennent silencieusement inopérantes.
 */
class CacheConfigurationTest {

    private final CacheConfiguration configuration = new CacheConfiguration();

    @Test
    void providesACaffeineBackedCacheManager() {
        CacheManager cacheManager = configuration.cacheManager(configuration.ssCache());

        assertNotNull(cacheManager);
        // Les caches sont créés à la demande, sans déclaration préalable.
        assertNotNull(cacheManager.getCache("sysFieldOds"));
    }

    @Test
    void storesAndReturnsCachedValues() {
        CacheManager cacheManager = configuration.cacheManager(configuration.ssCache());
        var cache = cacheManager.getCache("standardSelection");

        cache.put("CUSTOMER", "{\"NAME\":\"String.c1\"}");

        assertEquals("{\"NAME\":\"String.c1\"}", cache.get("CUSTOMER").get());
    }
}
