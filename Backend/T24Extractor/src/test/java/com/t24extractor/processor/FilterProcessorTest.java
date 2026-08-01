package com.t24extractor.processor;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.t24extractor.model.T24Table;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FilterProcessorTest {

    @Test
    void blobFilterDropsKnownIdsAndKeepsNewRows() throws Exception {
        BlobFilterProcessor processor = new BlobFilterProcessor();
        ReflectionTestUtils.setField(processor, "idsList", new HashSet<>(java.util.Set.of("known")));
        HashMap<String, Object> known = new HashMap<>();
        known.put("_id", "known");
        HashMap<String, Object> fresh = new HashMap<>();
        fresh.put("_id", "fresh");

        assertNull(processor.process(known));
        assertSame(fresh, processor.process(fresh));
    }

    @Test
    void xmlFilterDropsKnownIdsAndKeepsNewRows() throws Exception {
        XMLFilterProcessor processor = new XMLFilterProcessor();
        ReflectionTestUtils.setField(processor, "idsList", new HashSet<>(java.util.Set.of("known")));
        T24Table known = new T24Table("known", "<r/>");
        T24Table fresh = new T24Table("fresh", "<r/>");

        assertNull(processor.process(known));
        assertSame(fresh, processor.process(fresh));
    }
}
