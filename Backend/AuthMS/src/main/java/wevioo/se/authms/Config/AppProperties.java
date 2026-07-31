package wevioo.se.authms.Config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration typée du microservice, liée au préfixe {@code app} des fichiers de propriétés.
 *
 * <p>Regroupe les réglages de sécurité et de cycle de vie des jetons. Les contraintes de
 * validation sont vérifiées au démarrage grâce à {@link Validated} : une propriété manquante
 * ou incohérente empêche le contexte Spring de se charger plutôt que de provoquer une erreur
 * à l'exécution.</p>
 *
 * <p>Exemple de configuration :</p>
 * <pre>
 * app.jwt.secret=...
 * app.jwt.access-token-expiration-ms=3600000
 * app.oauth2.issuer=http://localhost:8090
 * app.reset-password.base-url=http://localhost:4200
 * </pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Réglages des jetons JWT (secret de signature et durées de validité). */
    private final Jwt jwt = new Jwt();

    /** Réglages du serveur d'autorisation OAuth2. */
    private final Oauth2 oauth2 = new Oauth2();

    /** Réglages du parcours de réinitialisation de mot de passe. */
    private final ResetPassword resetPassword = new ResetPassword();

    /** Constructeur par défaut, utilisé par Spring lors de la liaison des propriétés. */
    public AppProperties() {
    }

    /**
     * Paramètres de signature et d'expiration des jetons JWT.
     */
    @Getter
    @Setter
    public static class Jwt {

        /** Constructeur par défaut ; les valeurs sont ensuite injectées par liaison de propriétés. */
        public Jwt() {
        }

        /** Secret utilisé pour signer et vérifier les JWT. Obligatoire. */
        @NotBlank
        private String secret;

        /** Durée de validité d'un jeton d'accès, en millisecondes (1 heure par défaut). */
        @Positive
        private long accessTokenExpirationMs  = 3_600_000L;

        /** Durée de validité d'un jeton de rafraîchissement, en millisecondes (7 jours par défaut). */
        @Positive
        private long refreshTokenExpirationMs = 604_800_000L;
    }

    /**
     * Paramètres du serveur d'autorisation OAuth2.
     */
    @Getter
    @Setter
    public static class Oauth2 {

        /** Constructeur par défaut ; les valeurs sont ensuite injectées par liaison de propriétés. */
        public Oauth2() {
        }

        /** URL publique de l'émetteur, publiée dans les métadonnées OpenID et la revendication {@code iss}. */
        @NotBlank
        private String issuer = "http://localhost:8090";
    }

    /**
     * Paramètres du parcours « mot de passe oublié ».
     */
    @Getter
    @Setter
    public static class ResetPassword {

        /** Constructeur par défaut ; les valeurs sont ensuite injectées par liaison de propriétés. */
        public ResetPassword() {
        }

        /** Durée de validité d'un jeton de réinitialisation, en minutes. */
        @Positive
        private int tokenExpirationMinutes = 15;

        /** URL de base du front-end utilisée pour construire le lien de réinitialisation envoyé par mail. */
        @NotBlank
        private String baseUrl = "http://localhost:4200";
    }
}