package wevioo.se.userms.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.userms.Dto.PasswordUpdateRequest;
import wevioo.se.userms.Dto.UserCredentialsResponse;
import wevioo.se.userms.Services.UserService;

import java.util.UUID;

/**
 * API interne d'accès aux identifiants, exposée sous
 * {@code /api/v1/users/internal}.
 *
 * <p><strong>Ces endpoints ne sont pas destinés aux clients finaux.</strong> Ils
 * permettent au service d'authentification de récupérer l'empreinte d'un mot de
 * passe pour vérifier une connexion, puis de la remplacer lors d'un changement
 * de mot de passe.</p>
 *
 * <p>La protection repose sur des scopes dédiés, déclarés dans
 * {@link wevioo.se.userms.Config.SecurityConfig} :
 * {@code user:credentials:read} pour les lectures et
 * {@code user:credentials:write} pour l'écriture. Un jeton porteur des scopes
 * ordinaires {@code user:read} ou {@code user:write} se voit refuser l'accès.</p>
 *
 * <p>Contrairement à {@link UserController}, un compte introuvable donne lieu à
 * un {@code 404} construit ici même à partir d'un {@code Optional} vide, sans
 * passer par une exception.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

    /** Couche métier à laquelle toutes les opérations sont déléguées. */
    private final UserService userService;

    /**
     * Récupère les identifiants d'un compte par nom de connexion ou par e-mail.
     *
     * <p>Exactement un critère doit être fourni ; s'ils sont tous deux présents,
     * le nom de connexion l'emporte.</p>
     *
     * @param username nom de connexion recherché, facultatif
     * @param email    adresse e-mail recherchée, facultative
     * @return {@code 200 OK} avec les identifiants du compte,
     *         {@code 404 Not Found} si aucun compte ne correspond, ou
     *         {@code 400 Bad Request} si aucun critère n'est fourni
     */
    @GetMapping("/credentials")
    public ResponseEntity<UserCredentialsResponse> getCredentials(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email) {

        log.debug(">>> INTERNAL HIT - username={}, email={}", username, email);

        if (username != null) {
            return userService.findCredentialsByUsername(username)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        if (email != null) {
            return userService.findCredentialsByEmail(email)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.badRequest().build();
    }

    /**
     * Récupère les identifiants d'un compte par son identifiant technique.
     *
     * @param id identifiant du compte
     * @return {@code 200 OK} avec les identifiants du compte, ou
     *         {@code 404 Not Found} si le compte n'existe pas
     */
    @GetMapping("/credentials/{id}")
    public ResponseEntity<UserCredentialsResponse> getCredentialsById(@PathVariable UUID id) {
        log.debug(">>> INTERNAL HIT - id={}", id);
        return userService.findCredentialsById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remplace l'empreinte du mot de passe d'un compte.
     *
     * <p>La valeur transmise est stockée telle quelle : elle doit déjà être
     * encodée par l'appelant. Aucune validation Bean Validation n'est appliquée
     * sur cette charge utile interne.</p>
     *
     * @param id      identifiant du compte concerné
     * @param request charge utile contenant la nouvelle empreinte
     * @return {@code 204 No Content}
     */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable UUID id,
            @RequestBody PasswordUpdateRequest request) {
        log.debug(">>> INTERNAL HIT - updatePassword id={}", id);
        userService.updatePassword(id, request.passwordHash());
        return ResponseEntity.noContent().build();
    }


}
