package com.smart_logistics.backend.service.eta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.eta", name = "enabled", havingValue = "true")
public class EtaRefreshScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtaRefreshScheduler.class);
    private final EtaCalculationService etaCalculationService;

    public EtaRefreshScheduler(EtaCalculationService etaCalculationService) {
        this.etaCalculationService = etaCalculationService;
    }

    @Scheduled(
            initialDelayString = "${app.eta.initial-delay-ms:1000}",
            fixedDelayString = "${app.eta.refresh-delay-ms:1000}"
    )
    public void refresh() {
        EtaCalculationService.EtaRefreshSummary summary =
                etaCalculationService.refreshTransportingTasks();
        if (summary.total() > 0) {
            LOGGER.info("ETA refresh total={} updated={} skipped={} failed={}",
                    summary.total(), summary.updated(), summary.skipped(), summary.failed());
        }
    }
}
