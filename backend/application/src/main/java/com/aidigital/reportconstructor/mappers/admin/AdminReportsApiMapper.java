package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminReportPageV1;
import com.aidigital.reportconstructor.api.v1.model.AdminReportSortV1;
import com.aidigital.reportconstructor.api.v1.model.SortDirectionV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.mappers.reports.ReportsApiMapper;
import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;
import org.mapstruct.Mapper;

/**
 * Maps a page of the team-wide report history to its V1 DTO; the rows reuse the "My reports"
 * summary mapping.
 */
@Mapper(config = ApplicationMapperConfig.class, uses = ReportsApiMapper.class)
public interface AdminReportsApiMapper {

	/**
	 * Converts one page of the admin report history into its V1 DTO.
	 *
	 * @param page the service page
	 * @return the V1 page with its rows, totals and paging flags
	 */
	AdminReportPageV1 toReportPage(AdminReportPage page);

	/**
	 * Reads the requested sort column as the wire code the service resolves.
	 *
	 * @param sort the requested column, or {@code null} for the default
	 * @return the wire code, or {@code null}
	 */
	default String toSortCode(AdminReportSortV1 sort) {
		return sort == null ? null : sort.getValue();
	}

	/**
	 * Reads the requested order direction as the wire code the service resolves.
	 *
	 * @param dir the requested direction, or {@code null} for the default
	 * @return the wire code, or {@code null}
	 */
	default String toDirectionCode(SortDirectionV1 dir) {
		return dir == null ? null : dir.getValue();
	}
}
