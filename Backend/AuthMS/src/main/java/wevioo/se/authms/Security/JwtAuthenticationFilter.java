package wevioo.se.authms.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import wevioo.se.authms.Services.Utils.JwtService;
import wevioo.se.authms.UserMS.UserPrincipal;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


/**
 * Filtre d'authentification par jeton JWT, exécuté une fois par requête.
 *
 * <p>Extrait le jeton porté par l'en-tête {@code Authorization}, le valide et, s'il est
 * exploitable, alimente le contexte de sécurité avec un {@link UserPrincipal} et l'autorité
 * correspondant au rôle. Une requête sans jeton, ou porteuse d'un jeton invalide, poursuit
 * la chaîne sans authentification : c'est aux règles d'autorisation, et non à ce filtre,
 * de décider si l'accès est refusé.</p>
 *
 * @see JwtService
 * @see SecurityConfig
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Service de validation et de lecture des jetons JWT. */
    private final JwtService jwtService;

    /** Préfixe attendu de l'en-tête {@code Authorization}. */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Construit le filtre avec le service de lecture des jetons.
     *
     * @param jwtService service de validation et de lecture des jetons JWT
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Authentifie la requête à partir du jeton JWT présenté, puis poursuit la chaîne de filtres.
     *
     * <p>Le traitement est ignoré lorsque l'en-tête {@code Authorization} est absent ou ne
     * commence pas par {@code Bearer }, ainsi que lorsque le contexte de sécurité porte déjà
     * une authentification — un autre mécanisme s'en étant chargé.</p>
     *
     * <p>Un jeton invalide ou expiré ne provoque pas d'erreur : l'échec est simplement
     * journalisé en {@code debug} et la requête continue sans authentification.</p>
     *
     * @param request     requête entrante
     * @param response    réponse en cours de construction
     * @param filterChain chaîne de filtres à poursuivre
     * @throws ServletException si un filtre suivant échoue
     * @throws IOException      en cas d'erreur d'entrée/sortie
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length());

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.isValid(token)) {
                String username = jwtService.extractUsername(token);
                String role     = jwtService.extractRole(token);
                UUID userId   = jwtService.extractUserId(token);

                UserPrincipal principal = UserPrincipal.builder()
                        .userId(userId)
                        .username(username)
                        .email(jwtService.extractEmail(token))
                        .role(role)
                        .active(true)
                        .password("")
                        .build();

                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority(authority))
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception ex) {
            log.debug("JWT validation failed", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Écarte du filtrage les chemins qui n'exigent pas de jeton.
     *
     * <p>Concerne les points d'entrée publics d'authentification (connexion, rafraîchissement,
     * parcours de mot de passe oublié), les routes du serveur d'autorisation OAuth2 et les
     * sondes de supervision.</p>
     *
     * @param request requête entrante
     * @return {@code true} si le filtre doit être ignoré pour cette requête
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/reset-password")
                || path.startsWith("/oauth2/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
    }
}
