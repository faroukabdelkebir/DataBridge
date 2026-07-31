package com.t24extractor.processor;

import com.alibaba.fastjson2.JSONArray;

import com.t24extractor.model.T24Table;
import lombok.Setter;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.Elements;
import org.json.JSONObject;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Décode un XMLRECORD T24 en document JSON nommé et typé.
 *
 * <p>S'appuie sur le dictionnaire {@code Mongo_Standard_Selection} pour
 * remplacer les balises anonymes ({@code c1}, {@code c2}…) par les noms de
 * champs métier.
 *
 * <h2>Multivaluation T24</h2>
 * T24 encode la répétition dans deux attributs : {@code m} (multivaleur) et
 * {@code s} (sous-valeur). L'un comme l'autre peuvent manquer, et chaque
 * combinaison a une signification :
 * <table class="striped">
 *   <caption>Interprétation des attributs</caption>
 *   <thead><tr><th>Attributs</th><th>mvsequence</th><th>sequence</th></tr></thead>
 *   <tbody>
 *     <tr><td>{@code m} et {@code s}</td><td>{@code m}</td><td>{@code s}</td></tr>
 *     <tr><td>{@code m} seul</td><td>{@code m}</td><td>1</td></tr>
 *     <tr><td>{@code s} seul</td><td>1</td><td>{@code s}</td></tr>
 *     <tr><td>aucun</td><td>1</td><td>1</td></tr>
 *   </tbody>
 * </table>
 * Ces séquences sont conservées dans le JSON, puis éclatées en tables
 * satellites par {@link com.t24extractor.tasklet.Json_Flattener} : elles
 * rattachent chaque ligne fille à son enregistrement parent.
 *
 * <h2>Champs LOCAL.REF</h2>
 * Les champs locaux sont regroupés dans un conteneur unique, repéré par
 * {@code localRefPos}. Ils sont adressés par leur position ({@code m1},
 * {@code m2}…) et peuvent être simples ou multivalués.
 */
@Component
@StepScope
public class XMLProcessor implements ItemProcessor<T24Table, com.alibaba.fastjson2.JSONObject> {
    private static final String MV_SEQUENCE = "mvsequence";
    private static final String SEQUENCE = "sequence";

    @Value("#{jobExecutionContext['Mongo_Standard_Selection']}")
    String ss;

    @Setter
    String oracleTableName;

    /**
     * {@inheritDoc}
     *
     * <p>Le document produit porte toujours {@code _id} (le RECID) et
     * {@code ORIGINAL_ID}. Sur les tables historisées ({@code #HIS}, {@code #ARC}),
     * {@code ORIGINAL_ID} est le RECID amputé de son numéro de version, ce qui
     * permet de regrouper les versions d'un même enregistrement.
     *
     * @param t24TableItem enregistrement brut lu sur Oracle
     * @return le document JSON prêt à être inséré en BI
     * @throws Exception si le XMLRECORD est malformé
     */
    @Override
    public com.alibaba.fastjson2.JSONObject process(final T24Table t24TableItem) throws Exception {
        ProcessorContext ctx = buildContext();
        String recID = t24TableItem.getRecid();
        com.alibaba.fastjson2.JSONObject jsonObject = initializeOutput(recID);

        final Builder parser = new Builder();
        final InputStream is = new ByteArrayInputStream(t24TableItem.getXmlRecord().getBytes(StandardCharsets.UTF_8));
        final nu.xom.Document xmlRecord = parser.build(is);
        final Element element = xmlRecord.getRootElement();
        final Elements children = element.getChildElements();

        boolean stopProcessing = false;
        for (int i = 0; i < children.size() && !stopProcessing; i++) {
            Element current = children.get(i);
            String localName = current.getLocalName();

            if (current.getValue().trim().isEmpty()) {
                element.removeChild(current);
            } else if (ctx.sysFieldJs.has(localName)) {
                if (isMultiSystemField(ctx, current)) {
                    putMultiSystemField(jsonObject, element, current, ctx);
                } else if (isSingleSystemField(ctx, current)) {
                    jsonObject.put(ctx.sysFieldJs.get(localName).toString(), current.getValue());
                } else if (localName.equals(ctx.local)) {
                    if (ctx.usrFieldJs.isEmpty()) {
                        stopProcessing = true;
                    } else {
                        putLocalRefField(jsonObject, children, i, current, ctx);
                    }
                }
            }
        }
        return jsonObject;
    }

    private ProcessorContext buildContext() {
        JSONObject ts = new JSONObject(ss);
        return new ProcessorContext(
                ts.getJSONArray("sysField").getJSONObject(0),
                ts.getJSONArray("typeField").getJSONObject(0),
                ts.getJSONArray("usrField").getJSONObject(0),
                ts.getJSONArray("UsrFieldType").getJSONObject(0),
                ts.getJSONArray("localRefPos").get(0).toString()
        );
    }

    private com.alibaba.fastjson2.JSONObject initializeOutput(String recID) {
        com.alibaba.fastjson2.JSONObject jsonObject = new com.alibaba.fastjson2.JSONObject();
        jsonObject.put("_id", recID);
        if ((oracleTableName.contains("#HIS") || oracleTableName.contains("#ARC")) && recID.contains(";")) {
            jsonObject.put("ORIGINAL_ID", recID.substring(0, recID.indexOf(";")));
        } else {
            jsonObject.put("ORIGINAL_ID", recID);
        }
        return jsonObject;
    }

    private boolean isMultiSystemField(ProcessorContext ctx, Element current) {
        String localName = current.getLocalName();
        return "M".equals(ctx.sysTypeFieldJs.get(localName))
                && !localName.equals(ctx.local)
                && !"DATE_TIME".equals(ctx.sysFieldJs.get(localName).toString())
                && !current.getValue().trim().isEmpty();
    }

    private boolean isSingleSystemField(ProcessorContext ctx, Element current) {
        String localName = current.getLocalName();
        return "DATE_TIME".equals(ctx.sysFieldJs.get(localName).toString())
                || ("S".equals(ctx.sysTypeFieldJs.get(localName)) && !localName.equals(ctx.local));
    }

    private void putMultiSystemField(com.alibaba.fastjson2.JSONObject jsonObject, Element element, Element current, ProcessorContext ctx) {
        JSONArray jsonArray = new JSONArray();
        for (Element child : element.getChildElements(current.getLocalName())) {
            com.alibaba.fastjson2.JSONObject entry = createMultiValueEntry(child, ctx);
            if (!entry.isEmpty()) {
                jsonArray.add(entry);
            }
            jsonObject.put(ctx.sysFieldJs.get(child.getLocalName()).toString(), jsonArray);
        }
    }

    private com.alibaba.fastjson2.JSONObject createMultiValueEntry(Element child, ProcessorContext ctx) {
        int attrCount = child.getAttributeCount();
        com.alibaba.fastjson2.JSONObject entry = new com.alibaba.fastjson2.JSONObject();
        entry.put(ctx.sysFieldJs.get(child.getLocalName()).toString(), child.getValue());

        if (attrCount == 2) {
            entry.put(MV_SEQUENCE, Integer.valueOf(child.getAttributeValue("m")));
            entry.put(SEQUENCE, Integer.valueOf(child.getAttributeValue("s")));
            return entry;
        }
        if (attrCount == 1) {
            if (child.getAttribute("m") == null) {
                entry.put(SEQUENCE, Integer.valueOf(child.getAttributeValue("s")));
                entry.put(MV_SEQUENCE, 1);
            } else {
                entry.put(MV_SEQUENCE, Integer.valueOf(child.getAttributeValue("m")));
                entry.put(SEQUENCE, 1);
            }
            return entry;
        }
        if (attrCount == 0) {
            entry.put(MV_SEQUENCE, 1);
            entry.put(SEQUENCE, 1);
            return entry;
        }
        return new com.alibaba.fastjson2.JSONObject();
    }

    private void putLocalRefField(com.alibaba.fastjson2.JSONObject jsonObject, Elements children, int i, Element current, ProcessorContext ctx) {
        if (current.getAttributeCount() == 0) {
            current.addAttribute(new Attribute("m", "1"));
        }
        String localRefKey = "m" + current.getAttributeValue("m");
        if ("S".equals(ctx.usrTypeFieldJs.get(localRefKey).toString())) {
            jsonObject.put(ctx.usrFieldJs.get(localRefKey).toString(), current.getValue());
            return;
        }

        if (current.getAttribute("s") == null) {
            putLocalRefCollection(jsonObject, current, ctx, 1);
            return;
        }
        if (i > 0 && Integer.valueOf(children.get(i - 1).getAttributeValue("m"))
                .equals(Integer.valueOf(current.getAttributeValue("m")))) {
            return;
        }
        putLocalRefCollection(jsonObject, current, ctx, Integer.valueOf(current.getAttributeValue("s")));
    }

    private void putLocalRefCollection(com.alibaba.fastjson2.JSONObject jsonObject, Element current, ProcessorContext ctx, int sequence) {
        List<com.alibaba.fastjson2.JSONObject> localRefMult = new ArrayList<>();
        com.alibaba.fastjson2.JSONObject hash = new com.alibaba.fastjson2.JSONObject();
        String localRefKey = "m" + current.getAttributeValue("m");
        hash.put(ctx.usrFieldJs.get(localRefKey).toString(), current.getValue());
        hash.put(SEQUENCE, sequence);
        localRefMult.add(hash);
        jsonObject.put(ctx.usrFieldJs.get(localRefKey).toString(), localRefMult);
    }

    private static final class ProcessorContext {
        private final JSONObject sysFieldJs;
        private final JSONObject sysTypeFieldJs;
        private final JSONObject usrFieldJs;
        private final JSONObject usrTypeFieldJs;
        private final String local;

        private ProcessorContext(
                JSONObject sysFieldJs,
                JSONObject sysTypeFieldJs,
                JSONObject usrFieldJs,
                JSONObject usrTypeFieldJs,
                String local) {
            this.sysFieldJs = sysFieldJs;
            this.sysTypeFieldJs = sysTypeFieldJs;
            this.usrFieldJs = usrFieldJs;
            this.usrTypeFieldJs = usrTypeFieldJs;
            this.local = local;
        }
    }
}
