package com.t24extractor.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import org.junit.jupiter.api.Test;

class DateUtilsTest {

    @Test
    void calculatesAbsoluteDifferenceInBothDirections() {
        Date first = new Date(0);
        Date second = new Date((2 * 3600L + 3 * 60L + 4) * 1000);

        assertEquals("02:03:04", DateUtils.differenceBetween(first, second));
        assertEquals("02:03:04", DateUtils.differenceBetween(second, first));
    }
}
