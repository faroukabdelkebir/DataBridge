package wevioo.se.monitoringms.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsRecordTest {

    @Test
    void reconciliationRateIsTheShareOfSourceRowsActuallyLoaded() {
        StatsRecord stats = StatsRecord.builder().oracleCount(1000).sqlCount(970).build();

        assertThat(stats.getReconciliationRate()).isEqualTo(97.0);
    }

    @Test
    void reconciliationRateIsNullWhenTheSourceIsEmpty() {
        StatsRecord stats = StatsRecord.builder().oracleCount(0).sqlCount(0).build();

        assertThat(stats.getReconciliationRate()).isNull();
        assertThat(stats.isReconciled(5.0)).isFalse();
    }

    @Test
    void aRateInsideTheToleranceIsReconciled() {
        StatsRecord stats = StatsRecord.builder().oracleCount(1000).sqlCount(960).build();

        assertThat(stats.isReconciled(5.0)).isTrue();
    }

    @Test
    void aRateExactlyAtTheToleranceBoundaryIsReconciled() {
        StatsRecord stats = StatsRecord.builder().oracleCount(1000).sqlCount(950).build();

        assertThat(stats.getReconciliationRate()).isEqualTo(95.0);
        assertThat(stats.isReconciled(5.0)).isTrue();
    }

    @Test
    void aRateBelowTheToleranceIsNotReconciled() {
        StatsRecord stats = StatsRecord.builder().oracleCount(1000).sqlCount(949).build();

        assertThat(stats.isReconciled(5.0)).isFalse();
    }

    @Test
    void loadingMoreRowsThanTheSourceStaysReconciled() {
        StatsRecord stats = StatsRecord.builder().oracleCount(100).sqlCount(105).build();

        assertThat(stats.getReconciliationRate()).isEqualTo(105.0);
        assertThat(stats.isReconciled(5.0)).isTrue();
    }
}
