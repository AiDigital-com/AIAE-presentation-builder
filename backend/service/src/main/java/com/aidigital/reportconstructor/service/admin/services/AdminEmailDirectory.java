package com.aidigital.reportconstructor.service.admin.services;

import java.util.List;

/**
 * Read-only view of who currently has admin access, exposed as plain email addresses so
 * integration adapters (Drive auto-share) never touch the admin policy or its persistence.
 */
public interface AdminEmailDirectory {

	/**
	 * Returns every current admin email: the configured allow-list ({@code app.admin.emails})
	 * followed by the UI-managed grants. A failure to read the managed grants is logged and
	 * degrades to the configured list, so callers on the report path never break on it.
	 *
	 * @return admin emails as stored (not normalized); never null
	 */
	List<String> adminEmails();
}
