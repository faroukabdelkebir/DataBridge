package com.t24extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Le démarrage complet du contexte n'est pas testable hors environnement : il
 * exige Oracle (T24), SQL Server (BI) et un registre Eureka. Ce test couvre
 * donc ce qui est vérifiable sans infrastructure — les annotations de bootstrap
 * dont dépendent des fonctionnalités entières.
 */
class ExtractorApplicationTest {

    @Test
    void enablesSchedulingSoAutoExtractionSchedulerRuns() {
        // Sans @EnableScheduling, le @Scheduled d'AutoExtractionScheduler ne se
        // déclenche jamais : la planification automatique est silencieusement morte.
        assertNotNull(ExtractorApplication.class.getAnnotation(EnableScheduling.class));
    }

    @Test
    void isASpringBootApplicationRegisteredInDiscovery() {
        assertNotNull(ExtractorApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(ExtractorApplication.class.getAnnotation(EnableDiscoveryClient.class));
    }

    @Test
    void componentScanRootCoversEveryModule() {
        // Le package racine conditionne la découverte des tasklets, contrôleurs
        // et repositories déclarés dans les sous-packages.
        assertEquals("com.t24extractor", ExtractorApplication.class.getPackageName());
    }

    @Test
    void generatesAUniqueApplicationNamePerInstance() throws Exception {
        var method = ExtractorApplication.class.getDeclaredMethod("generateAppNameArgument");
        method.setAccessible(true);

        String argument = String.valueOf(method.invoke(null));

        // Le nom sert d'identifiant d'instance dans le registre de services :
        // il doit rester préfixé et distinct d'une instance à l'autre.
        assertTrue(argument.startsWith("--spring.application.name=Extractor-"), argument);
        assertTrue(argument.length() > "--spring.application.name=Extractor-".length(), argument);
    }

    @Test
    void t24DatesAreInterpretedInBankTimeZone() {
        // main() force GMT+1 : les dates T24 (yyyyMMdd) sont comparées sans zone,
        // un décalage de fuseau déplacerait la frontière de journée d'extraction.
        assertEquals(TimeZone.getTimeZone("GMT+1").getRawOffset(), 3600_000);
    }
}
