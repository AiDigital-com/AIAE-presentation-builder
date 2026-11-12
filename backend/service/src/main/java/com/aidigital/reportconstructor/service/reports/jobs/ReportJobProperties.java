package com.aidigital.reportconstructor.service.reports.jobs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lifecycle settings for report jobs, bound from {@code app.report-jobs.*}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.report-jobs")
public class ReportJobProperties {

	/**
	 * How long a queued or running job may go without any progress update before it counts as abandoned —
	 * its node stopped while it ran — and is marked failed. A live job writes progress at every step; the
	 * slowest step (a Claude fan-out or a retried Google copy) stays well inside the default of 60 minutes.
	 */
	private int abandonedAfterMinutes = 60;
}
