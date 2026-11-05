package com.aidigital.reportconstructor.service.reports.engine;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class PaidValueFormatterTest {

	@Test
	void shouldFormatCountsCompactLikeTheSampleDeckTest() {
		// Given:
		PaidValueFormatter fmt = new PaidValueFormatter();

		// When-Then: rounded, one decimal under 100K, whole thousands from there, one-decimal millions
		assertThat(fmt.compact(742)).isEqualTo("742");
		assertThat(fmt.compact(14_920)).isEqualTo("14.9K");
		assertThat(fmt.compact(99_960)).isEqualTo("100K");
		assertThat(fmt.compact(327_412)).isEqualTo("327K");
		assertThat(fmt.compact(2_101_437)).isEqualTo("2.1M");
		assertThat(fmt.moneyCompact(42_800)).isEqualTo("$42.8K");
		assertThat(fmt.count(1_247.6)).isEqualTo("1,248");
	}

	@Test
	void shouldDashRatesAndSharesWithoutADenominatorTest() {
		// Given:
		PaidValueFormatter fmt = new PaidValueFormatter();

		// When-Then:
		assertThat(fmt.money(19.041)).isEqualTo("$19.04");
		assertThat(fmt.money(null)).isEqualTo("—");
		assertThat(fmt.pct1(4.63)).isEqualTo("4.6%");
		assertThat(fmt.pct1(null)).isEqualTo("—");
		assertThat(fmt.share(28, 100)).isEqualTo("28%");
		assertThat(fmt.share1(503, 1000)).isEqualTo("50.3%");
		assertThat(fmt.share(5, 0)).isEqualTo("—");
	}

	@Test
	void shouldSignTheMonthOnMonthChangeTest() {
		// Given:
		PaidValueFormatter fmt = new PaidValueFormatter();

		// When-Then:
		assertThat(fmt.growth(118, 100d)).isEqualTo("+18%");
		assertThat(fmt.growth(95, 100d)).isEqualTo("-5%");
		assertThat(fmt.growth(10, null)).isEqualTo("—");
		assertThat(fmt.growth(10, 0d)).isEqualTo("—");
		assertThat(fmt.month(YearMonth.of(2026, 10))).isEqualTo("October 2026");
	}
}
