package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the tab-not-found path that powers the manual media-plan tab picker: a missing tab
 * becomes a normal result carrying the workbook's visible tabs; every other failure propagates.
 */
@ExtendWith(MockitoExtension.class)
class SheetTabReadServiceImplTest {

	@Mock
	private SheetQueryService sheetQuery;

	@InjectMocks
	private SheetTabReadServiceImpl service;

	@Test
	void shouldReturnTheTabDataWhenTheTabExistsTest() {
		// Given:
		SheetData data = mock(SheetData.class);
		when(sheetQuery.fetchTab("http://sheet", "Basic", "user-1")).thenReturn(data);

		// When:
		SheetTabRead read = service.readTab("http://sheet", "Basic", "user-1");

		// Then:
		assertThat(read.found()).isTrue();
		assertThat(read.data()).isSameAs(data);
		assertThat(read.visibleTabs()).isEmpty();
	}

	@Test
	void shouldReturnTheVisibleTabsWhenTheTabIsMissingTest() {
		// Given: the adapter's C001 carrying the visible tabs as a delimited parameter
		AppException notFound = new AppException(ErrorReason.C001,
				new ValidationParameter("param0", "Tab \"Proposal\" not found"),
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM,
						String.join(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER, List.of("Overview", "Plan v2"))));
		when(sheetQuery.fetchTab("http://sheet", "Proposal", "user-1")).thenThrow(notFound);

		// When:
		SheetTabRead read = service.readTab("http://sheet", "Proposal", "user-1");

		// Then:
		assertThat(read.found()).isFalse();
		assertThat(read.data()).isNull();
		assertThat(read.visibleTabs()).containsExactly("Overview", "Plan v2");
	}

	@Test
	void shouldRethrowFailuresOtherThanNotFoundTest() {
		// Given: a Google API failure (not C001)
		AppException other = new AppException(ErrorReason.C003, "Sheets API timed out");
		when(sheetQuery.fetchTab("http://sheet", "Basic", "user-1")).thenThrow(other);

		// When-Then:
		assertThat(catchThrowable(() -> service.readTab("http://sheet", "Basic", "user-1"))).isSameAs(other);
	}

	@Test
	void shouldTreatEveryNotFoundFromTheSheetReadAsAMissingTabTest() {
		// Given: any C001 — its message always reads "Resource not found: …", so the check cannot tell a
		// missing tab from a missing spreadsheet. Pinned as the behaviour carried over from the controller.
		AppException notFound = new AppException(ErrorReason.C001, "Spreadsheet does not exist");
		when(sheetQuery.fetchTab("http://sheet", "Basic", "user-1")).thenThrow(notFound);

		// When:
		SheetTabRead read = service.readTab("http://sheet", "Basic", "user-1");

		// Then:
		assertThat(read.found()).isFalse();
		assertThat(read.visibleTabs()).isEmpty();
	}

	@Test
	void shouldExtractVisibleTabsFromExceptionParameterTest() {
		// Given: a C001 exception carrying the visible tabs as a delimited parameter
		AppException ex = new AppException(ErrorReason.C001,
				new ValidationParameter("param0", "Tab \"Proposal\" not found"),
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM,
						String.join(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER, List.of("Overview", "Plan v2"))));

		// When:
		List<String> tabs = service.visibleTabsFrom(ex);

		// Then:
		assertThat(tabs).containsExactly("Overview", "Plan v2");
	}

	@Test
	void shouldReturnEmptyWhenExceptionCarriesNoVisibleTabsTest() {
		// Given: a C001 exception without the visible-tabs parameter
		AppException ex = new AppException(ErrorReason.C001, "Tab \"Geo\" not found");

		// When:
		List<String> tabs = service.visibleTabsFrom(ex);

		// Then:
		assertThat(tabs).isEmpty();
	}

	@Test
	void shouldReturnEmptyWhenVisibleTabsParameterIsBlankTest() {
		// Given: the visible-tabs parameter is present but empty (workbook has no visible tabs)
		AppException ex = new AppException(ErrorReason.C001,
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM, ""));

		// When:
		List<String> tabs = service.visibleTabsFrom(ex);

		// Then:
		assertThat(tabs).isEmpty();
	}
}
