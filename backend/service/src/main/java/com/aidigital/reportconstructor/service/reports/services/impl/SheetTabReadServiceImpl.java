package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.services.SheetTabReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Default {@link SheetTabReadService}: delegates to the {@link SheetQueryService} port and converts
 * its tab-not-found {@link AppException} into a {@link SheetTabRead} carrying the visible tabs.
 */
@Service
@RequiredArgsConstructor
public class SheetTabReadServiceImpl implements SheetTabReadService {

	/** Message fragment the Sheets adapter puts on a missing-tab {@code C001}. */
	private static final String NOT_FOUND_FRAGMENT = "not found";

	private final SheetQueryService sheetQuery;

	@Override
	public SheetTabRead readTab(String spreadsheetUrl, String tab, String callerUserId) {
		try {
			return new SheetTabRead(sheetQuery.fetchTab(spreadsheetUrl, tab, callerUserId), List.of());
		} catch (AppException ex) {
			if (isTabNotFound(ex)) {
				return new SheetTabRead(null, visibleTabsFrom(ex));
			}
			throw ex;
		}
	}

	/**
	 * Whether the exception is the adapter's "tab not found" error rather than any other failure.
	 *
	 * @param ex the exception thrown by the sheet read
	 * @return {@code true} for a {@code C001} whose message says the tab was not found
	 */
	boolean isTabNotFound(AppException ex) {
		return ErrorReason.C001.getCode().equals(ex.getCode())
				&& ex.getMessage() != null
				&& ex.getMessage().toLowerCase(Locale.ROOT).contains(NOT_FOUND_FRAGMENT);
	}

	/**
	 * Extracts the workbook's visible tab titles that a tab-not-found {@link AppException} carries in
	 * its {@link SheetQueryService#TAB_NOT_FOUND_TABS_PARAM} structured parameter.
	 *
	 * @param ex the caught tab-not-found exception
	 * @return the visible tab titles, or an empty list when none were carried
	 */
	List<String> visibleTabsFrom(AppException ex) {
		return ex.getValidationMessage().getParameters().stream()
				.filter(p -> SheetQueryService.TAB_NOT_FOUND_TABS_PARAM.equals(p.getCode()))
				.findFirst()
				.map(p -> p.getValue().isEmpty()
						? List.<String>of()
						: List.of(p.getValue().split(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER)))
				.orElseGet(List::of);
	}
}
