package wevioo.se.monitoringms.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.model.BatchStepExecution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Non-régression du SQL et du mapping des lignes Spring Batch Metadata :
 * le JdbcTemplate est mocké, la requête émise est capturée puis le RowMapper
 * est appliqué à un ResultSet simulé.
 */
@ExtendWith(MockitoExtension.class)
class BatchExecutionRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    private BatchExecutionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new BatchExecutionRepository(jdbc);
    }

    // ── Requêtes ─────────────────────────────────────────────────────────────

    @Test
    void findRunningSelectsOnlyStartedAndStartingExecutions() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        repository.findRunning();

        assertThat(capturedSql()).contains("STATUS IN ('STARTED','STARTING')")
                .contains("INNER JOIN BATCH_JOB_INSTANCE")
                .contains("ORDER BY je.START_TIME DESC");
    }

    @Test
    void findRecentAppliesTheLimitAsAFetchArgument() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(25))).thenReturn(List.of());

        repository.findRecent(25);

        verify(jdbc).query(anyString(), any(RowMapper.class), eq(25));
    }

    @Test
    void findByJobNamePassesNameThenLimit() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("T24"), eq(20))).thenReturn(List.of());

        repository.findByJobName("T24", 20);

        verify(jdbc).query(anyString(), any(RowMapper.class), eq("T24"), eq(20));
    }

    @Test
    void findByIdReturnsTheFirstRow() {
        BatchJobExecution job = BatchJobExecution.builder().jobExecutionId(42L).build();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of(job));

        assertThat(repository.findById(42L)).contains(job);
    }

    @Test
    void findByIdReturnsEmptyWhenNoRowMatches() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(999L))).thenReturn(List.of());

        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findLastSuccessfulByJobNameKeepsOnlyCompletedExecutions() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("T24"))).thenReturn(List.of());

        Optional<BatchJobExecution> result = repository.findLastSuccessfulByJobName("T24");

        assertThat(result).isEmpty();
        assertThat(capturedSql("T24")).contains("je.STATUS = 'COMPLETED'")
                .contains("ORDER BY je.END_TIME DESC")
                .contains("FETCH NEXT 1 ROWS ONLY");
    }

    // ── Compteurs : null défensif ────────────────────────────────────────────

    @Test
    void countCompletedTodayFallsBackToZeroWhenTheDriverReturnsNull() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);

        assertThat(repository.countCompletedToday()).isZero();
    }

    @Test
    void countCompletedTodayReturnsTheAggregate() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(14);

        assertThat(repository.countCompletedToday()).isEqualTo(14);
    }

    @Test
    void countFailedTodayFallsBackToZeroWhenTheDriverReturnsNull() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);

        assertThat(repository.countFailedToday()).isZero();
    }

    @Test
    void computeSuccessRateFallsBackToZeroWhenTheDriverReturnsNull() {
        when(jdbc.queryForObject(anyString(), eq(Double.class), eq(7))).thenReturn(null);

        assertThat(repository.computeSuccessRate(7)).isZero();
    }

    @Test
    void computeSuccessRateReturnsTheAggregate() {
        when(jdbc.queryForObject(anyString(), eq(Double.class), eq(7))).thenReturn(96.25);

        assertThat(repository.computeSuccessRate(7)).isEqualTo(96.25);
    }

    @Test
    void countConsecutiveFailuresFallsBackToZeroWhenTheDriverReturnsNull() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("T24"))).thenReturn(null);

        assertThat(repository.countConsecutiveFailures("T24")).isZero();
    }

    // ── RowMappers ───────────────────────────────────────────────────────────

    @Test
    void jobRowMapperMapsEveryColumn() throws SQLException {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        repository.findRunning();

        ResultSet rs = resultSet();
        when(rs.getLong("JOB_EXECUTION_ID")).thenReturn(42L);
        when(rs.getLong("JOB_INSTANCE_ID")).thenReturn(7L);
        when(rs.getString("JOB_NAME")).thenReturn("T24");
        when(rs.getInt("VERSION")).thenReturn(2);
        when(rs.getTimestamp("CREATE_TIME")).thenReturn(Timestamp.valueOf("2026-07-28 05:00:00"));
        when(rs.getTimestamp("START_TIME")).thenReturn(Timestamp.valueOf("2026-07-28 05:00:10"));
        when(rs.getTimestamp("END_TIME")).thenReturn(Timestamp.valueOf("2026-07-28 05:30:10"));
        when(rs.getTimestamp("LAST_UPDATED")).thenReturn(Timestamp.valueOf("2026-07-28 05:30:11"));
        when(rs.getString("STATUS")).thenReturn("COMPLETED");
        when(rs.getString("EXIT_CODE")).thenReturn("COMPLETED");
        when(rs.getString("EXIT_MESSAGE")).thenReturn("");

        BatchJobExecution job = capturedJobMapper().mapRow(rs, 0);

        assertThat(job.getJobExecutionId()).isEqualTo(42L);
        assertThat(job.getJobInstanceId()).isEqualTo(7L);
        assertThat(job.getJobName()).isEqualTo("T24");
        assertThat(job.getVersion()).isEqualTo(2);
        assertThat(job.getStartTime()).isEqualTo(LocalDateTime.of(2026, 7, 28, 5, 0, 10));
        assertThat(job.getEndTime()).isEqualTo(LocalDateTime.of(2026, 7, 28, 5, 30, 10));
        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getDurationSeconds()).isEqualTo(1800L);
    }

    @Test
    void jobRowMapperKeepsNullTimestampsAsNull() throws SQLException {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        repository.findRunning();

        ResultSet rs = resultSet();
        when(rs.getString("STATUS")).thenReturn("STARTED");
        when(rs.getTimestamp(anyString())).thenReturn(null);

        BatchJobExecution job = capturedJobMapper().mapRow(rs, 0);

        assertThat(job.getStartTime()).isNull();
        assertThat(job.getEndTime()).isNull();
        assertThat(job.getCreateTime()).isNull();
        assertThat(job.getLastUpdated()).isNull();
        assertThat(job.getDurationSeconds()).isNull();
        assertThat(job.isRunning()).isTrue();
    }

    @Test
    void stepRowMapperMapsEveryMetric() throws SQLException {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());
        repository.findStepsByJobExecutionId(42L);

        ResultSet rs = resultSet();
        when(rs.getLong("STEP_EXECUTION_ID")).thenReturn(101L);
        when(rs.getLong("JOB_EXECUTION_ID")).thenReturn(42L);
        when(rs.getString("STEP_NAME")).thenReturn("extractCustomer");
        when(rs.getInt("VERSION")).thenReturn(1);
        when(rs.getTimestamp("START_TIME")).thenReturn(Timestamp.valueOf("2026-07-28 05:00:00"));
        when(rs.getTimestamp("END_TIME")).thenReturn(Timestamp.valueOf("2026-07-28 05:00:20"));
        when(rs.getTimestamp("CREATE_TIME")).thenReturn(null);
        when(rs.getTimestamp("LAST_UPDATED")).thenReturn(null);
        when(rs.getString("STATUS")).thenReturn("COMPLETED");
        when(rs.getLong("COMMIT_COUNT")).thenReturn(10L);
        when(rs.getLong("READ_COUNT")).thenReturn(1000L);
        when(rs.getLong("FILTER_COUNT")).thenReturn(5L);
        when(rs.getLong("WRITE_COUNT")).thenReturn(980L);
        when(rs.getLong("READ_SKIP_COUNT")).thenReturn(3L);
        when(rs.getLong("WRITE_SKIP_COUNT")).thenReturn(2L);
        when(rs.getLong("PROCESS_SKIP_COUNT")).thenReturn(1L);
        when(rs.getLong("ROLLBACK_COUNT")).thenReturn(0L);
        when(rs.getString("EXIT_CODE")).thenReturn("COMPLETED");
        when(rs.getString("EXIT_MESSAGE")).thenReturn("");

        BatchStepExecution step = capturedStepMapper().mapRow(rs, 0);

        assertThat(step.getStepExecutionId()).isEqualTo(101L);
        assertThat(step.getStepName()).isEqualTo("extractCustomer");
        assertThat(step.getReadCount()).isEqualTo(1000L);
        assertThat(step.getWriteCount()).isEqualTo(980L);
        assertThat(step.getTotalSkipCount()).isEqualTo(6L);
        assertThat(step.getDurationSeconds()).isEqualTo(20L);
        assertThat(step.getThroughputPerSecond()).isEqualTo(49.0);
    }

    // ── Agrégats ─────────────────────────────────────────────────────────────

    @Test
    void jobAggregateComputesItsSuccessRate() {
        var aggregate = new BatchExecutionRepository.JobAggregate("T24", 10, 8, 2, 120.5);

        assertThat(aggregate.successRate()).isEqualTo(80.0);
    }

    @Test
    void jobAggregateSuccessRateIsZeroWithoutAnyExecution() {
        var aggregate = new BatchExecutionRepository.JobAggregate("T24", 0, 0, 0, 0.0);

        assertThat(aggregate.successRate()).isZero();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * ResultSet permissif : seules les colonnes utiles au test sont stubbées,
     * les autres retournent la valeur par défaut du driver simulé.
     */
    private static ResultSet resultSet() {
        return mock(ResultSet.class, withSettings().strictness(Strictness.LENIENT));
    }

    /** SQL de l'appel {@code query(sql, rowMapper)} sans paramètre lié. */
    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        return sql.getValue();
    }

    /** SQL de l'appel {@code query(sql, rowMapper, args...)}. */
    private String capturedSql(Object arg) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(arg));
        return sql.getValue();
    }

    @SuppressWarnings("unchecked")
    private RowMapper<BatchJobExecution> capturedJobMapper() {
        ArgumentCaptor<RowMapper<BatchJobExecution>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture());
        return mapper.getValue();
    }

    @SuppressWarnings("unchecked")
    private RowMapper<BatchStepExecution> capturedStepMapper() {
        ArgumentCaptor<RowMapper<BatchStepExecution>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture(), eq(42L));
        return mapper.getValue();
    }
}
