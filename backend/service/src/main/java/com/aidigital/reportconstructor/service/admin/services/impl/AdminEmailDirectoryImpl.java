package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import com.aidigital.reportconstructor.service.admin.services.AdminEmailDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines the configured admin allow-list with the UI-managed admin grants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEmailDirectoryImpl implements AdminEmailDirectory {

	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminUserService adminUserService;

	@Override
	public List<String> adminEmails() {
		List<String> emails = new ArrayList<>(adminAccessPolicy.configAdminEmails());
		emails.addAll(managedAdminEmails());
		return emails;
	}

	/**
	 * Reads the UI-managed admin grants, degrading to an empty list when the lookup fails.
	 *
	 * @return managed admin emails, or empty when they could not be read
	 */
	List<String> managedAdminEmails() {
		try {
			List<String> emails = new ArrayList<>();
			for (AdminUserEntity admin : adminUserService.listAll()) {
				emails.add(admin.getEmail());
			}
			return emails;
		} catch (RuntimeException ex) {
			log.warn("[admin] could not read managed admin emails: {}", ex.getMessage());
			return List.of();
		}
	}
}
