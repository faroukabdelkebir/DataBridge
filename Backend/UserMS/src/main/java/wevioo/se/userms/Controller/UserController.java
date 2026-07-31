package wevioo.se.userms.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import wevioo.se.userms.Dto.*;
import wevioo.se.userms.Entities.Role;
import wevioo.se.userms.Services.UserService;

import java.net.URI;
import java.util.UUID;

/**
 * API REST publique de gestion des utilisateurs, exposée sous
 * {@code /api/v1/users}.
 *
 * <p>Chaque opération exige un JWT valide portant le scope adéquat :
 * {@code user:read} pour les consultations, {@code user:write} pour les
 * écritures, {@code batch:execute} pour l'enregistrement des connexions. Ces
 * règles sont posées deux fois, dans
 * {@link wevioo.se.userms.Config.SecurityConfig} au niveau des URL et par
 * {@link PreAuthorize} au niveau des méthodes : la seconde barrière protège
 * l'endpoint même si le mappage d'URL venait à changer.</p>
 *
 * <p>Le contrôleur ne contient aucune logique métier ; il traduit les requêtes
 * HTTP en appels à {@link UserService} et les résultats en réponses. Les erreurs
 * sont converties en {@code application/problem+json} par
 * {@link wevioo.se.userms.Exceptions.GlobalExceptionHandler}.</p>
 *
 * @see InternalUserController
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    /** Couche métier à laquelle toutes les opérations sont déléguées. */
    private final UserService userService;


    /**
     * Crée un utilisateur.
     *
     * <p>Répond {@code 201 Created} avec un en-tête {@code Location} pointant
     * vers la ressource nouvellement créée.</p>
     *
     * @param request données du compte à créer, validées par Bean Validation
     * @return le compte créé, accompagné de son URI
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_user:write')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /users – creating user");

        UserResponse created = userService.createUser(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }


    /**
     * Consulte un utilisateur par son identifiant.
     *
     * @param id identifiant du compte recherché
     * @return {@code 200 OK} avec la vue complète du compte
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_user:read', 'SCOPE_user:write')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Recherche paginée d'utilisateurs.
     *
     * <p>Les trois filtres sont facultatifs et cumulables ; en l'absence de
     * paramètre, l'ensemble des comptes est renvoyé page par page.</p>
     *
     * @param search   terme libre recherché dans le nom de connexion ou l'e-mail
     * @param role     rôle exact attendu
     * @param active   statut d'activation attendu
     * @param pageable pagination issue des paramètres {@code page}, {@code size}
     *                 et {@code sort} ; par défaut 20 éléments triés par date de
     *                 création
     * @return {@code 200 OK} avec la page de résumés correspondants
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_user:read', 'SCOPE_user:write')")
    public ResponseEntity<Page<UserSummary>> searchUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(userService.searchUsers(search, role, active, pageable));
    }


    /**
     * Met à jour partiellement un utilisateur.
     *
     * <p>Seuls les champs présents dans la charge utile sont appliqués.</p>
     *
     * @param id      identifiant du compte à modifier
     * @param request champs à modifier, validés par Bean Validation
     * @return {@code 200 OK} avec la vue complète du compte après modification
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_user:write')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("PATCH /users/{} – updating user", id);
        return ResponseEntity.ok(userService.updateUser(id, request));
    }


    /**
     * Désactive un utilisateur sans le supprimer.
     *
     * <p>Opération idempotente.</p>
     *
     * @param id identifiant du compte à désactiver
     * @return {@code 204 No Content}
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('SCOPE_user:write')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        log.info("PATCH /users/{}/deactivate", id);
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }


    /**
     * Réactive un utilisateur désactivé.
     *
     * <p>Opération idempotente.</p>
     *
     * @param id identifiant du compte à réactiver
     * @return {@code 204 No Content}
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('SCOPE_user:write')")
    public ResponseEntity<Void> activateUser(@PathVariable UUID id) {
        log.info("PATCH /users/{}/activate", id);
        userService.activateUser(id);
        return ResponseEntity.noContent().build();
    }


    /**
     * Supprime définitivement un utilisateur.
     *
     * <p>Action irréversible : la désactivation est à privilégier lorsque
     * l'historique doit être conservé.</p>
     *
     * @param id identifiant du compte à supprimer
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_user:write')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        log.info("DELETE /users/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }


    /**
     * Enregistre une connexion réussie sur un compte.
     *
     * <p>Endpoint appelé par le service d'authentification, d'où le scope
     * spécifique {@code batch:execute}. Un identifiant inconnu est ignoré
     * silencieusement et renvoie tout de même {@code 204}.</p>
     *
     * @param id identifiant du compte qui vient de se connecter
     * @return {@code 204 No Content}
     */
    @PostMapping("/{id}/login-event")
    @PreAuthorize("hasAuthority('SCOPE_batch:execute')")
    public ResponseEntity<Void> recordLoginEvent(@PathVariable UUID id) {
        userService.recordLogin(id);
        return ResponseEntity.noContent().build();
    }






}
