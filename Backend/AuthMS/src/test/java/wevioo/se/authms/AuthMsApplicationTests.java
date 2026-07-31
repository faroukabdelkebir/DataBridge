package wevioo.se.authms;

import org.junit.jupiter.api.Test;

/**
 * Tests de la classe de démarrage de l'application.
 */
class AuthMsApplicationTests {

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    AuthMsApplicationTests() {
    }

    /**
     * Vérifie que la classe de démarrage est présente et chargeable.
     *
     * <p>Ce test délibérément minimal ne charge pas le contexte Spring, celui-ci exigeant une
     * base de données et un serveur de messagerie.</p>
     */
    @Test
    void applicationClassExists() {
        org.assertj.core.api.Assertions.assertThat(AuthMsApplication.class).isNotNull();
    }

}
