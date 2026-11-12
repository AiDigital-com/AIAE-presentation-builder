package com.aidigital.reportconstructor.admin.controllers;

import com.aidigital.reportconstructor.mappers.admin.AdminManagementApiMapper;
import com.aidigital.reportconstructor.mappers.admin.AdminReportsApiMapper;
import com.aidigital.reportconstructor.mappers.admin.AdminStatsApiMapper;
import com.aidigital.reportconstructor.api.v1.AdminApi;
import com.aidigital.reportconstructor.api.v1.model.AddAdminRequestV1;
import com.aidigital.reportconstructor.api.v1.model.AdminListV1;
import com.aidigital.reportconstructor.api.v1.model.AdminReportPageV1;
import com.aidigital.reportconstructor.api.v1.model.AdminStatsV1;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.admin.services.AdminFailuresService;
import com.aidigital.reportconstructor.service.admin.services.AdminManagementService;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for the admin dashboard: statistics, team-wide report history, and
 * managing admins by email. Authorization (email allow-list) is enforced by the admin
 * services, which throw {@code C004}/403 for non-admin callers.
 */
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

	private final AdminStatsService adminStats;
	private final AdminReportsService adminReports;
	private final AdminManagementService adminManagement;
	private final AdminFailuresService adminFailures;
	private final AdminStatsApiMapper statsMapper;
	private final AdminManagementApiMapper adminMapper;
	private final AdminReportsApiMapper reportsPageMapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<AdminStatsV1> getAdminStats(LocalDate from, LocalDate to) {
		return ResponseEntity.ok(statsMapper.toStats(adminStats.statsFor(caller().email(), from, to)));
	}

	@Override
	public ResponseEntity<Void> clearAdminFailures() {
		adminFailures.clearFailures(caller().email());
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Void> resolveAdminFailure(Long jobId) {
		adminFailures.resolveFailure(caller().email(), jobId);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<AdminReportPageV1> listAllReports(
			Integer page, Integer size, String sort, String dir) {
		return ResponseEntity.ok(reportsPageMapper.toReportPage(
				adminReports.allReports(caller().email(), page, size, sort, dir)));
	}

	@Override
	public ResponseEntity<AdminListV1> listAdmins() {
		return ResponseEntity.ok(adminMapper.toList(adminManagement.listAdmins(caller().email())));
	}

	@Override
	public ResponseEntity<AdminListV1> addAdmin(AddAdminRequestV1 body) {
		return ResponseEntity.ok(adminMapper.toList(adminManagement.addAdmin(caller().email(), body.getEmail())));
	}

	@Override
	public ResponseEntity<AdminListV1> removeAdmin(String email) {
		return ResponseEntity.ok(adminMapper.toList(adminManagement.removeAdmin(caller().email(), email)));
	}

	/**
	 * Resolves the current caller from the security context.
	 *
	 * @return the authenticated caller
	 */
	AppUser caller() {
		return appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
	}
}
