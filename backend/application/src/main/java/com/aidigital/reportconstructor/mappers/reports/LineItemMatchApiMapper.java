package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.IdNamingV1;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.dto.TacticMapping;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the line-item matching view (per-tactic rows, id&rarr;naming index, channel&rarr;ids grouping and
 * the auto-match summary) to its V1 DTO.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface LineItemMatchApiMapper {

	/**
	 * Converts the matching view into its V1 DTO. Warnings are not produced by matching yet, so the
	 * DTO's empty default stands.
	 *
	 * @param view the service matching view
	 * @return the V1 line-item match result
	 */
	@Mapping(target = "warnings", ignore = true)
	LineItemMatchResultV1 toResult(LineItemMatchView view);

	/**
	 * Converts one tactic row into its V1 mapping entry. Budget and channel expectations are filled in
	 * later by the wizard, not by matching.
	 *
	 * @param row the service tactic row
	 * @return the V1 mapping entry
	 */
	@Mapping(target = "expectedChannel", ignore = true)
	@Mapping(target = "unitPrice", ignore = true)
	@Mapping(target = "monthlyBudget", ignore = true)
	@Mapping(target = "rateType", ignore = true)
	MappingEntryV1 toEntry(TacticMapping row);

	/**
	 * Converts one line item's metadata into its V1 naming entry (the id is the map key).
	 *
	 * @param meta the line item's metadata
	 * @return the V1 naming entry
	 */
	IdNamingV1 toIdNaming(LineItemMeta meta);
}
