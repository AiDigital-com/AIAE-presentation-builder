package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.drive.DriveShareAudience;
import com.aidigital.reportconstructor.service.admin.services.AdminEmailDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Feeds the Drive integration's {@link DriveShareAudience} from the service's
 * {@link AdminEmailDirectory}: every current admin gets access to every generated file. The directory
 * already degrades to the configured admins when the managed grants cannot be read.
 */
@Component
@RequiredArgsConstructor
public class AdminDriveShareAudience implements DriveShareAudience {

	private final AdminEmailDirectory adminEmailDirectory;

	@Override
	public List<String> additionalRecipients() {
		return adminEmailDirectory.adminEmails();
	}
}
