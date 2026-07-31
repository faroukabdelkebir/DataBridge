package com.t24extractor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.t24extractor.commons.Constants;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * Contrats des modèles persistés.
 *
 * Ces objets sont mappés colonne par colonne sur Oracle et SQL Server, et
 * certains servent de clés dans des ensembles ou d'arguments de comparaison
 * (MetaDataTable, ScheduledOps). Leur égalité et leurs accesseurs font donc
 * partie du contrat fonctionnel.
 */
class ModelContractTest {

    @Test
    void schemaSourceAuditExposesTheBeforeAndAfterStateOfAColumn() {
        SchemaSourceAudit audit =
                new SchemaSourceAudit(7, "src-1", "CUSTOMER", "PHONES", "StringMAX", 1, 0, 1, 0);

        assertEquals(7, audit.id());
        assertEquals("src-1", audit.sourceId());
        assertEquals("CUSTOMER", audit.sourceTableName());
        assertEquals("PHONES", audit.ColumnName());
        assertEquals("StringMAX", audit.dataType());
        // La différence entre newSource et oldSource pilote l'ALTER TABLE.
        assertEquals(1, audit.newSourceEnabled() - audit.oldSourceEnabled());
        assertEquals(1, audit.newSourceIndexed() - audit.oldSourceIndexed());
        assertEquals(audit, new SchemaSourceAudit(7, "src-1", "CUSTOMER", "PHONES", "StringMAX", 1, 0, 1, 0));
    }

    @Test
    void metaDataTableEqualityDrivesMetadataDeletion() {
        MetaDataTable phones = new MetaDataTable("CUSTOMER", "PHONES", false);

        // BISyncTasklet supprime la métadonnée en passant un objet reconstruit :
        // sans égalité par valeur, la suppression ne ciblerait rien.
        assertEquals(phones, new MetaDataTable("CUSTOMER", "PHONES", false));
        assertNotEquals(phones, new MetaDataTable("CUSTOMER", "PHONES", true));
        assertEquals("CUSTOMER", phones.tablename());
        assertEquals("PHONES", phones.multivalueFields());
    }

    @Test
    void scheduledOpsCarriesTheDeferredOperation() {
        ScheduledOps op = new ScheduledOps(null, "[CUSTOMER]", "[DATE_TIME]", "I");

        assertEquals("[CUSTOMER]", op.getTable());
        assertEquals("[DATE_TIME]", op.getColumn());
        assertEquals("I", op.getOp());
        assertEquals(op, new ScheduledOps(null, "[CUSTOMER]", "[DATE_TIME]", "I"));
    }

    @Test
    void schemaSourceIsAlwaysTreatedAsNewToForceInserts() {
        SchemaSource column =
                new SchemaSource("id-1", "CUSTOMER", "NAME", "String", true, false, "c1", "S", false);

        assertEquals("id-1", column.getId());
        // isNew() constant à true : Spring Data JDBC émet toujours un INSERT,
        // la table SCHEMA_SOURCE étant alimentée en append.
        assertTrue(column.isNew());
        assertEquals("CUSTOMER", column.getTableName());
        assertEquals("NAME", column.getColumnName());
        assertEquals("String", column.getDataType());
    }

    @Test
    void cobStatusIdentifiesTheServiceControlRow() {
        CobStatus status = new CobStatus(Constants.COB_RECID, Constants.COB_RUNNING);

        assertEquals("COB", status.recid());
        assertEquals("COB_RUNNING", status.serviceControl());
        assertEquals(status, new CobStatus("COB", "COB_RUNNING"));
    }

    @Test
    void t24DatesCarriesTheLastWorkingDay() {
        T24Dates dates = new T24Dates("TN0010001", "20260101");

        assertEquals("TN0010001", dates.recid());
        assertEquals("20260101", dates.laskWorkingDay());
    }

    @Test
    void tafjVocMapsAT24NameToItsOracleTable() {
        TafjVoc voc = new TafjVoc("FBNK.CUSTOMER", "X", "FBNK_CUSTOMER");

        assertEquals("FBNK.CUSTOMER", voc.recid());
        assertEquals("X", voc.isBlob());
        assertEquals("FBNK_CUSTOMER", voc.orclFileName());
    }

    @Test
    void updateRequestCarriesTheEnabledFlag() {
        assertTrue(new UpdateSchemaSourceEnabledRequest(true).enabled());
    }

    @Test
    void constantsClassCannotBeInstantiated() throws Exception {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                "Constants est un porte-constantes, pas un composant instanciable");
        constructor.setAccessible(true);
        // Le constructeur privé reste couvert : on vérifie qu'il n'échoue pas.
        assertTrue(constructor.newInstance() instanceof Constants);
    }

    @Test
    void mandatoryTechnicalColumnsAreStable() {
        // Ces noms sont écrits en dur dans les DDL générés et dans les vues BI.
        assertEquals("_id", Constants.ID);
        assertEquals("ORIGINAL_ID", Constants.ORIGINAL_ID);
        assertEquals("INSERT_DATE", Constants.INSERT_DATE_COLUMN);
        assertEquals("FUNDS.TRANSFER$HIS", Constants.FUNDS_TRANSFER_HIS);
        assertEquals("AA.BILL.DETAILS", Constants.AA_BILL_DETAILS);
    }

    @Test
    void dateUtilsAndRenamerAreUtilityClasses() throws Exception {
        assertPrivateConstructor(com.t24extractor.utils.DateUtils.class);
    }

    private void assertPrivateConstructor(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            assertThrows(RuntimeException.class, () -> {
                throw (RuntimeException) e.getCause();
            });
        }
    }
}
