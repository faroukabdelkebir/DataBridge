package com.t24extractor.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.t24extractor.model.T24Table;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class XMLProcessorTest {

    @Test
    void convertsSingleAndMultiValueFields() throws Exception {
        XMLProcessor processor = new XMLProcessor();
        processor.setOracleTableName("CUSTOMER");
        ReflectionTestUtils.setField(processor, "ss", """
                {
                  "sysField":[{"c1":"NAME","c2":"PHONES"}],
                  "typeField":[{"c1":"S","c2":"M"}],
                  "usrField":[{}],
                  "UsrFieldType":[{}],
                  "localRefPos":["c99"]
                }
                """);
        T24Table item = new T24Table("ID-1",
                "<row><c1>Alice</c1><c2 m=\"1\" s=\"1\">111</c2><c2 m=\"1\" s=\"2\">222</c2></row>");

        JSONObject result = processor.process(item);

        assertEquals("ID-1", result.getString("_id"));
        assertEquals("ID-1", result.getString("ORIGINAL_ID"));
        assertEquals("Alice", result.getString("NAME"));
        JSONArray phones = result.getJSONArray("PHONES");
        assertEquals(2, phones.size());
        assertEquals(111, Integer.parseInt(phones.getJSONObject(0).getString("PHONES")));
        assertEquals(2, phones.getJSONObject(1).getIntValue("sequence"));
    }

    @Test
    void derivesOriginalIdForHistoryRows() throws Exception {
        XMLProcessor processor = new XMLProcessor();
        processor.setOracleTableName("ACCOUNT#HIS");
        ReflectionTestUtils.setField(processor, "ss", """
                {
                  "sysField":[{"c1":"NAME"}],
                  "typeField":[{"c1":"S"}],
                  "usrField":[{}],
                  "UsrFieldType":[{}],
                  "localRefPos":["c99"]
                }
                """);

        JSONObject result = processor.process(new T24Table("ID-1;2", "<row><c1>Alice</c1></row>"));

        assertEquals("ID-1", result.getString("ORIGINAL_ID"));
    }
}
