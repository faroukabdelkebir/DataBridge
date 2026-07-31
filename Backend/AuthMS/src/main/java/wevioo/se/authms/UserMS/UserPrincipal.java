package wevioo.se.authms.UserMS;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Représentation de l'utilisateur authentifié dans le contexte de sécurité Spring.
 *
 * <p>Enrichit le contrat {@link UserDetails} de l'identifiant, de l'adresse e-mail et du rôle,
 * ce qui permet aux contrôleurs d'accéder à ces informations via
 * {@code @AuthenticationPrincipal} sans requête supplémentaire.</p>
 *
 * <p>Le champ mot de passe n'est renseigné que lors de la vérification des identifiants à la
 * connexion ; l'instance reconstituée à partir d'un JWT le laisse vide.</p>
 *
 * @see wevioo.se.authms.Security.JwtAuthenticationFilter
 */
@Getter
public class UserPrincipal implements UserDetails {

    /** Identifiant unique de l'utilisateur. */
    private final UUID userId;

    /** Nom d'utilisateur servant d'identifiant de connexion. */
    private final String username;

    /** Adresse e-mail de l'utilisateur. */
    private final String email;

    /** Empreinte du mot de passe ; vide hors phase de vérification des identifiants. */
    private final String password;

    /** Rôle applicatif, sans le préfixe {@code ROLE_}. */
    private final String role;

    /** Indique si le compte est actif. */
    private final boolean active;

    /**
     * Construit le principal à partir des attributs de l'utilisateur.
     *
     * <p>Porte l'annotation {@code @Builder} : c'est ce constructeur qui alimente
     * {@code UserPrincipal.builder()}.</p>
     *
     * @param userId   identifiant unique de l'utilisateur
     * @param username nom d'utilisateur
     * @param email    adresse e-mail
     * @param password empreinte du mot de passe, vide hors vérification des identifiants
     * @param role     rôle applicatif, sans le préfixe {@code ROLE_}
     * @param active   indique si le compte est actif
     */
    @Builder
    public UserPrincipal(UUID userId, String username, String email,
                         String password, String role, boolean active) {
        this.userId   = userId;
        this.username = username;
        this.email    = email;
        this.password = password;
        this.role     = role;
        this.active   = active;
    }

    /**
     * Retourne les autorisations de l'utilisateur.
     *
     * <p>Le rôle est préfixé de {@code ROLE_}, convention attendue par les expressions
     * {@code hasRole(...)} de Spring Security.</p>
     *
     * @return la liste des autorisations, réduite au rôle de l'utilisateur
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** {@return l'empreinte du mot de passe, vide hors vérification des identifiants} */
    @Override public String getPassword()                          { return password; }

    /** {@return le nom d'utilisateur} */
    @Override public String getUsername()                          { return username; }

    /** {@return toujours {@code true} : l'expiration de compte n'est pas gérée} */
    @Override public boolean isAccountNonExpired()                 { return true; }

    /** {@return {@code true} si le compte est actif ; un compte désactivé est considéré verrouillé} */
    @Override public boolean isAccountNonLocked()                  { return active; }

    /** {@return toujours {@code true} : l'expiration des identifiants n'est pas gérée} */
    @Override public boolean isCredentialsNonExpired()             { return true; }

    /** {@return {@code true} si le compte est actif} */
    @Override public boolean isEnabled()                           { return active; }
}
