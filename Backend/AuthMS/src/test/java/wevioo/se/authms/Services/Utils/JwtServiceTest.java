package wevioo.se.authms.Services.Utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wevioo.se.authms.Config.AppProperties;
import wevioo.se.authms.UserMS.UserPrincipal;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link JwtService}.
 */
class JwtServiceTest {

    /** Service testé, reconstruit avant chaque test. */
    private JwtService jwtService;

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    JwtServiceTest() {
    }

    /**
     * Prépare le service avec une configuration de test : secret de signature fixe, jeton
     * valable une heure et émetteur factice.
     */
    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        props.getJwt().setAccessTokenExpirationMs(3_600_000L);
        props.getOauth2().setIssuer("http://auth.test");
        jwtService = new JwtService(props);
    }

    /**
     * Vérifie qu'un jeton émis pour un utilisateur est valide et que chacune de ses
     * revendications est relue à l'identique.
     */
    @Test
    void generateAccessTokenIncludesStableUserClaims() {
        UUID userId = UUID.randomUUID();
        UserPrincipal user = UserPrincipal.builder()
                .userId(userId)
                .username("alice")
                .email("alice@example.com")
                .role("ADMIN")
                .active(true)
                .build();

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.isValid(token, "alice")).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractExpiration(token)).isAfter(new java.util.Date());
    }

    /**
     * Vérifie qu'un jeton dont la signature a été altérée est rejeté.
     */
    @Test
    void isValidReturnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(), "bob", "bob@example.com", "USER");

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }
}
