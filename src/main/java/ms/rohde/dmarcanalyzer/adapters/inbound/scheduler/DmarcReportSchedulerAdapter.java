package ms.rohde.dmarcanalyzer.adapters.inbound.scheduler;

import ms.rohde.dmarcanalyzer.ports.inbound.DmarcReportProcessingUseCase;
import ms.rohde.hexagonalarch.annotations.DrivingAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Triggers {@link DmarcReportProcessingUseCase} on a configurable cron schedule.
 *
 * <p>A single run failing with an unexpected exception (e.g. the mailbox connection itself
 * failing) is logged and swallowed here so the schedule keeps firing on subsequent runs instead
 * of the scheduler thread dying silently.
 */
@DrivingAdapter
public class DmarcReportSchedulerAdapter {

    private static final Logger LOG = LogManager.getLogger(DmarcReportSchedulerAdapter.class);

    private final DmarcReportProcessingUseCase dmarcReportProcessingUseCase;

    public DmarcReportSchedulerAdapter(DmarcReportProcessingUseCase dmarcReportProcessingUseCase) {
        this.dmarcReportProcessingUseCase = dmarcReportProcessingUseCase;
    }

    @Scheduled(cron = "${dmarc-analyzer.schedule.cron:0 */15 * * * *}")
    public void triggerReportProcessing() {
        try {
            dmarcReportProcessingUseCase.processIncomingReports();
        } catch (RuntimeException e) {
            LOG.error("DMARC report processing run failed unexpectedly", e);
        }
    }
}
