package com.t24extractor.preparedstatementsetter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.alibaba.fastjson2.JSONObject;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ItemPrepStmtSetterTest {

    @Test
    void bindsScalarsCollectionsAndNullsInConfiguredOrder() throws Exception {
        ItemPrepStmtSetter setter = new ItemPrepStmtSetter();
        ReflectionTestUtils.setField(setter, "columns", List.of("_id", "tags", "missing"));
        JSONObject document = new JSONObject();
        document.put("_id", "ID-1");
        document.put("tags", List.of("A", "B"));
        PreparedStatement statement = mock(PreparedStatement.class);

        setter.setValues(document, statement);

        verify(statement).setObject(1, "ID-1");
        verify(statement).setString(2, "[\"A\",\"B\"]");
        verify(statement).setString(3, null);
    }
}
