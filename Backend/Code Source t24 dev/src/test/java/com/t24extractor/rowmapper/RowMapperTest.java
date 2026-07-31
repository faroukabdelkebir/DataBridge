package com.t24extractor.rowmapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.model.T24Table;
import com.t24extractor.tasklet.StandardSelectionTasklet;
import java.sql.ResultSet;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RowMapperTest {

    @AfterEach
    void clearSharedFields() {
        StandardSelectionTasklet.getBlobViewFields().clear();
    }

    @Test
    void mapsXmlColumnsByPosition() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn("ID-1");
        when(resultSet.getString(2)).thenReturn("<row/>");

        T24Table result = new XMLRowMapper().mapRow(resultSet, 0);

        assertEquals("ID-1", result.getRecid());
        assertEquals("<row/>", result.getXmlRecord());
    }

    @Test
    void mapsBlobFieldsAndIgnoresUnavailableOptionalField() throws Exception {
        StandardSelectionTasklet.getBlobViewFields().add("NAME");
        StandardSelectionTasklet.getBlobViewFields().add("MISSING");
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("FIELD_ID")).thenReturn("ID-2");
        when(resultSet.getObject("NAME")).thenReturn("Alice");
        when(resultSet.getObject("MISSING")).thenThrow(new RuntimeException("not selected"));
        when(resultSet.getLong("rowNumber")).thenReturn(9L);

        HashMap<String, Object> result = new BlobRowMapper().mapRow(resultSet, 0);

        assertEquals("ID-2", result.get("_id"));
        assertEquals("Alice", result.get("NAME"));
        assertEquals(9L, result.get("rowNumber"));
    }
}
