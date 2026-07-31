package wevioo.se.authms.Services.Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Utilitaire de génération et de hachage des jetons opaques.
 *
 * <p>Les jetons de rafraîchissement et de réinitialisation sont transmis en clair à
 * l'utilisateur mais stockés sous forme d'empreinte : une lecture de la base ne permet donc
 * pas d'usurper une session ni de réinitialiser un mot de passe.</p>
 *
 * <p>SHA-256 est employé sans sel ni itérations, contrairement à ce que l'on ferait pour un
 * mot de passe. C'est adapté ici : ces jetons sont aléatoires sur 256 bits et donc hors de
 * portée d'une attaque par dictionnaire ou par table précalculée.</p>
 *
 * <p>Classe utilitaire non instanciable.</p>
 *
 * @see wevioo.se.authms.Entities.RefreshToken
 * @see wevioo.se.authms.Entities.PasswordResetToken
 */
public final class TokenHashUtil {

    /** Générateur d'aléa cryptographique, sûr pour un usage concurrent. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Constructeur privé : cette classe n'est pas destinée à être instanciée. */
    private TokenHashUtil() {}

    /**
     * Génère un jeton aléatoire de 256 bits.
     *
     * <p>Encodé en Base64 URL-safe sans caractères de remplissage, il peut être placé
     * directement dans une URL ou un corps JSON.</p>
     *
     * @return le jeton en clair, à transmettre à l'utilisateur et à ne jamais stocker tel quel
     */
    public static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Calcule l'empreinte SHA-256 d'un jeton, sous forme hexadécimale.
     *
     * <p>Déterministe : hacher deux fois le même jeton donne la même empreinte, ce qui permet
     * de rechercher un jeton en base sans jamais y stocker sa forme en clair.</p>
     *
     * @param rawToken jeton en clair à hacher
     * @return l'empreinte hexadécimale, longue de 64 caractères
     * @throws IllegalStateException si l'algorithme SHA-256 est indisponible sur la plateforme
     */
    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}