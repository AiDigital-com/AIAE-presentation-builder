package com.aidigital.reportconstructor.service.reports.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaidMetricsTest {

	@Test
	void shouldDeriveTheRatesFromTheTotalsTest() {
		// Given: 2,000 impressions, 50 clicks, $100 spend, 10 conversions
		PaidMetrics m = new PaidMetrics(2_000, 50, 100, 10);

		// When-Then: CTR = clicks/imps, CPC = cost/clicks, CPA = cost/conversions, CR = conversions/clicks
		assertThat(m.ctr()).isCloseTo(2.5, within(1e-9));
		assertThat(m.cpc()).isCloseTo(2.0, within(1e-9));
		assertThat(m.cpa()).isCloseTo(10.0, within(1e-9));
		assertThat(m.cr()).isCloseTo(20.0, within(1e-9));
	}

	@Test
	void shouldLeaveARateEmptyWhenItsDenominatorIsZeroTest() {
		// Given: impressions only — no clicks, no conversions
		PaidMetrics m = new PaidMetrics(52, 0, 0, 0);

		// When-Then:
		assertThat(m.ctr()).isEqualTo(0.0);
		assertThat(m.cpc()).isNull();
		assertThat(m.cpa()).isNull();
		assertThat(m.cr()).isNull();
		assertThat(PaidMetrics.ZERO.ctr()).isNull();
	}

	@Test
	void shouldAddTwoRowsTest() {
		// Given:
		PaidMetrics a = new PaidMetrics(1, 2, 3, 4);

		// When:
		PaidMetrics sum = a.plus(new PaidMetrics(10, 20, 30, 40));

		// Then:
		assertThat(sum).isEqualTo(new PaidMetrics(11, 22, 33, 44));
	}
}
