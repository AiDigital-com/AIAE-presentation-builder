package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.DateFilterModeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportListV1;
import com.aidigital.reportconstructor.api.v1.model.ReportResumeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.api.v1.model.TemplateVariantV1;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.ReportHistory;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportsApiMapperTest {

	private final ReportsApiMapperImpl mapper = new ReportsApiMapperImpl();

	@Test
	void shouldFlattenTheStoredStateOntoTheResumeResponseTest() {
		// Given: a draft whose stored state carries a window, toggles and tactic names
		ReportResume resume = new ReportResume(7L, "https://sheet/7", "https://plan", "https://elevate",
				new ReportResumeState("EOM", "the brief", "the log", "1.2M",
						new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
						Boolean.FALSE, List.of(new BreakdownSelection(2, List.of("aud", "dev"))),
						List.of("Display", "CTV"), TemplateVariant.MPO));

		// When: it is mapped to the API DTO
		ReportResumeV1 dto = mapper.toResume(resume);

		// Then: every nested field lands on the flat response
		assertThat(dto.getJobId()).isEqualTo(7L);
		assertThat(dto.getSheetUrl()).isEqualTo("https://sheet/7");
		assertThat(dto.getMediaPlanUrl()).isEqualTo("https://plan");
		assertThat(dto.getElevateUrl()).isEqualTo("https://elevate");
		assertThat(dto.getReportType()).isEqualTo(ReportTypeV1.EOM);
		assertThat(dto.getBrief()).isEqualTo("the brief");
		assertThat(dto.getChangeLog()).isEqualTo("the log");
		assertThat(dto.getMarketVolume()).isEqualTo("1.2M");
		assertThat(dto.getEstimateDaypartGender()).isFalse();
		assertThat(dto.getDateFilter().getMode()).isEqualTo(DateFilterModeV1.RANGE);
		assertThat(dto.getDateFilter().getEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(dto.getBreakdownSelections()).singleElement()
				.satisfies(sel -> {
					assertThat(sel.getTacticNum()).isEqualTo(2);
					assertThat(sel.getBreakdowns()).containsExactly("aud", "dev");
				});
		assertThat(dto.getTacticNames()).containsExactly("Display", "CTV");
		assertThat(dto.getTemplateVariant()).isEqualTo(TemplateVariantV1.MPO);
	}

	@Test
	void shouldMapTheRequestedTemplateVariantOntoTheServiceEnumTest() {
		// Given / When
		TemplateVariant mpo = mapper.toTemplateVariant(TemplateVariantV1.MPO);
		TemplateVariant unset = mapper.toTemplateVariant(null);

		// Then: an omitted variant stays null for the service to read as standard
		assertThat(mpo).isEqualTo(TemplateVariant.MPO);
		assertThat(unset).isNull();
	}

	@Test
	void shouldLeaveTheReportTypeUnsetWhenTheStoredCodeIsMissingOrUnknownTest() {
		// Given: drafts stored before the type was recorded, and one carrying a retired code

		// When-Then: neither blows up on the enum — the client falls back to asking the user
		assertThat(mapper.toReportType(null)).isNull();
		assertThat(mapper.toReportType("  ")).isNull();
		assertThat(mapper.toReportType("QBR")).isNull();
		assertThat(mapper.toReportType("EOC")).isEqualTo(ReportTypeV1.EOC);
	}

	@Test
	void shouldMapEveryHistoryRowFieldTest() {
		// Given: a finished report with token usage
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 30);
		ReportSummary summary = new ReportSummary(5L, "EOM", "error", "EOM July", createdAt,
				"https://slides/5", "https://sheet/5", "EOM July.pptx", "https://plan/5", "https://elevate/5",
				"bob@aidigital.com", "Bob", 1200L, 300L, 1500L, 0.42d, false);

		// When:
		ReportSummaryV1 dto = subject.toSummary(summary);

		// Then:
		assertThat(dto.getJobId()).isEqualTo(5L);
		assertThat(dto.getType()).isEqualTo("EOM");
		assertThat(dto.getStatus()).isEqualTo(ReportJobStatusV1.ERROR);
		assertThat(dto.getTitle()).isEqualTo("EOM July");
		assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(dto.getSlideUrl()).isEqualTo("https://slides/5");
		assertThat(dto.getSheetUrl()).isEqualTo("https://sheet/5");
		assertThat(dto.getFileName()).isEqualTo("EOM July.pptx");
		assertThat(dto.getMediaPlanUrl()).isEqualTo("https://plan/5");
		assertThat(dto.getElevateUrl()).isEqualTo("https://elevate/5");
		assertThat(dto.getOwnerEmail()).isEqualTo("bob@aidigital.com");
		assertThat(dto.getOwnerName()).isEqualTo("Bob");
		assertThat(dto.getInputTokens()).isEqualTo(1200L);
		assertThat(dto.getOutputTokens()).isEqualTo(300L);
		assertThat(dto.getTotalTokens()).isEqualTo(1500L);
		assertThat(dto.getCostUsd()).isEqualTo(0.42d);
		assertThat(dto.getDraft()).isFalse();
	}

	@Test
	void shouldWrapTheHistoryIntoTheListEnvelopeTest() {
		// Given: two rows, one still queued and one with no status
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		ReportHistory history = new ReportHistory(List.of(
				new ReportSummary(1L, "EOC", "queued", "A", null, null, null, null, null, null, null, null,
						0, 0, 0, 0d, false),
				new ReportSummary(2L, "EOC", null, "B", null, null, null, null, null, null, null, null,
						0, 0, 0, 0d, false)));

		// When:
		ReportListV1 dto = subject.toReportList(history);

		// Then:
		assertThat(dto.getTotal()).isEqualTo(2);
		assertThat(dto.getReports()).extracting(ReportSummaryV1::getJobId).containsExactly(1L, 2L);
		assertThat(dto.getReports().get(0).getStatus()).isEqualTo(ReportJobStatusV1.QUEUED);
		assertThat(dto.getReports().get(1).getStatus()).isNull();
	}

	@Test
	void shouldReturnNullForNullInputsTest() {
		// Given:
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();

		// When-Then:
		assertThat(subject.toSummary(null)).isNull();
		assertThat(subject.toSummaries(null)).isNull();
		assertThat(subject.toReportList(null)).isNull();
		assertThat(subject.toResume(null)).isNull();
		assertThat(subject.toStatus(null)).isNull();
	}

	@Test
	void shouldMapAnEmptyHistoryWithNullReportsTest() {
		// Given: a history built without a row list
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		ReportHistory history = new ReportHistory(0, null);

		// When:
		ReportListV1 dto = subject.toReportList(history);

		// Then:
		assertThat(dto.getTotal()).isZero();
		assertThat(dto.getReports()).isNull();
	}

	@Test
	void shouldMapAResumeWhoseStoredStateHasNoOptionalPartsTest() {
		// Given: an adopted sheet with an ALL-time window, a null selection entry and no names or variant
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		ReportResume resume = new ReportResume(8L, "https://sheet/8", null, null,
				new ReportResumeState("EOM", null, null, null, new DateFilter(DateFilterMode.ALL, null, null),
						null, Arrays.asList(new BreakdownSelection(1, null), null), null, TemplateVariant.STANDARD));

		// When:
		ReportResumeV1 dto = subject.toResume(resume);

		// Then:
		assertThat(dto.getJobId()).isEqualTo(8L);
		assertThat(dto.getReportType()).isEqualTo(ReportTypeV1.EOM);
		assertThat(dto.getMediaPlanUrl()).isNull();
		assertThat(dto.getBrief()).isNull();
		assertThat(dto.getEstimateDaypartGender()).isNull();
		assertThat(dto.getDateFilter().getMode()).isEqualTo(DateFilterModeV1.ALL);
		assertThat(dto.getDateFilter().getStart()).isNull();
		assertThat(dto.getBreakdownSelections()).hasSize(2);
		assertThat(dto.getBreakdownSelections().get(0).getTacticNum()).isEqualTo(1);
		assertThat(dto.getBreakdownSelections().get(0).getBreakdowns()).isEmpty();
		assertThat(dto.getBreakdownSelections().get(1)).isNull();
		assertThat(dto.getTacticNames()).isEmpty();
		assertThat(dto.getTemplateVariant()).isEqualTo(TemplateVariantV1.STANDARD);
	}

	@Test
	void shouldMapAResumeWithoutWindowSelectionsOrVariantTest() {
		// Given: a draft stored before the window, breakdowns and deck design were recorded
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		ReportResume resume = new ReportResume(9L, null, null, null,
				new ReportResumeState(null, "brief", null, null, new DateFilter(null, null, null),
						Boolean.TRUE, null, List.of(), null));

		// When:
		ReportResumeV1 dto = subject.toResume(resume);

		// Then:
		assertThat(dto.getReportType()).isNull();
		assertThat(dto.getBrief()).isEqualTo("brief");
		assertThat(dto.getEstimateDaypartGender()).isTrue();
		assertThat(dto.getDateFilter().getMode()).isNull();
		assertThat(dto.getBreakdownSelections()).isNull();
		assertThat(dto.getTemplateVariant()).isNull();
	}

	@Test
	void shouldMapAResumeWithoutADateFilterTest() {
		// Given:
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();
		ReportResume resume = new ReportResume(10L, null, null, null,
				new ReportResumeState("EOC", null, null, null, null, null, null, null, TemplateVariant.MPO));

		// When:
		ReportResumeV1 dto = subject.toResume(resume);

		// Then:
		assertThat(dto.getDateFilter()).isNull();
		assertThat(dto.getTemplateVariant()).isEqualTo(TemplateVariantV1.MPO);
	}

	@Test
	void shouldMapEveryRequestedTemplateVariantTest() {
		// Given:
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();

		// When-Then:
		assertThat(subject.toTemplateVariant(TemplateVariantV1.STANDARD)).isEqualTo(TemplateVariant.STANDARD);
		assertThat(subject.toTemplateVariant(TemplateVariantV1.MPO)).isEqualTo(TemplateVariant.MPO);
	}

	@Test
	void shouldRejectAnUnknownStatusWireCodeTest() {
		// Given:
		ReportsApiMapperImpl subject = new ReportsApiMapperImpl();

		// When-Then:
		assertThat(subject.toStatus("done")).isEqualTo(ReportJobStatusV1.DONE);
		assertThatThrownBy(() -> subject.toStatus("paused")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldCarryTheDraftFlagOntoTheHistoryRowTest() {
		// Given: a history row for a resumable sheet build
		ReportSummary summary = new ReportSummary(1L, "EOC", "done", "EOC report", null, null,
				"https://sheet/1", null, null, null, null, null, 0, 0, 0, 0d, true);

		// When: it is mapped to the API DTO
		ReportSummaryV1 dto = mapper.toSummary(summary);

		// Then: the client can tell a draft from a finished report
		assertThat(dto.getDraft()).isTrue();
		assertThat(dto.getSheetUrl()).isEqualTo("https://sheet/1");
		assertThat(dto.getSlideUrl()).isNull();
	}
}
