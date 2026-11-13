package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryResultV1;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummary;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataSourceApiMapper} through its generated implementation.
 */
class DataSourceApiMapperTest {

	@Test
	void shouldMapEveryConnectionStatusFieldTest() {
		// Given:
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		GoogleConnectionStatus status = new GoogleConnectionStatus(true, false, "alice@aidigital.com",
				List.of("drive", "sheets"), "https://connect");

		// When:
		GoogleConnectionStatusV1 dto = mapper.toStatus(status);

		// Then:
		assertThat(dto.getConnected()).isTrue();
		assertThat(dto.getMockMode()).isFalse();
		assertThat(dto.getEmail()).isEqualTo("alice@aidigital.com");
		assertThat(dto.getScopes()).containsExactly("drive", "sheets");
		assertThat(dto.getConnectUrl()).isEqualTo("https://connect");
	}

	@Test
	void shouldKeepTheEmptyScopesDefaultWhenTheStatusHasNoScopesTest() {
		// Given: a disconnected mock-mode status with no scopes
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		GoogleConnectionStatus status = new GoogleConnectionStatus(false, true, null, null, null);

		// When:
		GoogleConnectionStatusV1 dto = mapper.toStatus(status);

		// Then:
		assertThat(dto.getConnected()).isFalse();
		assertThat(dto.getMockMode()).isTrue();
		assertThat(dto.getEmail()).isNull();
		assertThat(dto.getScopes()).isEmpty();
		assertThat(dto.getConnectUrl()).isNull();
	}

	@Test
	void shouldReturnNullForNullInputsTest() {
		// Given:
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();

		// When-Then:
		assertThat(mapper.toStatus(null)).isNull();
		assertThat(mapper.toSuccess(null)).isNull();
		assertThat(mapper.tabNotFound(null, null)).isNull();
		assertThat(mapper.toSummaryRow(null)).isNull();
		assertThat(mapper.toSummary(null)).isNull();
	}

	@Test
	void shouldMapASuccessfulSheetReadTest() {
		// Given:
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		SheetData data = new SheetData("sheet-1", "Media plan", "Plan", List.of("Plan", "Notes"), 3, 2,
				List.of("Tactic", "Budget"), List.of(List.of("Display", "100")),
				List.of(List.of("Tactic", "Budget"), List.of("Display", "100")));

		// When:
		SheetReadResultV1 dto = mapper.toSuccess(data);

		// Then:
		assertThat(dto.getOk()).isTrue();
		assertThat(dto.getError()).isNull();
		assertThat(dto.getSheetId()).isEqualTo("sheet-1");
		assertThat(dto.getTitle()).isEqualTo("Media plan");
		assertThat(dto.getTab()).isEqualTo("Plan");
		assertThat(dto.getTabs()).containsExactly("Plan", "Notes");
		assertThat(dto.getRows()).isEqualTo(3);
		assertThat(dto.getCols()).isEqualTo(2);
		assertThat(dto.getHeaders()).containsExactly("Tactic", "Budget");
		assertThat(dto.getPreview()).containsExactly(List.of("Display", "100"));
		assertThat(dto.getRawRows()).containsExactly(List.of("Tactic", "Budget"), List.of("Display", "100"));
	}

	@Test
	void shouldKeepEmptyListDefaultsWhenTheSheetReadHasNoListsTest() {
		// Given: an empty tab with no list data
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		SheetData data = new SheetData("sheet-2", "Empty", "Blank", null, 0, 0, null, null, null);

		// When:
		SheetReadResultV1 dto = mapper.toSuccess(data);

		// Then:
		assertThat(dto.getOk()).isTrue();
		assertThat(dto.getTab()).isEqualTo("Blank");
		assertThat(dto.getTabs()).isEmpty();
		assertThat(dto.getHeaders()).isEmpty();
		assertThat(dto.getPreview()).isEmpty();
		assertThat(dto.getRawRows()).isEmpty();
	}

	@Test
	void shouldMapAFoundTabReadToTheSuccessResultTest() {
		// Given: the service found the requested tab
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		SheetData data = new SheetData("sheet-3", "Plan", "Proposal", List.of("Proposal"), 1, 1,
				List.of("A"), List.of(), List.of(List.of("A")));
		SheetTabRead read = new SheetTabRead(data, List.of("Proposal"));

		// When:
		SheetReadResultV1 dto = mapper.toReadResult("Proposal", read);

		// Then:
		assertThat(dto.getOk()).isTrue();
		assertThat(dto.getError()).isNull();
		assertThat(dto.getSheetId()).isEqualTo("sheet-3");
		assertThat(dto.getTab()).isEqualTo("Proposal");
	}

	@Test
	void shouldBuildTabNotFoundWithoutVisibleTabsTest() {
		// Given / When: the workbook's tabs are unknown
		SheetReadResultV1 dto = new DataSourceApiMapperImpl().tabNotFound("Proposal", null);

		// Then: the result still reports the missing tab, with zero dimensions
		assertThat(dto.getOk()).isFalse();
		assertThat(dto.getError()).isEqualTo(DataSourceApiMapper.TAB_NOT_FOUND_ERROR);
		assertThat(dto.getTab()).isEqualTo("Proposal");
		assertThat(dto.getTabs()).isEmpty();
		assertThat(dto.getRows()).isZero();
		assertThat(dto.getCols()).isZero();
	}

	@Test
	void shouldBuildTabNotFoundWhenOnlyTheTabsAreKnownTest() {
		// Given / When:
		SheetReadResultV1 dto = new DataSourceApiMapperImpl().tabNotFound(null, List.of("Overview"));

		// Then:
		assertThat(dto.getOk()).isFalse();
		assertThat(dto.getTab()).isNull();
		assertThat(dto.getTabs()).containsExactly("Overview");
	}

	@Test
	void shouldMapTheSummaryRowsAndContextCellsTest() {
		// Given: two tactic rows (one missing) and both context cells
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		SheetSummary summary = new SheetSummary(
				Arrays.asList(new SheetSummaryRow("Display", "100", "90", "$1,000", "$950"), null),
				"the brief", "the log");

		// When:
		SheetSummaryResultV1 dto = mapper.toSummary(summary);

		// Then:
		assertThat(dto.getRfpInfo()).isEqualTo("the brief");
		assertThat(dto.getChangeLog()).isEqualTo("the log");
		assertThat(dto.getRows()).hasSize(2);
		assertThat(dto.getRows().get(0).getTactic()).isEqualTo("Display");
		assertThat(dto.getRows().get(0).getUnitPlan()).isEqualTo("100");
		assertThat(dto.getRows().get(0).getUnitFact()).isEqualTo("90");
		assertThat(dto.getRows().get(0).getSpendPlan()).isEqualTo("$1,000");
		assertThat(dto.getRows().get(0).getSpendFact()).isEqualTo("$950");
		assertThat(dto.getRows().get(1)).isNull();
	}

	@Test
	void shouldDefaultMissingSummaryRowsToAnEmptyListTest() {
		// Given: a workbook with no summary table and empty context cells
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		SheetSummary summary = new SheetSummary(null, null, null);

		// When:
		SheetSummaryResultV1 dto = mapper.toSummary(summary);

		// Then:
		assertThat(dto.getRows()).isNotNull().isEmpty();
		assertThat(dto.getRfpInfo()).isNull();
		assertThat(dto.getChangeLog()).isNull();
	}
}
