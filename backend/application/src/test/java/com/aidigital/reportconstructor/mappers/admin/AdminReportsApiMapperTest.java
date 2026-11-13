package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminReportPageV1;
import com.aidigital.reportconstructor.api.v1.model.AdminReportSortV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.api.v1.model.SortDirectionV1;
import com.aidigital.reportconstructor.mappers.reports.ReportsApiMapper;
import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the admin report page envelope (rows delegated to the "My reports" mapper) and the sort
 * and direction wire codes the controller forwards to the service.
 */
class AdminReportsApiMapperTest {

	/** Name of the row-mapper field the generated Spring mapper injects. */
	private static final String ROW_MAPPER_FIELD = "reportsApiMapper";

	@Test
	void shouldMapPageEnvelopeAndDelegateRowsTest() {
		// Given:
		ReportsApiMapper rowMapper = mock(ReportsApiMapper.class);
		AdminReportsApiMapperImpl mapper = new AdminReportsApiMapperImpl();
		ReflectionTestUtils.setField(mapper, ROW_MAPPER_FIELD, rowMapper);
		List<ReportSummary> rows = List.of(new ReportSummary(5L, "EOM", "done", "July EOM",
				LocalDateTime.of(2026, 8, 1, 9, 0), "https://slides/5", "https://sheet/5", "deck.pptx",
				null, null, "alice@aidigital.com", "Alice", 10L, 20L, 30L, 0.5, false));
		ReportSummaryV1 mappedRow = new ReportSummaryV1();
		mappedRow.setJobId(5L);
		mappedRow.setStatus(ReportJobStatusV1.DONE);
		when(rowMapper.toSummaries(same(rows))).thenReturn(List.of(mappedRow));
		AdminReportPage page = new AdminReportPage(12_480L, 2, 50, true, rows);

		// When:
		AdminReportPageV1 dto = mapper.toReportPage(page);

		// Then:
		assertThat(dto.getTotal()).isEqualTo(12_480L);
		assertThat(dto.getPage()).isEqualTo(2);
		assertThat(dto.getSize()).isEqualTo(50);
		assertThat(dto.getHasMore()).isTrue();
		assertThat(dto.getReports()).containsExactly(mappedRow);
	}

	@Test
	void shouldReturnNullForNullPageTest() {
		// Given:
		ReportsApiMapper rowMapper = mock(ReportsApiMapper.class);
		AdminReportsApiMapperImpl mapper = new AdminReportsApiMapperImpl();
		ReflectionTestUtils.setField(mapper, ROW_MAPPER_FIELD, rowMapper);

		// When:
		AdminReportPageV1 dto = mapper.toReportPage(null);

		// Then:
		assertThat(dto).isNull();
		verifyNoInteractions(rowMapper);
	}

	@Test
	void shouldMapEverySortColumnToItsWireCodeTest() {
		// Given:
		AdminReportsApiMapper mapper = new AdminReportsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toSortCode(AdminReportSortV1.CREATED_AT)).isEqualTo("createdAt");
		assertThat(mapper.toSortCode(AdminReportSortV1.TOKENS)).isEqualTo("tokens");
		assertThat(mapper.toSortCode(AdminReportSortV1.SLIDES)).isEqualTo("slides");
		assertThat(mapper.toSortCode(AdminReportSortV1.OWNER)).isEqualTo("owner");
		assertThat(mapper.toSortCode(AdminReportSortV1.TYPE)).isEqualTo("type");
		assertThat(mapper.toSortCode(AdminReportSortV1.STATUS)).isEqualTo("status");
		assertThat(mapper.toSortCode(null)).isNull();
	}

	@Test
	void shouldMapEveryDirectionToItsWireCodeTest() {
		// Given:
		AdminReportsApiMapper mapper = new AdminReportsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toDirectionCode(SortDirectionV1.ASC)).isEqualTo("asc");
		assertThat(mapper.toDirectionCode(SortDirectionV1.DESC)).isEqualTo("desc");
		assertThat(mapper.toDirectionCode(null)).isNull();
	}
}
