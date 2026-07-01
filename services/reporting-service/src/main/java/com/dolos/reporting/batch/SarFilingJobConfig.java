package com.dolos.reporting.batch;

import com.dolos.reporting.config.ReportingProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The Phase 6B batch pipeline: {@code sarFilingJob}.
 *
 * <pre>
 *   alert.alert_view (HIGH, business date) ──▶ [SAR/STR processor] ──▶ MinIO + reporting.filed_report
 * </pre>
 *
 * Chunk-oriented (commit every 10). Reader + processor are step-scoped so they bind the
 * {@code businessDate} job parameter. The single identifying parameter (business date) is what gives the
 * job its idempotency/restartability contract — see {@link com.dolos.reporting.ReportLauncher}.
 */
@Configuration
public class SarFilingJobConfig {

    private static final int CHUNK = 10;

    /** Reads HIGH-severity alerts raised on the business date, oldest first, from the alert read model. */
    @Bean
    @StepScope
    public JdbcPagingItemReader<AlertRow> alertReader(
            DataSource dataSource,
            @Value("#{jobParameters['businessDate']}") String businessDate)
            throws Exception {

        SqlPagingQueryProviderFactoryBean providerFactory = new SqlPagingQueryProviderFactoryBean();
        providerFactory.setDataSource(dataSource);
        providerFactory.setSelectClause(
                "alert_id, transaction_id, account_id, score, severity, title, reasons, detail, raised_at");
        providerFactory.setFromClause("alert.alert_view");
        providerFactory.setWhereClause("severity = 'HIGH' AND raised_at::date = :businessDate");
        providerFactory.setSortKeys(Map.of("alert_id", Order.ASCENDING));
        PagingQueryProvider queryProvider = providerFactory.getObject();

        return new JdbcPagingItemReaderBuilder<AlertRow>()
                .name("alertReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.<String, Object>of("businessDate", LocalDate.parse(businessDate)))
                .pageSize(100)
                .rowMapper(
                        (rs, rowNum) ->
                                new AlertRow(
                                        rs.getObject("alert_id", UUID.class),
                                        rs.getObject("transaction_id", UUID.class),
                                        rs.getString("account_id"),
                                        rs.getInt("score"),
                                        rs.getString("severity"),
                                        rs.getString("title"),
                                        rs.getString("reasons"),
                                        rs.getString("detail"),
                                        rs.getTimestamp("raised_at").toInstant()))
                .build();
    }

    @Bean
    @StepScope
    public AlertToFiledReportProcessor sarProcessor(
            @Value("#{jobParameters['businessDate']}") String businessDate, ReportingProperties props) {
        return new AlertToFiledReportProcessor(LocalDate.parse(businessDate), props.minio().bucket());
    }

    /** Upserts the filed report into {@code reporting.filed_report} (idempotent on {@code alert_id}). */
    @Bean
    public JdbcBatchItemWriter<FiledReport> filedReportJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<FiledReport>()
                .dataSource(dataSource)
                .sql(
                        "INSERT INTO reporting.filed_report"
                            + " (report_ref, alert_id, account_id, report_type, score, business_date,"
                            + " object_pointer, narrative, filed_at)"
                            + " VALUES (:reportRef, :alertId, :accountId, :reportType, :score,"
                            + " :businessDate, :objectPointer, :narrative, now())"
                            + " ON CONFLICT (alert_id) DO UPDATE SET"
                            + " report_ref = EXCLUDED.report_ref, account_id = EXCLUDED.account_id,"
                            + " report_type = EXCLUDED.report_type, score = EXCLUDED.score,"
                            + " business_date = EXCLUDED.business_date,"
                            + " object_pointer = EXCLUDED.object_pointer, narrative = EXCLUDED.narrative,"
                            + " filed_at = now()")
                .itemSqlParameterSourceProvider(
                        report ->
                                new MapSqlParameterSource()
                                        .addValue("reportRef", report.reportRef())
                                        .addValue("alertId", report.alertId())
                                        .addValue("accountId", report.accountId())
                                        .addValue("reportType", report.reportType())
                                        .addValue("score", report.score())
                                        .addValue("businessDate", report.businessDate())
                                        .addValue("objectPointer", report.objectPointer())
                                        .addValue("narrative", report.narrative()))
                .assertUpdates(false)
                .build();
    }

    /** MinIO first (render the object), then JDBC (record the row + pointer) — both in the same chunk tx. */
    @Bean
    public CompositeItemWriter<FiledReport> reportWriter(
            MinioReportWriter minioWriter, JdbcBatchItemWriter<FiledReport> filedReportJdbcWriter) {
        CompositeItemWriter<FiledReport> composite = new CompositeItemWriter<>();
        composite.setDelegates(List.of(minioWriter, filedReportJdbcWriter));
        return composite;
    }

    @Bean
    public Step fileReportsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<AlertRow> alertReader,
            AlertToFiledReportProcessor sarProcessor,
            CompositeItemWriter<FiledReport> reportWriter) {
        return new StepBuilder("fileReportsStep", jobRepository)
                .<AlertRow, FiledReport>chunk(CHUNK, transactionManager)
                .reader(alertReader)
                .processor(sarProcessor)
                .writer(reportWriter)
                .build();
    }

    @Bean
    public Job sarFilingJob(JobRepository jobRepository, Step fileReportsStep) {
        // No incrementer: businessDate is the sole IDENTIFYING parameter, so Spring Batch treats each
        // date as one JobInstance — a completed date will not re-run (idempotent) and a failed date
        // restarts on relaunch (restartable). See ReportLauncher for how the completion case is handled.
        return new JobBuilder("sarFilingJob", jobRepository).start(fileReportsStep).build();
    }
}
