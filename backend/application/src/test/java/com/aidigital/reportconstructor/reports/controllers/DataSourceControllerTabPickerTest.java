package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.mappers.reports.DataSourceApiMapperImpl;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the tab-not-found response that powers the manual media-plan tab picker: the mapper
 * echoing the workbook's visible tabs. Detecting the missing tab lives in
 * {@code SheetTabReadServiceImpl} and is tested there.
 */
class DataSourceControllerTabPickerTest {

	private final DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();

	@Test
	void shouldEchoVisibleTabsInTabNotFoundResultTest() {
		// Given: the workbook's visible tabs and the missing requested tab
		List<String> tabs = List.of("Overview", "Plan v2", "Basic");

		// When
		SheetReadResultV1 result = mapper.tabNotFound("Proposal", tabs);

		// Then: not ok, coded tab_not_found, and the visible tabs are carried back
		assertThat(result.getOk()).isFalse();
		assertThat(result.getError()).isEqualTo("tab_not_found");
		assertThat(result.getTab()).isEqualTo("Proposal");
		assertThat(result.getTabs()).containsExactly("Overview", "Plan v2", "Basic");
	}

	@Test
	void shouldMapAMissingTabReadToTheTabNotFoundResultTest() {
		// Given: the service found no such tab and returned the workbook's visible tabs
		SheetTabRead read = new SheetTabRead(null, List.of("Overview", "Plan v2"));

		// When
		SheetReadResultV1 result = mapper.toReadResult("Proposal", read);

		// Then
		assertThat(result.getOk()).isFalse();
		assertThat(result.getError()).isEqualTo("tab_not_found");
		assertThat(result.getTabs()).containsExactly("Overview", "Plan v2");
	}
}
