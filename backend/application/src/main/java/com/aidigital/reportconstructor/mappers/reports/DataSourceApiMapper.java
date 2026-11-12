package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryRowV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummary;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps Google data-source service records (connection status, sheet reads) to their V1 API DTOs.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface DataSourceApiMapper {

	/** Error code the client keys its manual tab picker on. */
	String TAB_NOT_FOUND_ERROR = "tab_not_found";

	/**
	 * Converts the Google connection status into its V1 DTO.
	 *
	 * @param status the service connection status
	 * @return the V1 connection status DTO
	 */
	GoogleConnectionStatusV1 toStatus(GoogleConnectionStatus status);

	/**
	 * Converts a successful sheet read into its V1 result DTO.
	 *
	 * @param data the service sheet data
	 * @return the V1 sheet read result with {@code ok=true}
	 */
	@Mapping(target = "ok", constant = "true")
	@Mapping(target = "error", ignore = true)
	SheetReadResultV1 toSuccess(SheetData data);

	/**
	 * Builds the V1 result returned when a requested sheet tab is missing. The
	 * workbook's visible tabs are echoed back so the client can offer them as a
	 * manual media-plan tab picker instead of failing outright.
	 *
	 * @param tab  the requested tab name
	 * @param tabs the workbook's visible tab titles (empty when unknown)
	 * @return a V1 result with {@code ok=false}, error {@code "tab_not_found"} and the visible tabs
	 */
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "ok", constant = "false")
	@Mapping(target = "error", constant = TAB_NOT_FOUND_ERROR)
	@Mapping(target = "tab", source = "tab")
	@Mapping(target = "tabs", source = "tabs")
	@Mapping(target = "rows", constant = "0")
	@Mapping(target = "cols", constant = "0")
	SheetReadResultV1 tabNotFound(String tab, List<String> tabs);

	/**
	 * Converts the outcome of a tab read: the tab's data when it was found, otherwise the tab-not-found
	 * result echoing the workbook's visible tabs.
	 *
	 * @param tab  the requested tab name
	 * @param read the service read outcome
	 * @return the V1 sheet read result
	 */
	default SheetReadResultV1 toReadResult(String tab, SheetTabRead read) {
		return read.found() ? toSuccess(read.data()) : tabNotFound(tab, read.visibleTabs());
	}

	/**
	 * Converts one summary-table service row into its V1 DTO (fields map by name).
	 *
	 * @param row the service summary row
	 * @return the V1 summary row DTO
	 */
	SheetSummaryRowV1 toSummaryRow(SheetSummaryRow row);

	/**
	 * Wraps the workbook read-back — per-tactic rows plus the campaign-context cells — into the V1
	 * result DTO.
	 *
	 * @param summary the service summary read back from the workbook
	 * @return the V1 summary result carrying the mapped rows and context cells
	 */
	@Mapping(target = "rows", source = "rows", defaultExpression = "java(java.util.List.of())")
	SheetSummaryResultV1 toSummary(SheetSummary summary);
}
