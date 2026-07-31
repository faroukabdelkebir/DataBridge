package com.t24extractor.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.model.StandardSelectionRecord;
import com.t24extractor.model.TafjVoc;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Interprétation du STANDARD.SELECTION T24 : typage des champs système et
 * champs locaux (LOCAL.REF).
 *
 * Le typage détermine la colonne SQL Server créée et la forme du JSON produit.
 * Un montant traité comme texte, ou un champ local multivalué traité comme
 * simple, corrompt les données en aval sans erreur visible.
 */
class StandardSelectionLocalRefTest {

    /**
     * c1 = nom, c2 = D (donnée), c3 = numéro de champ, c4 = type T24,
     * c6 = longueur, c10 = S/M (simple ou multivalué).
     * c15/c17/c18/c24 décrivent les champs LOCAL.REF.
     */
    private static final String RICH_XML = """
            <row>
              <c1 m="1">NAME</c1>
              <c2 m="1">D</c2>
              <c3 m="1">1</c3>
              <c4 m="1">A</c4>
              <c6 m="1">35</c6>
              <c10 m="1">S</c10>

              <c1 m="2">AMOUNT</c1>
              <c2 m="2">D</c2>
              <c3 m="2">2</c3>
              <c4 m="2">IN2AMT</c4>
              <c6 m="2">19</c6>
              <c10 m="2">S</c10>

              <c1 m="3">OPEN.DATE</c1>
              <c2 m="3">D</c2>
              <c3 m="3">3</c3>
              <c4 m="3">IN2.D</c4>
              <c6 m="3">8</c6>
              <c10 m="3">S</c10>

              <c1 m="4">PHONES</c1>
              <c2 m="4">D</c2>
              <c3 m="4">4</c3>
              <c4 m="4">A</c4>
              <c6 m="4">MAX</c6>
              <c10 m="4">M</c10>

              <c1 m="5">LOCAL.REF</c1>
              <c2 m="5">D</c2>
              <c3 m="5">5</c3>
              <c4 m="5">A</c4>
              <c6 m="5">MAX</c6>
              <c10 m="5">S</c10>

              <c15 m="10">MY.LOCAL.FIELD</c15>
              <c17 m="10">1,1&gt;2</c17>
              <c18 m="10">IN2AMT&amp;19</c18>
              <c24 m="10">S</c24>

              <c15 m="11">MV.LOCAL</c15>
              <c17 m="11">1,2&gt;3</c17>
              <c18 m="11">A</c18>
              <c24 m="11">M</c24>
            </row>
            """;

    private final JdbcTemplate jdbcBi = mock(JdbcTemplate.class);
    private final TafjVocRepository tafjVocRepository = mock(TafjVocRepository.class);
    private final StandardSelectionRepository standardSelectionRepository = mock(StandardSelectionRepository.class);

    @AfterEach
    void clearSharedState() {
        StandardSelectionTasklet.getBlobViewFields().clear();
    }

    @Test
    void mapsT24TypesToTheMatchingOdsTypes() throws Exception {
        JSONObject sysFieldOds = new JSONObject(runAndGet("X", "sysFieldOds"));

        assertEquals("String35.c1", sysFieldOds.getString("NAME"), "champ texte : longueur conservée");
        assertEquals("Float.c2", sysFieldOds.getString("AMOUNT"), "IN2AMT est un montant décimal");
        assertEquals("Integer.c3", sysFieldOds.getString("OPEN_DATE"), "IN2.D est une date numérique T24");
        assertEquals("StringMAX.c4", sysFieldOds.getString("PHONES"), "c10=M impose un champ multivalué");
    }

    @Test
    void replacesDotsByUnderscoresInColumnNames() throws Exception {
        JSONObject sysFieldOds = new JSONObject(runAndGet("X", "sysFieldOds"));

        // SQL Server n'accepte pas le point dans un nom de colonne non échappé.
        assertTrue(sysFieldOds.has("OPEN_DATE"));
        assertFalse(sysFieldOds.has("OPEN.DATE"));
    }

    @Test
    void distinguishesSingleAndMultiValueLocalRefFields() throws Exception {
        JSONObject sysFieldOds = new JSONObject(runAndGet("X", "sysFieldOds"));

        assertEquals("Local_Ref_String.m1", sysFieldOds.getString("MY_LOCAL_FIELD"));
        // c24=M : le champ local est multivalué et sera normalisé en table satellite.
        assertEquals("Local_Ref_StringMAX.m2", sysFieldOds.getString("MV_LOCAL"));
    }

    @Test
    void exposesLocalRefPositionAndTypesInTheDictionary() throws Exception {
        JSONObject dictionary = new JSONObject(runAndGet("X", "Mongo_Standard_Selection"));

        // localRefPos localise le champ LOCAL.REF parmi les champs système :
        // le processeur XML s'en sert pour basculer sur le décodage des champs locaux.
        assertEquals("c5", dictionary.getJSONArray("localRefPos").get(0));

        JSONObject usrField = dictionary.getJSONArray("usrField").getJSONObject(0);
        assertEquals("MY_LOCAL_FIELD", usrField.getString("m1"));
        assertEquals("MV_LOCAL", usrField.getString("m2"));

        JSONObject usrFieldType = dictionary.getJSONArray("UsrFieldType").getJSONObject(0);
        assertEquals("S", usrFieldType.getString("m1"));
        assertEquals("M", usrFieldType.getString("m2"));

        JSONObject usrFieldDataType = dictionary.getJSONArray("UsrFieldDataType").getJSONObject(0);
        assertEquals("Float", usrFieldDataType.getString("m1"), "IN2AMT& est un montant");
        assertEquals("String", usrFieldDataType.getString("m2"));
    }

    @Test
    void blobViewQuerySelectsBusinessFieldsAndLocalRefsButNotLocalRefItself() throws Exception {
        String viewQuery = runAndGet("B", "VIEW_QUERY");

        assertTrue(viewQuery.startsWith("SELECT RECID as FIELD_ID, "), viewQuery);
        assertTrue(viewQuery.endsWith(", ROWNUM rowNumber FROM V_FBNK_CUSTOMER t"), viewQuery);
        for (String field : new String[] {"NAME", "AMOUNT", "OPEN_DATE", "PHONES"}) {
            assertTrue(viewQuery.contains(field), field + " absent de " + viewQuery);
        }
        // Les champs locaux sont lus depuis l'alias t de la vue.
        assertTrue(viewQuery.contains("t.MY_LOCAL_FIELD"), viewQuery);
        assertTrue(viewQuery.contains("t.MV_LOCAL"), viewQuery);
        // LOCAL_REF est un conteneur, pas une colonne : il ne doit pas être sélectionné.
        assertFalse(viewQuery.contains("LOCAL_REF,"), viewQuery);
    }

    @Test
    void blobViewFieldsAreExposedForDownstreamProcessing() throws Exception {
        runAndGet("B", "VIEW_QUERY");

        assertTrue(StandardSelectionTasklet.getBlobViewFields().containsAll(
                java.util.List.of("NAME", "AMOUNT", "OPEN_DATE", "PHONES", "MY_LOCAL_FIELD", "MV_LOCAL")));
    }

    @Test
    void xmlTablesProduceNoBlobViewQuery() throws Exception {
        BatchTestContext context = run("X");

        assertFalse(context.jobExecution().getExecutionContext().containsKey("VIEW_QUERY"),
                "une table XML est lue directement, sans vue intermédiaire");
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private String runAndGet(String storageType, String key) throws Exception {
        return run(storageType).jobExecution().getExecutionContext().getString(key);
    }

    private BatchTestContext run(String storageType) throws Exception {
        when(tafjVocRepository.findTafjVocByOrclFileName("FBNK_CUSTOMER"))
                .thenReturn(Optional.of(new TafjVoc("CUSTOMER", storageType, "FBNK_CUSTOMER")));
        StandardSelectionRecord record = new StandardSelectionRecord();
        record.setRecid("CUSTOMER");
        record.setXmlRecord(RICH_XML);
        when(standardSelectionRepository.findById("CUSTOMER")).thenReturn(Optional.of(record));

        StandardSelectionTasklet tasklet = new StandardSelectionTasklet(
                jdbcBi, tafjVocRepository, standardSelectionRepository);
        tasklet.setT24TableName("CUSTOMER");
        tasklet.setOracleTableName("FBNK_CUSTOMER");
        tasklet.setTableType(storageType);

        BatchTestContext context = BatchTestContext.create();
        tasklet.execute(context.contribution(), context.chunkContext());
        return context;
    }
}
