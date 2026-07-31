package wevioo.se.monitoringms.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requêtes analytiques métier sur les tables extraites de T24.
 *
 * <p><strong>Reconstruction de l'axe temps.</strong> Les identifiants T24
 * encodent la date métier ({@code FTyyddd…}, {@code AABILLyyddd…}) : les
 * séries temporelles restent donc calculables même lorsque la colonne
 * {@code PROCESSING_DATE} n'a pas encore été extraite.</p>
 *
 * <p><strong>Champs multivalués.</strong> Les champs déclarés
 * {@code single_mult = 'M'} sont normalisés par le Json_Flattener de
 * l'extracteur dans des tables satellites nommées {@code TABLE#CHAMP}
 * ({@code _id}, {@code CHAMP}, {@code MV_SEQUENCE}, {@code SEQUENCE}). La
 * colonne homonyme de la table principale ne contient que le JSON brut et ne
 * doit jamais être interrogée pour l'analytique.</p>
 *
 * <p><strong>Dégradation contrôlée.</strong> {@link #hasColumn} et
 * {@link #tableExists} vérifient la présence des colonnes et satellites
 * enrichis avant d'émettre une requête. Les indicateurs concernés retournent
 * {@code null} ou une liste vide tant que l'extraction n'a pas été enrichie,
 * plutôt que de faire échouer l'appel.</p>
 *
 * <p><strong>Construction du SQL.</strong> Plusieurs requêtes interpolent un
 * nom de colonne ou de table dans le texte SQL, ce que les paramètres liés ne
 * permettent pas. Ces noms proviennent exclusivement des listes fermées
 * {@link #CUSTOMER_DIMENSIONS} et {@link #BILLING_DIMENSIONS}, ou de
 * constantes internes : aucune saisie utilisateur n'atteint le SQL.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class AnalyticsRepository {

    /** Gabarit JDBC pointant sur la base décisionnelle SQL Server. */
    private final JdbcTemplate jdbc;

    /** Table des virements historisés. */
    public static final String FT_TABLE   = "FUNDS_TRANSFER#HIS";

    /** Table du référentiel clients. */
    public static final String CUS_TABLE  = "CUSTOMER";

    /** Table du détail de facturation. */
    public static final String BILL_TABLE = "AA_BILL_DETAILS";

    /**
     * Construit le nom de la table satellite portant un champ multivalué,
     * selon la convention de nommage du Json_Flattener.
     *
     * @param table table principale, par exemple {@code CUSTOMER}
     * @param field champ multivalué, par exemple {@code DATE_AC_OPND}
     * @return le nom du satellite, de la forme {@code TABLE#CHAMP}
     */
    private static String satellite(String table, String field) {
        return table + "#" + field;
    }

    /**
     * Filtre ramenant un satellite à une ligne par enregistrement : seule la
     * première multivaleur et sa première sous-valeur sont conservées, faute
     * de quoi les répartitions compteraient plusieurs fois le même dossier.
     */
    private static final String FIRST_MV =
            "COALESCE(s.MV_SEQUENCE, 1) = 1 AND COALESCE(s.[SEQUENCE], 1) = 1";

    /** Dimensions de segmentation client autorisées (clé API → colonne SQL). */
    public static final Map<String, String> CUSTOMER_DIMENSIONS = Map.of(
            "sector",      "SECTOR",
            "nationality", "NATIONALITY",
            "gender",      "GENDER",
            "target",      "TARGET",
            "status",      "CUSTOMER_STATUS");

    /** Dimensions de segmentation des créances autorisées (clé API → colonne SQL). */
    public static final Map<String, String> BILLING_DIMENSIONS = Map.of(
            "aging",   "AGING_STATUS",
            "settle",  "SETTLE_STATUS",
            "status",  "BILL_STATUS",
            "type",    "BILL_TYPE",
            "method",  "PAYMENT_METHOD");

    /**
     * Expression SQL reconstruisant la date métier d'un virement depuis son
     * identifiant {@code FTyyddd…} : deux chiffres d'année suivis du quantième.
     */
    private static final String FT_DATE =
            "DATEADD(DAY, TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 5, 3)) - 1, " +
                    "DATEFROMPARTS(2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 3, 2)), 1, 1))";

    /**
     * Expression SQL reconstruisant la date métier d'une facture depuis son
     * identifiant {@code AABILLyyddd…}.
     */
    private static final String BILL_DATE =
            "DATEADD(DAY, TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 9, 3)) - 1, " +
                    "DATEFROMPARTS(2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)), 1, 1))";

    /** Filtre écartant les virements dont l'identifiant ne suit pas la forme attendue. */
    private static final String FT_ID_FILTER   = "ORIGINAL_ID LIKE 'FT[0-9][0-9][0-9][0-9][0-9]%'";

    /** Filtre écartant les factures dont l'identifiant ne suit pas la forme attendue. */
    private static final String BILL_ID_FILTER = "ORIGINAL_ID LIKE 'AABILL[0-9][0-9][0-9][0-9][0-9]%'";

    /** Expression SQL extrayant l'année d'un identifiant de virement. */
    private static final String FT_YEAR =
            "2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 3, 2))";

    /**
     * Mémoïsation des sondes de schéma. Les colonnes et satellites n'évoluent
     * qu'au rythme des extractions : une vérification par clé suffit pour la
     * durée de vie de l'application.
     */
    private final Map<String, Boolean> columnCache = new ConcurrentHashMap<>();

    /**
     * Vérifie la présence d'une colonne dans la base décisionnelle.
     *
     * <p>Le résultat est mémorisé : le redémarrage de l'application est
     * nécessaire pour prendre en compte un enrichissement d'extraction.</p>
     *
     * @param table  nom de la table à sonder
     * @param column nom de la colonne recherchée
     * @return {@code true} si la colonne existe
     */
    public boolean hasColumn(String table, String column) {
        return columnCache.computeIfAbsent(table + "." + column, k -> {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            boolean present = n != null && n > 0;
            if (!present) {
                log.debug("Colonne absente (extraction non enrichie) : {}.{}", table, column);
            }
            return present;
        });
    }

    /**
     * Vérifie la présence d'une table, généralement un satellite de champ
     * multivalué.
     *
     * <p>Le résultat est mémorisé selon la même logique que
     * {@link #hasColumn(String, String)}.</p>
     *
     * @param table nom de la table recherchée
     * @return {@code true} si la table existe
     */
    public boolean tableExists(String table) {
        return columnCache.computeIfAbsent("TABLE::" + table, k -> {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                    Integer.class, table);
            boolean present = n != null && n > 0;
            if (!present) {
                log.debug("Table satellite absente (extraction non enrichie) : {}", table);
            }
            return present;
        });
    }

    /**
     * Dresse l'état des colonnes enrichies attendues par la page Flux.
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite ;
     *         l'ordre d'insertion est conservé pour l'affichage
     */
    public Map<String, Boolean> ftEnrichedColumnsStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String col : List.of("AMOUNT_DEBITED", "DEBIT_CURRENCY",
                "TRANSACTION_TYPE", "PROCESSING_DATE")) {
            status.put(col, hasColumn(FT_TABLE, col));
        }
        return status;
    }

    /**
     * Dresse l'état des colonnes enrichies attendues par la page Clients.
     *
     * <p>La date d'entrée en relation étant multivaluée, sa disponibilité se
     * mesure sur l'existence du satellite et non sur celle d'une colonne.</p>
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite
     */
    public Map<String, Boolean> customerEnrichedColumnsStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String col : List.of("SECTOR", "NATIONALITY", "GENDER",
                "TARGET", "CUSTOMER_STATUS")) {
            status.put(col, hasColumn(CUS_TABLE, col));
        }
        // Multivalué → normalisé par le Json_Flattener dans CUSTOMER#DATE_AC_OPND
        status.put("DATE_AC_OPND", tableExists(satellite(CUS_TABLE, "DATE_AC_OPND")));
        return status;
    }

    // ── Clients : socle ───────────────────────────────────────────────────────

    /**
     * Compte les clients du référentiel.
     *
     * @return l'effectif total, ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public long countClients() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM CUSTOMER", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Calcule l'âge moyen des clients.
     *
     * <p>La colonne {@code AGE} est textuelle et peut porter la chaîne
     * littérale {@code 'NULL'} : les valeurs non numériques sont écartées.</p>
     *
     * @return l'âge moyen, ou {@code null} si aucune valeur n'est exploitable
     */
    public Double averageAge() {
        return jdbc.queryForObject(
                "SELECT AVG(TRY_CONVERT(float, NULLIF(AGE, 'NULL'))) FROM CUSTOMER",
                Double.class);
    }

    /**
     * Compte les gestionnaires de portefeuille distincts.
     *
     * @return le nombre de gestionnaires, ou {@code 0} si l'agrégat n'a pu
     *         être calculé
     */
    public long countOfficers() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT ACCOUNT_OFFICER) FROM CUSTOMER " +
                        "WHERE ACCOUNT_OFFICER IS NOT NULL AND ACCOUNT_OFFICER <> 'NULL'",
                Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Classe les gestionnaires par taille de portefeuille.
     *
     * @param top nombre de gestionnaires à ramener
     * @return des lignes portant les clés {@code officer}, {@code nb},
     *         {@code age_moyen} et {@code part_pct}, du plus gros portefeuille
     *         au plus petit
     */
    public List<Map<String, Object>> portfolioByOfficer(int top) {
        return jdbc.queryForList("""
                SELECT TOP (?) ACCOUNT_OFFICER AS officer,
                       COUNT(*) AS nb,
                       AVG(TRY_CONVERT(float, NULLIF(AGE, 'NULL'))) AS age_moyen,
                       CAST(100.0 * COUNT(*) / SUM(COUNT(*)) OVER () AS float) AS part_pct
                FROM CUSTOMER
                WHERE ACCOUNT_OFFICER IS NOT NULL AND ACCOUNT_OFFICER <> 'NULL'
                GROUP BY ACCOUNT_OFFICER
                ORDER BY nb DESC
                """, top);
    }

    /**
     * Répartit les clients en six tranches d'âge, pour la page Synthèse.
     *
     * @return des lignes portant les clés {@code tranche}, {@code ordre} et
     *         {@code nb}, triées de la tranche la plus jeune à la plus âgée
     */
    public List<Map<String, Object>> agePyramid() {
        return jdbc.queryForList("""
                SELECT tranche, ordre, COUNT(*) AS nb
                FROM (
                    SELECT CASE
                             WHEN a < 25              THEN N'< 25'
                             WHEN a BETWEEN 25 AND 34 THEN N'25–34'
                             WHEN a BETWEEN 35 AND 44 THEN N'35–44'
                             WHEN a BETWEEN 45 AND 54 THEN N'45–54'
                             WHEN a BETWEEN 55 AND 64 THEN N'55–64'
                             ELSE N'65 +'
                           END AS tranche,
                           CASE
                             WHEN a < 25              THEN 1
                             WHEN a BETWEEN 25 AND 34 THEN 2
                             WHEN a BETWEEN 35 AND 44 THEN 3
                             WHEN a BETWEEN 45 AND 54 THEN 4
                             WHEN a BETWEEN 55 AND 64 THEN 5
                             ELSE 6
                           END AS ordre
                    FROM (SELECT TRY_CONVERT(int, NULLIF(AGE, 'NULL')) AS a FROM CUSTOMER) x
                    WHERE a IS NOT NULL
                ) t
                GROUP BY tranche, ordre
                ORDER BY ordre
                """);
    }

    // ── Clients : page dédiée ─────────────────────────────────────────────────

    /**
     * Répartit les clients par tranche d'âge de cinq ans.
     *
     * <p>Les 85 ans et plus sont regroupés dans une tranche unique, les
     * effectifs y devenant trop faibles pour être lisibles.</p>
     *
     * @return des lignes portant les clés {@code band} (borne basse de la
     *         tranche) et {@code nb}, triées par âge croissant
     */
    public List<Map<String, Object>> ageHistogram() {
        return jdbc.queryForList("""
                SELECT band, COUNT(*) AS nb
                FROM (
                    SELECT CASE WHEN a >= 85 THEN 85 ELSE (a / 5) * 5 END AS band
                    FROM (SELECT TRY_CONVERT(int, NULLIF(AGE, 'NULL')) AS a FROM CUSTOMER) x
                    WHERE a IS NOT NULL AND a >= 0
                ) t
                GROUP BY band
                ORDER BY band
                """);
    }

    /**
     * Répartit les clients par génération.
     *
     * <p>La génération est dérivée de l'année de naissance lue dans
     * {@code BIRTH_INCORP_DATE}. Les années hors de la plage 1900–2026 sont
     * écartées : elles trahissent une donnée de mauvaise qualité.</p>
     *
     * @return des lignes portant les clés {@code label}, {@code ordre} et
     *         {@code nb}, de la génération la plus jeune à la plus ancienne
     */
    public List<Map<String, Object>> generations() {
        return jdbc.queryForList("""
                SELECT gen AS label, ordre, COUNT(*) AS nb
                FROM (
                    SELECT CASE
                             WHEN y >= 1997 THEN N'Gén. Z (1997+)'
                             WHEN y >= 1981 THEN N'Millennials (1981–96)'
                             WHEN y >= 1965 THEN N'Gén. X (1965–80)'
                             WHEN y >= 1946 THEN N'Boomers (1946–64)'
                             ELSE               N'Avant 1946'
                           END AS gen,
                           CASE
                             WHEN y >= 1997 THEN 1
                             WHEN y >= 1981 THEN 2
                             WHEN y >= 1965 THEN 3
                             WHEN y >= 1946 THEN 4
                             ELSE 5
                           END AS ordre
                    FROM (SELECT TRY_CONVERT(int, LEFT(NULLIF(BIRTH_INCORP_DATE, 'NULL'), 4)) AS y
                          FROM CUSTOMER) x
                    WHERE y BETWEEN 1900 AND 2026
                ) t
                GROUP BY gen, ordre
                ORDER BY ordre
                """);
    }

    /**
     * Calcule la part des clients dont l'âge tombe dans un intervalle.
     *
     * @param from borne basse, incluse
     * @param to   borne haute, incluse
     * @return la part en pourcentage, ou {@code null} si aucun âge n'est
     *         exploitable
     */
    public Double ageShare(int from, int to) {
        return jdbc.queryForObject("""
                SELECT 100.0 * SUM(CASE WHEN a BETWEEN ? AND ? THEN 1 ELSE 0 END) / COUNT(*)
                FROM (SELECT TRY_CONVERT(int, NULLIF(AGE, 'NULL')) AS a FROM CUSTOMER) x
                WHERE a IS NOT NULL
                """, Double.class, from, to);
    }

    /**
     * Calcule la part des dossiers issus de la migration initiale.
     *
     * <p>Ces dossiers se reconnaissent au marqueur laissé dans le champ
     * {@code AUTHORISER} lors de la reprise de données.</p>
     *
     * @return la part en pourcentage, ou {@code null} si le champ n'est pas
     *         renseigné
     */
    public Double migrationShare() {
        return jdbc.queryForObject("""
                SELECT 100.0 * SUM(CASE WHEN AUTHORISER LIKE '%MIGRATION%' THEN 1 ELSE 0 END)
                       / COUNT(*)
                FROM CUSTOMER
                WHERE AUTHORISER IS NOT NULL AND AUTHORISER <> 'NULL'
                """, Double.class);
    }

    /**
     * Date d'entrée en relation par client : première valeur (MIN) du
     * satellite CUSTOMER#DATE_AC_OPND — un client peut porter plusieurs
     * dates d'ouverture de compte, la plus ancienne fait foi.
     */
    private static final String ACQ_FIRST_DATE = """
            SELECT s._id,
                   MIN(TRY_CONVERT(date, NULLIF(s.DATE_AC_OPND, 'NULL'), 112)) AS d
            FROM [CUSTOMER#DATE_AC_OPND] s
            GROUP BY s._id
            """;

    /**
     * Liste les années d'entrée en relation présentes dans les données.
     *
     * @return les années par ordre décroissant ; liste vide tant que le
     *         satellite {@code CUSTOMER#DATE_AC_OPND} n'est pas alimenté
     */
    public List<Integer> acquisitionYears() {
        if (!tableExists(satellite(CUS_TABLE, "DATE_AC_OPND"))) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT YEAR(d) AS y
                FROM (%s) t
                WHERE d IS NOT NULL
                ORDER BY y DESC
                """.formatted(ACQ_FIRST_DATE), Integer.class);
    }

    /**
     * Compte les entrées en relation mois par mois.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois} et {@code nb}, triées
     *         par mois ; liste vide tant que le satellite n'est pas alimenté
     */
    public List<Map<String, Object>> newCustomersMonthly(int year) {
        if (!tableExists(satellite(CUS_TABLE, "DATE_AC_OPND"))) return List.of();
        return jdbc.queryForList("""
                SELECT MONTH(d) AS mois, COUNT(*) AS nb
                FROM (%s) t
                WHERE d IS NOT NULL AND (? = 0 OR YEAR(d) = ?)
                GROUP BY MONTH(d)
                ORDER BY mois
                """.formatted(ACQ_FIRST_DATE), year, year);
    }

    /**
     * Compte les entrées en relation sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return l'effectif, ou {@code null} tant que le satellite n'est pas
     *         alimenté — le {@code null} distingue « donnée indisponible » de
     *         « aucune entrée en relation »
     */
    public Long countNewCustomers(int year) {
        if (!tableExists(satellite(CUS_TABLE, "DATE_AC_OPND"))) return null;
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (%s) t
                WHERE d IS NOT NULL AND (? = 0 OR YEAR(d) = ?)
                """.formatted(ACQ_FIRST_DATE), Long.class, year, year);
    }

    /**
     * Segmente les clients selon une dimension autorisée.
     *
     * <p>Les valeurs vides ou littéralement égales à {@code 'NULL'} sont
     * regroupées sous le libellé {@code —}.</p>
     *
     * @param dimensionKey clé de dimension, parmi {@link #CUSTOMER_DIMENSIONS}
     * @param top          nombre de valeurs à ramener
     * @return des lignes portant les clés {@code label} et {@code nb}, de la
     *         valeur la plus fréquente à la moins fréquente ; liste vide si la
     *         dimension est inconnue ou sa colonne non extraite
     */
    public List<Map<String, Object>> customerBreakdown(String dimensionKey, int top) {
        String column = CUSTOMER_DIMENSIONS.get(dimensionKey);
        if (column == null || !hasColumn(CUS_TABLE, column)) return List.of();
        return jdbc.queryForList("""
                SELECT TOP (?) COALESCE(NULLIF(NULLIF(%s, ''), 'NULL'), '—') AS label,
                       COUNT(*) AS nb
                FROM CUSTOMER
                GROUP BY COALESCE(NULLIF(NULLIF(%s, ''), 'NULL'), '—')
                ORDER BY nb DESC
                """.formatted(column, column), top);
    }

    // ── Virements : volumes ───────────────────────────────────────────────────

    /**
     * Liste les années présentes dans l'historique des virements.
     *
     * @return les années par ordre décroissant, déduites des identifiants T24
     */
    public List<Integer> transferYears() {
        return jdbc.queryForList(
                "SELECT DISTINCT " + FT_YEAR + " AS y FROM [" + FT_TABLE + "] " +
                        "WHERE " + FT_ID_FILTER + " ORDER BY y DESC",
                Integer.class);
    }

    /**
     * Compte les virements mois par mois.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois} et {@code nb}, triées
     *         par mois ; les mois sans opération sont absents
     */
    public List<Map<String, Object>> monthlyTransfers(int year) {
        return jdbc.queryForList("""
                SELECT MONTH(%s) AS mois, COUNT(*) AS nb
                FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                GROUP BY MONTH(%s)
                ORDER BY mois
                """.formatted(FT_DATE, FT_TABLE, FT_ID_FILTER, FT_YEAR, FT_DATE),
                year, year);
    }

    /**
     * Compte les virements sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return le nombre d'opérations, ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public long countTransfers(int year) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                """.formatted(FT_TABLE, FT_ID_FILTER, FT_YEAR),
                Long.class, year, year);
        return n == null ? 0 : n;
    }

    // ── Virements : montants (page Flux) ──────────────────────────────────────

    /**
     * Totalise les montants débités sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return le montant total, ou {@code null} tant que {@code AMOUNT_DEBITED}
     *         n'est pas extraite
     */
    public Double sumTransfers(int year) {
        if (!hasColumn(FT_TABLE, "AMOUNT_DEBITED")) return null;
        return jdbc.queryForObject("""
                SELECT SUM(TRY_CONVERT(decimal(18,3), AMOUNT_DEBITED)) FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                """.formatted(FT_TABLE, FT_ID_FILTER, FT_YEAR),
                Double.class, year, year);
    }

    /**
     * Totalise les montants débités mois par mois.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois} et {@code montant} ;
     *         liste vide tant que {@code AMOUNT_DEBITED} n'est pas extraite
     */
    public List<Map<String, Object>> monthlyAmounts(int year) {
        if (!hasColumn(FT_TABLE, "AMOUNT_DEBITED")) return List.of();
        return jdbc.queryForList("""
                SELECT MONTH(%s) AS mois,
                       SUM(TRY_CONVERT(decimal(18,3), AMOUNT_DEBITED)) AS montant
                FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                GROUP BY MONTH(%s)
                ORDER BY mois
                """.formatted(FT_DATE, FT_TABLE, FT_ID_FILTER, FT_YEAR, FT_DATE),
                year, year);
    }

    /**
     * Répartit les virements par devise de débit.
     *
     * <p>La colonne de montant n'est agrégée que si elle est extraite ; sinon
     * la requête projette une valeur nulle, ce qui préserve la forme du
     * résultat. Les devises absentes sont ramenées à {@code TND}, devise de
     * référence de l'établissement.</p>
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code label}, {@code nb} et
     *         {@code montant} ; liste vide tant que {@code DEBIT_CURRENCY}
     *         n'est pas extraite
     */
    public List<Map<String, Object>> flowsByCurrency(int year) {
        if (!hasColumn(FT_TABLE, "DEBIT_CURRENCY")) return List.of();
        String amount = hasColumn(FT_TABLE, "AMOUNT_DEBITED")
                ? "SUM(TRY_CONVERT(decimal(18,3), AMOUNT_DEBITED))"
                : "CAST(NULL AS decimal(18,3))";
        return jdbc.queryForList("""
                SELECT COALESCE(NULLIF(NULLIF(DEBIT_CURRENCY, ''), 'NULL'), 'TND') AS label,
                       COUNT(*) AS nb, %s AS montant
                FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                GROUP BY COALESCE(NULLIF(NULLIF(DEBIT_CURRENCY, ''), 'NULL'), 'TND')
                ORDER BY nb DESC
                """.formatted(amount, FT_TABLE, FT_ID_FILTER, FT_YEAR),
                year, year);
    }

    /**
     * Répartit les virements par type d'opération.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code label}, {@code nb} et
     *         {@code montant} ; liste vide tant que {@code TRANSACTION_TYPE}
     *         n'est pas extraite
     */
    public List<Map<String, Object>> flowsByType(int year) {
        if (!hasColumn(FT_TABLE, "TRANSACTION_TYPE")) return List.of();
        String amount = hasColumn(FT_TABLE, "AMOUNT_DEBITED")
                ? "SUM(TRY_CONVERT(decimal(18,3), AMOUNT_DEBITED))"
                : "CAST(NULL AS decimal(18,3))";
        return jdbc.queryForList("""
                SELECT COALESCE(NULLIF(NULLIF(TRANSACTION_TYPE, ''), 'NULL'), '—') AS label,
                       COUNT(*) AS nb, %s AS montant
                FROM [%s]
                WHERE %s AND (? = 0 OR %s = ?)
                GROUP BY COALESCE(NULLIF(NULLIF(TRANSACTION_TYPE, ''), 'NULL'), '—')
                ORDER BY nb DESC
                """.formatted(amount, FT_TABLE, FT_ID_FILTER, FT_YEAR),
                year, year);
    }

    /**
     * Croise l'activité par mois et par jour de semaine.
     *
     * <p>Ne dépend d'aucune colonne enrichie : la date est reconstruite depuis
     * l'identifiant T24. Le jour de semaine est calculé par différence avec le
     * 1<sup>er</sup> janvier 1900, qui était un lundi ; le résultat est donc
     * indépendant du réglage {@code DATEFIRST} de la session SQL Server.</p>
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois}, {@code jour}
     *         ({@code 0} pour lundi jusqu'à {@code 6} pour dimanche) et {@code nb}
     */
    public List<Map<String, Object>> activityHeatmap(int year) {
        return jdbc.queryForList("""
                SELECT MONTH(d) AS mois,
                       (DATEDIFF(DAY, 0, d) %% 7) AS jour,
                       COUNT(*) AS nb
                FROM (
                    SELECT %s AS d FROM [%s] WHERE %s
                ) src
                WHERE d IS NOT NULL AND (? = 0 OR YEAR(d) = ?)
                GROUP BY MONTH(d), (DATEDIFF(DAY, 0, d) %% 7)
                """.formatted(FT_DATE, FT_TABLE, FT_ID_FILTER),
                year, year);
    }

    // ── Facturation (AA_BILL_DETAILS) ─────────────────────────────────────────

    /**
     * Dresse l'état des colonnes enrichies attendues par la page Créances.
     *
     * <p>Les champs de statut sont multivalués et donc portés par des
     * satellites, tandis que les montants et dates sont des colonnes directes
     * de la table principale : la sonde diffère selon le cas.</p>
     *
     * @return une entrée par champ attendu, {@code true} s'il est extrait
     */
    public Map<String, Boolean> billingEnrichedColumnsStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        // Multivalués → normalisés par le Json_Flattener dans AA_BILL_DETAILS#CHAMP
        for (String field : List.of("SETTLE_STATUS", "AGING_STATUS", "BILL_TYPE")) {
            status.put(field, tableExists(satellite(BILL_TABLE, field)));
        }
        // Monovalués → colonnes directes de la table principale
        for (String col : List.of("OR_TOTAL_AMOUNT", "OS_TOTAL_AMOUNT",
                "PAYMENT_DATE", "ACTUAL_PAY_DATE")) {
            status.put(col, hasColumn(BILL_TABLE, col));
        }
        return status;
    }

    /**
     * Liste les années présentes dans l'historique de facturation.
     *
     * @return les années par ordre décroissant, déduites des identifiants T24
     */
    public List<Integer> billYears() {
        return jdbc.queryForList(
                "SELECT DISTINCT 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) AS y " +
                        "FROM AA_BILL_DETAILS WHERE " + BILL_ID_FILTER + " ORDER BY y DESC",
                Integer.class);
    }

    /**
     * Compte les factures émises sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return le nombre de factures, ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public long countBills(int year) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM AA_BILL_DETAILS
                WHERE %s AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) = ?)
                """.formatted(BILL_ID_FILTER),
                Long.class, year, year);
        return n == null ? 0 : n;
    }

    /**
     * Compte les factures émises mois par mois.
     *
     * <p>Ne dépend d'aucune colonne enrichie : la date d'émission est
     * reconstruite depuis l'identifiant {@code AABILLyyddd…}.</p>
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois} et {@code nb}, triées
     *         par mois
     */
    public List<Map<String, Object>> monthlyBills(int year) {
        return jdbc.queryForList("""
                SELECT MONTH(%s) AS mois, COUNT(*) AS nb
                FROM AA_BILL_DETAILS
                WHERE %s AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) = ?)
                GROUP BY MONTH(%s)
                ORDER BY mois
                """.formatted(BILL_DATE, BILL_ID_FILTER, BILL_DATE),
                year, year);
    }

    /**
     * Totalise les montants facturés mois par mois.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return des lignes portant les clés {@code mois} et {@code montant} ;
     *         liste vide tant que {@code OR_TOTAL_AMOUNT} n'est pas extraite
     */
    public List<Map<String, Object>> monthlyBillAmounts(int year) {
        if (!hasColumn(BILL_TABLE, "OR_TOTAL_AMOUNT")) return List.of();
        return jdbc.queryForList("""
                SELECT MONTH(%s) AS mois,
                       SUM(TRY_CONVERT(decimal(18,3), OR_TOTAL_AMOUNT)) AS montant
                FROM AA_BILL_DETAILS
                WHERE %s AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) = ?)
                GROUP BY MONTH(%s)
                ORDER BY mois
                """.formatted(BILL_DATE, BILL_ID_FILTER, BILL_DATE),
                year, year);
    }

    /**
     * Totalise les montants facturés sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return le montant facturé, ou {@code null} tant que
     *         {@code OR_TOTAL_AMOUNT} n'est pas extraite
     */
    public Double billedAmount(int year) {
        return sumBillColumn("OR_TOTAL_AMOUNT", year);
    }

    /**
     * Totalise les encours restant dus sur une année.
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return l'encours total, ou {@code null} tant que
     *         {@code OS_TOTAL_AMOUNT} n'est pas extraite
     */
    public Double outstandingAmount(int year) {
        return sumBillColumn("OS_TOTAL_AMOUNT", year);
    }

    /**
     * Totalise une colonne monétaire de la table de facturation.
     *
     * <p>Le nom de colonne est interpolé dans le SQL car un paramètre lié ne
     * peut pas désigner une colonne. Les seules valeurs possibles sont les
     * constantes passées par {@link #billedAmount(int)} et
     * {@link #outstandingAmount(int)} : aucune saisie utilisateur n'y accède.</p>
     *
     * @param column nom de la colonne à sommer
     * @param year   année à filtrer ; {@code 0} pour tout l'historique
     * @return la somme, ou {@code null} si la colonne n'est pas extraite
     */
    private Double sumBillColumn(String column, int year) {
        if (!hasColumn(BILL_TABLE, column)) return null;
        return jdbc.queryForObject("""
                SELECT SUM(TRY_CONVERT(decimal(18,3), %s)) FROM AA_BILL_DETAILS
                WHERE %s AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) = ?)
                """.formatted(column, BILL_ID_FILTER),
                Double.class, year, year);
    }

    /**
     * Segmente les créances selon une dimension autorisée.
     *
     * <p>Toutes ces dimensions sont des champs multivalués : la valeur est
     * lue dans le satellite {@code AA_BILL_DETAILS#CHAMP}, restreint à la
     * première multivaleur, puis joint à la table principale pour appliquer le
     * filtre d'année et agréger l'encours.</p>
     *
     * <p>Le {@code DISTINCT} sur le satellite est indispensable : le
     * Json_Flattener peut réinsérer les mêmes lignes à chaque passage, ce qui
     * gonflerait les effectifs.</p>
     *
     * @param dimensionKey clé de dimension, parmi {@link #BILLING_DIMENSIONS}
     * @param year         année à filtrer ; {@code 0} pour tout l'historique
     * @param top          nombre de valeurs à ramener
     * @return des lignes portant les clés {@code label}, {@code nb} et
     *         {@code montant} ; liste vide si la dimension est inconnue ou son
     *         satellite absent
     */
    public List<Map<String, Object>> billingBreakdown(String dimensionKey, int year, int top) {
        String field = BILLING_DIMENSIONS.get(dimensionKey);
        if (field == null || !tableExists(satellite(BILL_TABLE, field))) return List.of();
        String amount = hasColumn(BILL_TABLE, "OS_TOTAL_AMOUNT")
                ? "SUM(TRY_CONVERT(decimal(18,3), b.OS_TOTAL_AMOUNT))"
                : "CAST(NULL AS decimal(18,3))";
        return jdbc.queryForList("""
                SELECT TOP (?) COALESCE(NULLIF(NULLIF(s.val, ''), 'NULL'), '—') AS label,
                       COUNT(*) AS nb, %s AS montant
                FROM (
                    -- DISTINCT : le Json_Flattener peut réinsérer les mêmes lignes
                    -- à chaque run ; une facture = une valeur, jamais plus
                    SELECT DISTINCT s._id, s.[%s] AS val
                    FROM [%s] s
                    WHERE %s
                ) s
                JOIN [%s] b ON b._id = s._id
                WHERE b.%s
                  AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(b.ORIGINAL_ID, 7, 2)) = ?)
                GROUP BY COALESCE(NULLIF(NULLIF(s.val, ''), 'NULL'), '—')
                ORDER BY nb DESC
                """.formatted(amount, field, satellite(BILL_TABLE, field),
                        FIRST_MV, BILL_TABLE, BILL_ID_FILTER),
                top, year, year);
    }

    /**
     * Calcule le délai moyen entre l'échéance et le paiement effectif.
     *
     * <p>Seules les factures dont les deux dates sont exploitables entrent
     * dans la moyenne.</p>
     *
     * @param year année à filtrer ; {@code 0} pour tout l'historique
     * @return le délai moyen en jours — positif si le règlement intervient
     *         après l'échéance, négatif s'il est anticipé — ou {@code null}
     *         tant que {@code PAYMENT_DATE} et {@code ACTUAL_PAY_DATE} ne sont
     *         pas toutes deux extraites
     */
    public Double avgSettlementDelay(int year) {
        if (!hasColumn(BILL_TABLE, "PAYMENT_DATE")
                || !hasColumn(BILL_TABLE, "ACTUAL_PAY_DATE")) return null;
        return jdbc.queryForObject("""
                SELECT AVG(CAST(DATEDIFF(DAY, echeance, paiement) AS float))
                FROM (
                    SELECT TRY_CONVERT(date, NULLIF(PAYMENT_DATE, 'NULL'), 112)    AS echeance,
                           TRY_CONVERT(date, NULLIF(ACTUAL_PAY_DATE, 'NULL'), 112) AS paiement
                    FROM AA_BILL_DETAILS
                    WHERE %s AND (? = 0 OR 2000 + TRY_CONVERT(int, SUBSTRING(ORIGINAL_ID, 7, 2)) = ?)
                ) t
                WHERE echeance IS NOT NULL AND paiement IS NOT NULL
                """.formatted(BILL_ID_FILTER),
                Double.class, year, year);
    }

    /**
     * Répartit les créances par état de vieillissement.
     *
     * <p>Conservée pour la page Synthèse, qui n'applique pas de filtre
     * d'année, contrairement à
     * {@link #billingBreakdown(String, int, int)}.</p>
     *
     * @return des lignes portant les clés {@code label} et {@code nb}, de la
     *         valeur la plus fréquente à la moins fréquente ; liste vide tant
     *         que le satellite {@code AGING_STATUS} n'est pas alimenté
     */
    public List<Map<String, Object>> billingAging() {
        if (!tableExists(satellite(BILL_TABLE, "AGING_STATUS"))) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT s.AGING_STATUS AS label, COUNT(DISTINCT s._id) AS nb
                FROM [%s] s
                WHERE %s
                  AND s.AGING_STATUS IS NOT NULL AND s.AGING_STATUS <> 'NULL'
                GROUP BY s.AGING_STATUS
                ORDER BY nb DESC
                """.formatted(satellite(BILL_TABLE, "AGING_STATUS"), FIRST_MV));
    }
}
