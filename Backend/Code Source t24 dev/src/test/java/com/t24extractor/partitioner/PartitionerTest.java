package com.t24extractor.partitioner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

class PartitionerTest {

    @Test
    void splitsColumnRangeWithoutLosingRows() {
        ColumnRangePartitioner partitioner = new ColumnRangePartitioner();
        ReflectionTestUtils.setField(partitioner, "count", 10);

        Map<String, ExecutionContext> result = partitioner.partition(3);

        assertEquals(3, result.size());
        assertEquals(1, result.get("partition0").getInt("minValue"));
        assertEquals(4, result.get("partition0").getInt("maxValue"));
        assertEquals(9, result.get("partition2").getInt("minValue"));
        assertEquals(10, result.get("partition2").getInt("maxValue"));
    }

    @Test
    void createsOneMssqlPartitionPerGridSlot() {
        Map<String, ExecutionContext> result = new MssqlPartitioner().partition(3);

        assertEquals(3, result.size());
        assertEquals(0, result.get("partition0").getInt("value"));
        assertEquals(2, result.get("partition2").getInt("value"));
    }
}
