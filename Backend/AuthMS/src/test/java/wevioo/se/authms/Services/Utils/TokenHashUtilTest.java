package wevioo.se.authms.Services.Utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link TokenHashUtil}.
 */
class TokenHashUtilTest {

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    TokenHashUtilTest() {
    }

    /**
     * Vérifie que le hachage est déterministe et produit bien une empreinte SHA-256
     * hexadécimale de 64 caractères.
     *
     * <p>L'empreinte attendue est figée dans le test : toute évolution de l'algorithme
     * rendrait illisibles les jetons déjà stockés en base et doit donc être détectée.</p>
     */
    @Test
    void hashTokenIsDeterministicSha256Hex() {
        String hash = TokenHashUtil.hashToken("refresh-token");

        assertThat(hash)
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    /**
     * Vérifie que les jetons générés sont utilisables dans une URL et distincts d'un appel
     * à l'autre.
     */
    @Test
    void generateRawReturnsUrlSafeTokenWithEnoughEntropy() {
        String first = TokenHashUtil.generateRaw();
        String second = TokenHashUtil.generateRaw();

        assertThat(first)
                .hasSizeGreaterThanOrEqualTo(40)
                .matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }
}
