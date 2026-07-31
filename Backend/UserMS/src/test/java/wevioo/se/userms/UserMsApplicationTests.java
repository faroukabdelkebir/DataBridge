package wevioo.se.userms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fumée sur la classe de démarrage.
 *
 * <p>Il vérifie uniquement que {@link UserMsApplication} est présente sur le
 * classpath. Le chargement complet du contexte Spring n'est volontairement pas
 * testé ici : il exigerait une base MySQL et un serveur Eureka joignables, ce
 * qui rendrait la construction dépendante d'infrastructures externes.</p>
 */
class UserMsApplicationTests {
    /** Vérifie que la classe de démarrage est bien disponible. */
    @Test
    void applicationClassIsAvailable() {
        assertThat(UserMsApplication.class).isNotNull();
    }
}
