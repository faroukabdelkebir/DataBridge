package com.t24extractor.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.t24extractor.model.T24Table;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Décodage XMLRECORD → JSON.
 *
 * T24 encode la multivaluation dans les attributs {@code m} (multi-value) et
 * {@code s} (sub-value), parfois absents. Chaque combinaison produit une paire
 * (mvsequence, sequence) différente, et ces séquences sont ensuite éclatées en
 * tables satellites : une erreur de numérotation désaligne définitivement les
 * lignes filles de leur enregistrement parent.
 */
class XMLProcessorLocalRefTest {

    /** c2, c3, c4 et c6 sont multivalués ; c5 porte les champs LOCAL.REF. */
    private static final String STANDARD_SELECTION = """
            {
              "sysField":[{"c1":"NAME","c2":"PHONES","c3":"ADDRESSES","c4":"TAGS","c6":"NOTES","c5":"LOCAL_REF"}],
              "typeField":[{"c1":"S","c2":"M","c3":"M","c4":"M","c6":"M","c5":"S"}],
              "usrField":[{"m1":"MY_LOCAL","m2":"MV_LOCAL","m3":"OTHER_LOCAL"}],
              "UsrFieldType":[{"m1":"S","m2":"M","m3":"M"}],
              "localRefPos":["c5"]
            }
            """;

    @Test
    void derivesMultiAndSubSequenceFromBothAttributes() throws Exception {
        JSONObject result = process("<row><c2 m=\"1\" s=\"1\">111</c2><c2 m=\"2\" s=\"3\">222</c2></row>");

        JSONArray phones = result.getJSONArray("PHONES");
        assertEquals(2, phones.size());
        assertEquals(1, phones.getJSONObject(0).getIntValue("mvsequence"));
        assertEquals(1, phones.getJSONObject(0).getIntValue("sequence"));
        assertEquals(2, phones.getJSONObject(1).getIntValue("mvsequence"));
        assertEquals(3, phones.getJSONObject(1).getIntValue("sequence"));
    }

    @Test
    void defaultsSubSequenceToOneWhenOnlyMultiValueIsPresent() throws Exception {
        JSONObject result = process("<row><c3 m=\"4\">Rue A</c3></row>");

        JSONObject address = result.getJSONArray("ADDRESSES").getJSONObject(0);
        assertEquals(4, address.getIntValue("mvsequence"));
        assertEquals(1, address.getIntValue("sequence"), "sous-séquence implicite");
    }

    @Test
    void defaultsMultiValueToOneWhenOnlySubSequenceIsPresent() throws Exception {
        JSONObject result = process("<row><c4 s=\"7\">tag</c4></row>");

        JSONObject tag = result.getJSONArray("TAGS").getJSONObject(0);
        assertEquals(1, tag.getIntValue("mvsequence"), "multivaleur implicite");
        assertEquals(7, tag.getIntValue("sequence"));
    }

    @Test
    void defaultsBothSequencesWhenNoAttributeIsPresent() throws Exception {
        JSONObject result = process("<row><c6>une note</c6></row>");

        JSONObject note = result.getJSONArray("NOTES").getJSONObject(0);
        assertEquals(1, note.getIntValue("mvsequence"));
        assertEquals(1, note.getIntValue("sequence"));
    }

    @Test
    void emptyElementsAreDiscarded() throws Exception {
        JSONObject result = process("<row><c1>   </c1><c2 m=\"1\" s=\"1\">111</c2></row>");

        assertFalse(result.containsKey("NAME"), "un champ vide ne doit pas créer de colonne nulle");
        assertTrue(result.containsKey("PHONES"));
    }

    // ── Champs LOCAL.REF ─────────────────────────────────────────────────────

    @Test
    void singleLocalRefFieldIsStoredAsAScalar() throws Exception {
        JSONObject result = process("<row><c5 m=\"1\">valeur locale</c5></row>");

        assertEquals("valeur locale", result.getString("MY_LOCAL"));
    }

    @Test
    void multiValueLocalRefWithoutSubSequenceIsStoredAsACollection() throws Exception {
        JSONObject result = process("<row><c5 m=\"3\">valeur</c5></row>");

        JSONArray values = result.getJSONArray("OTHER_LOCAL");
        assertEquals(1, values.size());
        assertEquals("valeur", values.getJSONObject(0).getString("OTHER_LOCAL"));
        assertEquals(1, values.getJSONObject(0).getIntValue("sequence"));
    }

    @Test
    void repeatedLocalRefOccurrencesAreCollectedOnce() throws Exception {
        JSONObject result = process(
                "<row><c5 m=\"1\">local</c5><c5 m=\"2\" s=\"1\">A</c5><c5 m=\"2\" s=\"2\">B</c5></row>");

        // Les occurrences suivantes du même m sont ignorées : sans cette
        // déduplication, la collection serait réécrite à chaque sous-valeur.
        JSONArray values = result.getJSONArray("MV_LOCAL");
        assertEquals(1, values.size());
        assertEquals("A", values.getJSONObject(0).getString("MV_LOCAL"));
        assertEquals("local", result.getString("MY_LOCAL"));
    }

    @Test
    void localRefWithoutAttributeDefaultsToTheFirstSlot() throws Exception {
        JSONObject result = process("<row><c5>valeur locale</c5></row>");

        // Un LOCAL.REF sans attribut m est rattaché au premier champ local.
        assertEquals("valeur locale", result.getString("MY_LOCAL"));
    }

    @Test
    void stopsProcessingWhenTableDeclaresNoLocalRefField() throws Exception {
        XMLProcessor processor = new XMLProcessor();
        processor.setOracleTableName("CUSTOMER");
        ReflectionTestUtils.setField(processor, "ss", """
                {
                  "sysField":[{"c1":"NAME","c5":"LOCAL_REF"}],
                  "typeField":[{"c1":"S","c5":"S"}],
                  "usrField":[{}],
                  "UsrFieldType":[{}],
                  "localRefPos":["c5"]
                }
                """);

        JSONObject result = processor.process(new T24Table("ID-1",
                "<row><c5 m=\"1\">ignoré</c5><c1>Alice</c1></row>"));

        // Aucun champ local déclaré : le décodage s'arrête, les éléments suivants
        // ne sont pas traités.
        assertFalse(result.containsKey("NAME"));
    }

    @Test
    void archiveRowsKeepTheirBusinessIdentifier() throws Exception {
        XMLProcessor processor = processor("ACCOUNT#ARC");

        JSONObject result = processor.process(new T24Table("ID-1;7", "<row><c1>Alice</c1></row>"));

        assertEquals("ID-1;7", result.getString("_id"));
        assertEquals("ID-1", result.getString("ORIGINAL_ID"));
    }

    @Test
    void liveRowsKeepTheFullIdentifier() throws Exception {
        JSONObject result = processor("CUSTOMER").process(new T24Table("ID-1;7", "<row><c1>Alice</c1></row>"));

        assertEquals("ID-1;7", result.getString("ORIGINAL_ID"));
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private JSONObject process(String xmlRecord) throws Exception {
        return processor("CUSTOMER").process(new T24Table("ID-1", xmlRecord));
    }

    private XMLProcessor processor(String oracleTableName) {
        XMLProcessor processor = new XMLProcessor();
        processor.setOracleTableName(oracleTableName);
        ReflectionTestUtils.setField(processor, "ss", STANDARD_SELECTION);
        return processor;
    }
}
