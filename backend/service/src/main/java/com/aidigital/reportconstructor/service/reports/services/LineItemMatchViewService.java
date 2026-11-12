package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.common.security.AppUser;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;

import java.util.List;

/**
 * Runs line-item matching for the wizard and returns the result shaped for its mapping table.
 */
public interface LineItemMatchViewService {

	/**
	 * Matches media-plan tactics to delivery line items.
	 *
	 * @param bqRows   delivery (BigQuery export) rows
	 * @param planRows media-plan rows
	 * @param caller   the signed-in user
	 * @return the mapping-table view of the match
	 */
	LineItemMatchView matchView(List<List<String>> bqRows, List<List<String>> planRows, AppUser caller);
}
