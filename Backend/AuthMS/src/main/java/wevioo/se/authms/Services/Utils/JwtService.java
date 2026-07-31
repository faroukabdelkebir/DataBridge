package wevioo.se.authms.Services.Utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import wevioo.se.authms.Config.AppProperties;
import wevioo.se.authms.UserMS.UserPrincipal;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service d'émission et de lecture des jetons d'accès JWT applicatifs.
 *
 * <p>Les jetons sont signés en HMAC-SHA256 avec le secret partagé de la configuration.
 * Ce mécanisme est distinct du serveur d'autorisation OAuth2, qui signe ses propres jetons
 * avec une clé RSA : celui-ci sert aux authentifications utilisateur de l'API, celui-là aux
 * échanges machine-à-machine.</p>
 *
 * <p>Le jeton porte le nom d'utilisateur en sujet ainsi que l'identifiant, l'adresse e-mail
 * et le rôle dans ses revendications, ce qui permet de reconstituer le principal sans accès
 * à la base.</p>
 *
 * @see wevioo.se.authms.Security.JwtAuthenticationFilter
 */
@Slf4j
@Component
public class JwtService {

    /** Configuration fournissant le secret de signature et les durées de validité. */
    private final AppProperties props;

    /**
     * Construit le service à partir de la configuration applicative.
     *
     * @param props configuration applicative du service
     */
    public JwtService(AppProperties props) {
        this.props = props;
    }

    /**
     * Construit la clé de signature à partir du secret encodé en Base64.
     *
     * @return la clé secrète HMAC utilisée pour signer et vérifier les jetons
     */
    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(props.getJwt().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Émet un jeton d'accès pour un utilisateur authentifié.
     *
     * <p>Le rôle est publié à la fois en {@code role} et en {@code roles} — sous forme de
     * liste — afin de rester compatible avec les services en aval attendant l'une ou l'autre
     * forme.</p>
     *
     * @param user principal de l'utilisateur authentifié
     * @return le jeton d'accès signé, sous forme compacte
     */
    public String generateAccessToken(UserPrincipal user) {
        return buildToken(Map.of(
                "userId", user.getUserId().toString(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "roles", List.of(user.getRole())
        ), user.getUsername(), props.getJwt().getAccessTokenExpirationMs());
    }

    /**
     * Émet un jeton d'accès à partir des attributs de l'utilisateur.
     *
     * <p>Variante utile lorsque aucun {@link UserPrincipal} n'est disponible, notamment lors
     * d'un rafraîchissement de jeton.</p>
     *
     * @param userId   identifiant de l'utilisateur
     * @param username nom d'utilisateur, placé en sujet du jeton
     * @param email    adresse e-mail
     * @param role     rôle applicatif
     * @return le jeton d'accès signé, sous forme compacte
     */
    public String generateAccessToken(UUID userId, String username, String email, String role) {
        return buildToken(Map.of(
                "userId", userId.toString(),
                "email", email,
                "role", role,
                "roles", List.of(role)
        ), username, props.getJwt().getAccessTokenExpirationMs());
    }

    /**
     * Assemble et signe un jeton JWT.
     *
     * @param extraClaims  revendications applicatives à inclure
     * @param subject      sujet du jeton, ici le nom d'utilisateur
     * @param expirationMs durée de validité en millisecondes, comptée à partir de l'instant courant
     * @return le jeton signé, sous forme compacte
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(props.getOauth2().getIssuer())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Vérifie la signature et la validité temporelle d'un jeton.
     *
     * <p>Ne lève aucune exception : un jeton expiré est journalisé en {@code debug}, la
     * situation étant attendue, tandis qu'un jeton dont la signature ou la structure est
     * invalide est journalisé en {@code warn}, car il peut trahir une tentative de forgeage.</p>
     *
     * @param token jeton à vérifier
     * @return {@code true} si le jeton est correctement signé et non expiré
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Vérifie qu'un jeton est valide et qu'il a bien été émis pour l'utilisateur attendu.
     *
     * @param token            jeton à vérifier
     * @param expectedUsername nom d'utilisateur attendu en sujet du jeton
     * @return {@code true} si le jeton est valide et correspond à cet utilisateur
     */
    public boolean isValid(String token, String expectedUsername) {
        return isValid(token) && extractUsername(token).equals(expectedUsername);
    }

    /**
     * Extrait le nom d'utilisateur porté par le sujet du jeton.
     *
     * @param token jeton à lire
     * @return le nom d'utilisateur
     * @throws JwtException si le jeton est invalide ou expiré
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrait l'identifiant de l'utilisateur porté par la revendication {@code userId}.
     *
     * @param token jeton à lire
     * @return l'identifiant de l'utilisateur
     * @throws JwtException             si le jeton est invalide ou expiré
     * @throws IllegalArgumentException si la revendication est absente ou mal formée
     */
    public UUID extractUserId(String token) {
        String userId = extractClaim(token, claims -> claims.get("userId", String.class));
        return UUID.fromString(userId);
    }

    /**
     * Extrait le rôle porté par la revendication {@code role}.
     *
     * @param token jeton à lire
     * @return le rôle applicatif de l'utilisateur
     * @throws JwtException si le jeton est invalide ou expiré
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extrait l'adresse e-mail portée par la revendication {@code email}.
     *
     * @param token jeton à lire
     * @return l'adresse e-mail de l'utilisateur
     * @throws JwtException si le jeton est invalide ou expiré
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /**
     * Extrait la date d'expiration du jeton.
     *
     * @param token jeton à lire
     * @return l'instant d'expiration
     * @throws JwtException si le jeton est invalide ou expiré
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extrait une revendication arbitraire du jeton.
     *
     * <p>Point d'entrée générique sur lequel s'appuient les méthodes d'extraction spécialisées.
     * La signature est vérifiée avant application de la fonction.</p>
     *
     * @param <T>      type de la valeur extraite
     * @param token    jeton à lire
     * @param resolver fonction appliquée aux revendications du jeton
     * @return la valeur produite par la fonction
     * @throws JwtException si le jeton est invalide ou expiré
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }

    /**
     * Analyse un jeton et retourne ses revendications après vérification de la signature.
     *
     * @param token jeton à analyser
     * @return les revendications du jeton
     * @throws ExpiredJwtException si le jeton est expiré
     * @throws JwtException        si la signature ou la structure du jeton est invalide
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
