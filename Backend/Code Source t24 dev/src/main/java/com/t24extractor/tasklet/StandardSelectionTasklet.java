package com.t24extractor.tasklet;

import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import lombok.extern.slf4j.Slf4j;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.ParsingException;
import org.json.JSONObject;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.*;

import static com.t24extractor.commons.Constants.SELECT_BLOB_VIEW_PREFIX;
import static com.t24extractor.commons.Constants.SELECT_BLOB_VIEW_SUFFIX_TPL;
import static com.t24extractor.commons.Constants.UPDATE_BATCH_CONFIG_TYPE_TPL;

/**
 * Traduit le dictionnaire STANDARD.SELECTION de T24 en schéma exploitable.
 *
 * <p>C'est l'étape qui donne un sens aux enregistrements T24 : sans elle, un
 * XMLRECORD n'est qu'une suite de balises {@code c1}, {@code c2}… sans nom ni
 * type. Le dictionnaire est un XML dont chaque balise porte un attribut
 * {@code m} identifiant le champ décrit :
 *
 * <table class="striped">
 *   <caption>Balises interprétées</caption>
 *   <thead><tr><th>Balise</th><th>Signification</th></tr></thead>
 *   <tbody>
 *     <tr><td>{@code c1}</td><td>nom du champ</td></tr>
 *     <tr><td>{@code c2}</td><td>nature ; seuls les champs de données ({@code D}) sont retenus</td></tr>
 *     <tr><td>{@code c3}</td><td>numéro de champ, qui devient la clé {@code c<n>}</td></tr>
 *     <tr><td>{@code c4}</td><td>type T24 ({@code IN2AMT} → Float, {@code IN2.D} → Integer…)</td></tr>
 *     <tr><td>{@code c6}</td><td>longueur</td></tr>
 *     <tr><td>{@code c10}</td><td>{@code S} simple ou {@code M} multivalué</td></tr>
 *     <tr><td>{@code c15}, {@code c17}, {@code c18}, {@code c24}</td>
 *         <td>nom, position, type et cardinalité des champs LOCAL.REF</td></tr>
 *   </tbody>
 * </table>
 *
 * <p>Les points des noms T24 sont remplacés par des soulignés, SQL Server ne
 * les acceptant pas dans un identifiant non échappé.
 *
 * <h2>Sorties</h2>
 * <ul>
 *   <li>{@code sysFieldOds} — dictionnaire nom de colonne → type ODS, référence
 *       unique pour BI_SYNC puis la normalisation JSON ;</li>
 *   <li>{@code Mongo_Standard_Selection} — dictionnaire détaillé consommé par
 *       {@link com.t24extractor.processor.XMLProcessor} ;</li>
 *   <li>{@code VIEW_QUERY} — uniquement pour les tables BLOB, requête de lecture
 *       de la vue éclatée ;</li>
 *   <li>{@code TYPE} — corrige le mode de stockage si TAFJ contredit la
 *       configuration enregistrée.</li>
 * </ul>
 */
@Slf4j
public class StandardSelectionTasklet implements Tasklet {
    private final JdbcTemplate jdbcBatchExecutor;
    private final TafjVocRepository tafjVocRepository;
    private final StandardSelectionRepository standardSelectionRepository;
    private JdbcOperations jdbcExecutor;
    private String oracleTableName;
    private String t24TableName;
    private String tableType;
    protected static final List<String> blobViewFields = new ArrayList<>();
    protected static final Set<String> multiValueBlobFields = new HashSet<>();
    private static final String STRING_TYPE = "String";
    private static final String IN2AMT = "IN2AMT";
    private static final String IN2AMT_AMP = "IN2AMT&";
    private static final String IN2LAMT = "IN2LAMT";
    private static final String IN2LAMT_AMP = "IN2LAMT&";
    private static final String IN2FWDR = "IN2FWDR";
    private static final String IN2FWDR_AMP = "IN2FWDR&";
    private static final String IN2PRICE = "IN2PRICE";
    private static final String IN2PRICE_AMP = "IN2PRICE&";
    private static final String IN2BMT = "IN2BMT";
    private static final String IN2BMT_AMP = "IN2BMT&";
    private static final String IN2NOMAMT = "IN2NOMAMT";
    private static final String IN2NOMAMT_AMP = "IN2NOMAMT&";
    private static final String IN2TAX = "IN2TAX";
    private static final String IN2TAX_AMP = "IN2TAX&";
    private static final String IN2DEC = "IN2DEC";
    private static final String IN2DEC_AMP = "IN2DEC&";
    private static final String IN2RR = "IN2RR";
    private static final String IN2RR_AMP = "IN2RR&";
    private static final String IN2CCYDEC = "IN2CCYDEC";
    private static final String IN2CCYDEC_AMP = "IN2CCYDEC&";
    private static final String IN2R_AMT = "IN2R.AMT";
    private static final String IN2R_AMT_AMP = "IN2R.AMT&";

    public StandardSelectionTasklet(
            @Qualifier("jdbcBI") JdbcTemplate jdbcBatchExecutor,
            TafjVocRepository tafjVocRepository,
            StandardSelectionRepository standardSelectionRepository) {
        this.jdbcBatchExecutor = jdbcBatchExecutor;
        this.tafjVocRepository = tafjVocRepository;
        this.standardSelectionRepository = standardSelectionRepository;
    }

    protected JdbcOperations getJdbcTemplate() {
        return jdbcExecutor;
    }

    /** @param jdbcOperations accès à la base T24 (Oracle) */
    public void setJdbcExecutor(JdbcOperations jdbcOperations) {
        jdbcExecutor = jdbcOperations;
    }

    /** @param oracleName table Oracle physique, par exemple {@code FBNK_CUSTOMER} */
    public void setOracleTableName(String oracleName) {
        oracleTableName = oracleName;
    }

    /** @param t24Name nom fonctionnel T24, par exemple {@code FUNDS.TRANSFER$HIS} */
    public void setT24TableName(String t24Name) {
        t24TableName = t24Name;
    }

    /** @param type mode de stockage attendu ({@code X}, {@code B} ou {@code C}) */
    public void setTableType(String type) {
        tableType = type;
    }

    /**
     * Colonnes de la vue BLOB, publiées pour
     * {@link com.t24extractor.rowmapper.BlobRowMapper} qui ne peut pas les
     * connaître autrement.
     *
     * <p><b>État partagé et mutable</b> : la liste est statique et réinitialisée
     * à chaque exécution. Deux tables BLOB ne peuvent donc pas être traitées
     * simultanément.
     *
     * @return les noms de colonnes de la dernière vue construite
     */
    public static List<String> getBlobViewFields() {
        return blobViewFields;
    }

    /**
     * {@inheritDoc}
     *
     * @return toujours {@link RepeatStatus#FINISHED}
     * @throws NullPointerException si TAFJ_VOC ou le STANDARD.SELECTION ne
     *         référencent pas la table
     * @throws ParsingException si le dictionnaire XML est illisible
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        blobViewFields.clear();
        multiValueBlobFields.clear();
        assert t24TableName != null;

        String tafjVocRow = tafjVocRepository.findTafjVocByOrclFileName(oracleTableName)
                .orElseThrow(NullPointerException::new)
                .isBlob();
        updateTypeIfNeeded(chunkContext, tafjVocRow);

        String xmlStr = resolveXmlRecord();
        ProcessingContext context = processXml(xmlStr);
        putViewQuery(chunkContext, context, tafjVocRow);
        putStandardSelectionInExecutionContext(chunkContext, context);
        return RepeatStatus.FINISHED;
    }

    private void updateTypeIfNeeded(ChunkContext chunkContext, String tafjVocRow) {
        if (tafjVocRow == null || tafjVocRow.isEmpty() || tableType.equals(tafjVocRow)) {
            return;
        }
        jdbcBatchExecutor.execute(
                String.format(UPDATE_BATCH_CONFIG_TYPE_TPL, tafjVocRow, oracleTableName));
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("TYPE", tafjVocRow);
    }

    private String resolveXmlRecord() {
        if (t24TableName.contains("$HIS") || t24TableName.contains("$NAU") || t24TableName.contains("$ARC")) {
            return standardSelectionRepository.findById(t24TableName.substring(0, t24TableName.indexOf("$")))
                    .orElseThrow(NullPointerException::new)
                    .getXmlRecord();
        }
        return standardSelectionRepository.findById(t24TableName)
                .orElseThrow(NullPointerException::new)
                .getXmlRecord();
    }

    private ProcessingContext processXml(String xmlStr) throws ParsingException, IOException {
        ProcessingContext context = new ProcessingContext();
        final Builder parser = new Builder();
        final nu.xom.Document xmlRecord = parser.build(xmlStr, null);
        final Element element = xmlRecord.getRootElement();

        for (Element child : element.getChildElements()) {
            if (child.getAttributeCount() == 0) {
                child.addAttribute(new Attribute("m", "1"));
            }
            applyChildMapping(context, child);
        }
        enrichMappedData(context);
        return context;
    }

    private void applyChildMapping(ProcessingContext context, Element child) {
        switch (child.getLocalName()) {
            case String ignored when "c1".equals(ignored) && !"@ID".equals(child.getValue()) ->
                    context.nameField.put(child.getAttributeValue("m"), child.getValue().replace(".", "_"));
            case String ignored when "c2".equals(ignored) && "D".equals(child.getValue()) ->
                    context.dataField.put(child.getAttributeValue("m"), child.getValue());
            case String ignored when "c3".equals(ignored) && !"0".equals(child.getValue()) ->
                    context.fieldNo.put(child.getAttributeValue("m"), child.getValue());
            case "c4" -> context.dataTypeField.put(child.getAttributeValue("m"), child.getValue());
            case "c6" -> context.lengthField.put(child.getAttributeValue("m"), child.getValue());
            case "c10" -> context.typeField.put(child.getAttributeValue("m"), child.getValue());
            case "c15" -> context.usrFieldName.put(child.getAttributeValue("m"), child.getValue().replace(".", "_"));
            case String ignored when "c17".equals(ignored) && child.getValue().contains(",") && child.getValue().contains(">") ->
                    context.usrFieldNo.put(child.getAttributeValue("m"), child.getValue()
                            .substring(child.getValue().indexOf(',') + 1, child.getValue().indexOf('>')));
            case "c18" -> {
                if (child.getValue().contains("&")) {
                    context.usrFieldDataType.put(child.getAttributeValue("m"),
                            child.getValue().substring(0, child.getValue().indexOf("&")));
                } else {
                    context.usrFieldDataType.put(child.getAttributeValue("m"), child.getValue());
                }
            }
            case "c24" -> context.usrFieldtype.put(child.getAttributeValue("m"), child.getValue());
            default -> {
                // Ignore unsupported XML nodes from STANDARD.SELECTION payload.
            }
        }
    }

    private void enrichMappedData(ProcessingContext context) {
        HashMap<String, String> auxMap = new HashMap<>();
        Set<String> field2keys = context.dataField.keySet();
        field2keys.retainAll(context.fieldNo.keySet());
        for (String key : field2keys) {
            auxMap.put(key, (String) context.fieldNo.get(key));
        }

        Set<String> auxmapKey = auxMap.keySet();
        auxmapKey.retainAll(context.nameField.keySet());
        for (String name : auxmapKey) {
            context.sysField.put("c" + auxMap.get(name), context.nameField.get(name));
        }

        Set<String> tfkey = context.typeField.keySet();
        tfkey.retainAll(auxMap.keySet());
        for (String type : tfkey) {
            context.enhancedFieldType.put("c" + auxMap.get(type), context.typeField.get(type));
        }

        auxmapKey.retainAll(context.nameField.keySet());
        for (String name : auxmapKey) {
            fillSystemFieldData(context, name);
        }

        for (String dTypeField : field2keys) {
            fillEnhancedFieldDataType(context, auxMap, dTypeField);
        }

        fillLocalRefData(context);
    }

    private void fillSystemFieldData(ProcessingContext context, String name) {
        String dataType = context.dataTypeField.optString(name, "");
        String fieldType = context.typeField.optString(name, "S");
        String fieldLength = context.lengthField.optString(name, "MAX");
        List<String> missingFields = missingStandardSelectionFields(context, name);
        if (!missingFields.isEmpty()) {
            log.warn("Champ '{}' du STANDARD.SELECTION incomplet pour la table '{}': champs absents {}, valeurs par defaut utilisees",
                    context.nameField.optString(name, name), t24TableName, missingFields);
        }
        if ("S".equals(fieldType)) {
            if (isFloatLikeType(dataType)) {
                context.sysFieldOds.put((String) context.nameField.get(name), "Float.c" + name);
            } else if (isIntegerLikeType(dataType)) {
                context.sysFieldOds.put((String) context.nameField.get(name), "Integer.c" + name);
            } else {
                context.sysFieldOds.put((String) context.nameField.get(name), STRING_TYPE + fieldLength + ".c" + name);
            }
            return;
        }
        context.sysFieldOds.put((String) context.nameField.get(name), STRING_TYPE + "MAX.c" + name);
        multiValueBlobFields.add((String) context.nameField.get(name));
    }

    private List<String> missingStandardSelectionFields(ProcessingContext context, String name) {
        List<String> missingFields = new ArrayList<>();
        if (!context.nameField.has(name)) {
            missingFields.add("c1");
        }
        if (!context.dataField.has(name)) {
            missingFields.add("c2");
        }
        if (!context.fieldNo.has(name)) {
            missingFields.add("c3");
        }
        if (!context.dataTypeField.has(name)) {
            missingFields.add("c4");
        }
        if (!context.lengthField.has(name)) {
            missingFields.add("c6");
        }
        if (!context.typeField.has(name)) {
            missingFields.add("c10");
        }
        return missingFields;
    }

    private void fillEnhancedFieldDataType(ProcessingContext context, HashMap<String, String> auxMap, String dTypeField) {
        String dataType = context.dataTypeField.optString(dTypeField, "");
        if (isFloatLikeType(dataType)) {
            context.enhancedFieldDataType.put("c" + auxMap.get(dTypeField), "Float");
        } else if (isIntegerLikeType(dataType)) {
            context.enhancedFieldDataType.put("c" + auxMap.get(dTypeField), "Integer");
        } else if (dataType.contains("TEXT")) {
            context.enhancedFieldType.put("c" + auxMap.get(dTypeField), "M");
        } else {
            context.enhancedFieldDataType.put("c" + auxMap.get(dTypeField), STRING_TYPE);
        }
    }

    private void fillLocalRefData(ProcessingContext context) {
        Set<String> usrFieldKeys = context.usrFieldNo.keySet();
        usrFieldKeys.retainAll(context.usrFieldName.keySet());
        for (String key : usrFieldKeys) {
            context.usrField.put("m" + context.usrFieldNo.get(key), context.usrFieldName.get(key));
        }

        usrFieldKeys.retainAll(context.usrFieldtype.keySet());
        for (String type : usrFieldKeys) {
            context.enhancedUsrFieldtype.put("m" + context.usrFieldNo.get(type), context.usrFieldtype.get(type));
            context.usrFieldTypeOds.put(context.usrField.get("m" + context.usrFieldNo.get(type)).toString(), context.usrFieldtype.get(type));
        }

        usrFieldKeys.retainAll(context.usrFieldDataType.keySet());
        for (String dTypeField : usrFieldKeys) {
            if (isFloatLikeUsrType(context.usrFieldDataType.get(dTypeField).toString())) {
                context.enhancedUsrFieldDataType.put("m" + context.usrFieldNo.get(dTypeField), "Float");
            } else {
                context.enhancedUsrFieldDataType.put("m" + context.usrFieldNo.get(dTypeField), STRING_TYPE);
            }
        }

        for (Map.Entry<String, Object> pos : context.sysField.toMap().entrySet()) {
            if (pos.getValue().equals("LOCAL_REF")) {
                context.localRefPos = pos.getKey();
            }
        }
    }

    private void putViewQuery(ChunkContext chunkContext, ProcessingContext context, String tafjVocRow) {
        context.enhancedFieldType.remove("cD");
        if ("X".equalsIgnoreCase(tafjVocRow)) {
            return;
        }

        StringBuilder localRefFields = new StringBuilder();
        Iterator<Map.Entry<String, Object>> usrIterator = context.usrField.toMap().entrySet().iterator();
        while (usrIterator.hasNext()) {
            Map.Entry<String, Object> entry = usrIterator.next();
            localRefFields.append("t.").append(entry.getValue());
            blobViewFields.add(entry.getValue().toString());
            if (usrIterator.hasNext()) {
                localRefFields.append(", ");
            }
        }

        StringBuilder viewQuery = new StringBuilder(SELECT_BLOB_VIEW_PREFIX);
        Iterator<Map.Entry<String, Object>> iterator = context.enhancedFieldType.toMap().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (!entry.getKey().equals(context.localRefPos)) {
                viewQuery.append(context.sysField.get(entry.getKey()));
                blobViewFields.add(context.sysField.get(entry.getKey()).toString());
                if (iterator.hasNext()) {
                    viewQuery.append(", ");
                }
            }
        }

        if (!localRefFields.isEmpty()) {
            viewQuery.append(", ").append(localRefFields);
        }
        viewQuery.append(String.format(SELECT_BLOB_VIEW_SUFFIX_TPL, oracleTableName));
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .put("VIEW_QUERY", viewQuery.toString());
    }

    private void putStandardSelectionInExecutionContext(ChunkContext chunkContext, ProcessingContext context) {
        context.biStandardSelection.put("sysFieldOds", context.sysFieldOds);

        context.standardSelectionJs.append("sysField", context.sysField);
        context.standardSelectionJs.append("typeField", context.enhancedFieldType);
        context.standardSelectionJs.append("DataTypeField", context.enhancedFieldDataType);
        context.standardSelectionJs.append("usrField", context.usrField);
        context.standardSelectionJs.append("UsrFieldType", context.enhancedUsrFieldtype);
        context.standardSelectionJs.append("UsrFieldDataType", context.enhancedUsrFieldDataType);
        context.standardSelectionJs.append("localRefPos", context.localRefPos);

        context.usrField.toMap().forEach((k, v) -> {
            String usrFieldType = context.enhancedUsrFieldtype.optString(k, "");
            if ("M".equals(usrFieldType)) {
                context.sysFieldOds.put(v.toString(), "Local_Ref_StringMAX." + k);
                multiValueBlobFields.add(v.toString());
            } else {
                context.sysFieldOds.put(v.toString(), "Local_Ref_String." + k);
            }
        });

        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .put("Mongo_Standard_Selection", context.standardSelectionJs.toString());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .put("fields", context.fields);
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .put("sysFieldOds", context.sysFieldOds.toString());
    }

    private boolean isIntegerLikeType(String value) {
        return value.contains("IN2.D&") || value.contains("IN2.YM&") || value.equals("IN2.D") || value.equals("IN2.YM");
    }

    private boolean isFloatLikeUsrType(String value) {
        return value.equals(IN2AMT) || value.equals(IN2BMT) || value.equals(IN2CCYDEC) || value.equals(IN2DEC)
                || value.equals(IN2LAMT) || value.equals(IN2NOMAMT) || value.equals(IN2R_AMT) || value.equals(IN2TAX)
                || value.equals(IN2FWDR) || value.equals(IN2PRICE) || value.equals("IN2R") || value.equals(IN2RR);
    }

    private boolean isFloatLikeType(String value) {
        return value.equals(IN2BMT) || value.equals(IN2AMT) || value.contains(IN2AMT_AMP) || value.contains(IN2BMT_AMP)
                || value.equals(IN2CCYDEC) || value.contains(IN2CCYDEC_AMP) || value.equals(IN2DEC) || value.contains(IN2DEC_AMP)
                || value.equals(IN2LAMT) || value.contains(IN2LAMT_AMP) || value.equals(IN2NOMAMT) || value.contains(IN2NOMAMT_AMP)
                || value.equals(IN2R_AMT) || value.contains(IN2R_AMT_AMP) || value.equals(IN2TAX) || value.contains(IN2TAX_AMP)
                || value.equals(IN2FWDR) || value.contains(IN2FWDR_AMP) || value.equals(IN2PRICE) || value.contains(IN2PRICE_AMP)
                || value.equals("IN2R") || value.contains("IN2R&") || value.equals(IN2RR) || value.contains(IN2RR_AMP);
    }

    private static final class ProcessingContext {
        private final JSONObject standardSelectionJs = new JSONObject();
        private final JSONObject fieldNo = new JSONObject();
        private final JSONObject sysField = new JSONObject();
        private final JSONObject dataField = new JSONObject();
        private final JSONObject lengthField = new JSONObject();
        private final JSONObject nameField = new JSONObject();
        private final JSONObject typeField = new JSONObject();
        private final JSONObject enhancedFieldType = new JSONObject();
        private final JSONObject enhancedFieldDataType = new JSONObject();
        private final JSONObject usrField = new JSONObject();
        private final JSONObject sysFieldOds = new JSONObject();
        private final JSONObject biStandardSelection = new JSONObject();
        private final JSONObject dataTypeField = new JSONObject();
        private final JSONObject usrFieldNo = new JSONObject();
        private final JSONObject usrFieldName = new JSONObject();
        private final JSONObject usrFieldtype = new JSONObject();
        private final JSONObject usrFieldDataType = new JSONObject();
        private final JSONObject usrFieldTypeOds = new JSONObject();
        private final JSONObject enhancedUsrFieldtype = new JSONObject();
        private final JSONObject enhancedUsrFieldDataType = new JSONObject();
        private final List<String> fields = new ArrayList<>();
        private String localRefPos = "";
    }
}
