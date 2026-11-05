package com.aidigital.reportconstructor.service.reports.model;

import java.util.List;

/**
 * One responsive search ad from the Google Ads export's AD BREAKDOWN tab.
 *
 * @param campaign     the campaign the ad runs in
 * @param group        the ad group the ad runs in
 * @param headlines    the ad's non-blank headlines, in order
 * @param descriptions the ad's non-blank descriptions, in order
 * @param landingPage  the ad's landing page, or {@code ""}
 * @param metrics      the ad's delivery
 */
public record PaidAd(String campaign, String group, List<String> headlines, List<String> descriptions,
		String landingPage, PaidMetrics metrics) {
}
