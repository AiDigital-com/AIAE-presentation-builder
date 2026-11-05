package com.aidigital.reportconstructor.service.reports.model;

import java.time.YearMonth;

/**
 * One calendar month of a paid channel's daily FULL-FLIGHT delivery, for the monthly trend charts.
 *
 * @param month   the calendar month
 * @param metrics the month's delivery
 */
public record PaidMonth(YearMonth month, PaidMetrics metrics) {
}
