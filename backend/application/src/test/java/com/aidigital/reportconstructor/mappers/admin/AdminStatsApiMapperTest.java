package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminRangeV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSeriesUnitV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTrendUnitV1;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the granularity codes, where the service's wire value and the generated enum's constant
 * name differ; mapping them by name turned the whole dashboard into a 500.
 */
class AdminStatsApiMapperTest {

	@Test
	void shouldMapRangeUnitCodeToItsEnumTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		AdminRangeView range = new AdminRangeView(
				LocalDate.of(2026, 7, 8), LocalDate.of(2026, 8, 6), AdminPeriodUnit.WEEK.getCode());

		// When:
		AdminRangeV1 mapped = mapper.toRange(range);

		// Then:
		assertThat(mapped.getSuggestedUnit()).isEqualTo(AdminTrendUnitV1.WEEK);
		assertThat(mapped.getFrom()).isEqualTo(LocalDate.of(2026, 7, 8));
		assertThat(mapped.getTo()).isEqualTo(LocalDate.of(2026, 8, 6));
	}

	@Test
	void shouldMapEverySeriesUnitCodeToItsEnumTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.DAY.getCode()))
				.isEqualTo(AdminSeriesUnitV1.DAY);
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.WEEK.getCode()))
				.isEqualTo(AdminSeriesUnitV1.WEEK);
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.MONTH.getCode()))
				.isEqualTo(AdminSeriesUnitV1.MONTH);
		assertThat(mapper.toSeriesUnit(null)).isNull();
	}
}
