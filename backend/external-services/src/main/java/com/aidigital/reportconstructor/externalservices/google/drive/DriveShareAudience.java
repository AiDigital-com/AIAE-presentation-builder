package com.aidigital.reportconstructor.externalservices.google.drive;

import java.util.List;

/**
 * Supplies the people, beyond the configured {@code external.google.share-with-emails}, that every
 * generated Drive file is auto-shared with. Owned by the Drive integration so it never depends on the
 * business side; the application provides the implementation (today: the current admins).
 */
public interface DriveShareAudience {

	/**
	 * Returns the additional recipients' emails. Implementations must not throw on lookup failures:
	 * report generation must never break on who a file could be shared with.
	 *
	 * @return emails as stored (not normalized); never null
	 */
	List<String> additionalRecipients();
}
