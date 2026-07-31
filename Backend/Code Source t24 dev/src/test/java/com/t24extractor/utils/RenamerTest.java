package com.t24extractor.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class RenamerTest {

    private final Renamer renamer = new Renamer();

    @Test
    void removesHistorySuffixForStandardSelection() {
        assertEquals("ACCOUNT", renamer.ssRenamer("ACCOUNT$HIS"));
        assertEquals("ACCOUNT", renamer.ssRenamer("ACCOUNT"));
    }

    @Test
    void convertsT24NameForBi() {
        assertEquals("FUNDS_TRANSFER#HIS", renamer.biRenamer("FUNDS.TRANSFER$HIS"));
    }

    @Test
    void rejectsNullNames() {
        assertThrows(NoSuchElementException.class, () -> renamer.ssRenamer(null));
        assertThrows(NoSuchElementException.class, () -> renamer.biRenamer(null));
    }
}
