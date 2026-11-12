package com.aidigital.reportconstructor.externalservices.google.drive;

import com.aidigital.reportconstructor.externalservices.google.GoogleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves who every generated Drive file (deck and EOC workbook alike) is auto-shared
 * with: the statically configured {@code external.google.share-with-emails} list plus
 * the {@link DriveShareAudience} (today every current admin — the config allow-list and the
 * UI-managed grants). Kept as one collaborator so both providers share a single policy.
 */
@Component
@RequiredArgsConstructor
public class DriveShareRecipients {

	private final GoogleProperties googleProperties;
	private final DriveShareAudience shareAudience;

	/**
	 * Returns the de-duplicated, lowercased list of emails a freshly generated file should
	 * be shared with. The audience never throws, so report generation never breaks on it.
	 *
	 * @return share recipients, in configuration order then audience order; never null
	 */
	public List<String> resolve() {
		Set<String> recipients = new LinkedHashSet<>();
		addAll(recipients, googleProperties.getShareWithEmails());
		addAll(recipients, shareAudience.additionalRecipients());
		return List.copyOf(recipients);
	}

	/**
	 * Adds every non-blank email of the source list to the target set, normalized to
	 * lowercase so the same address configured in two places is granted access once.
	 *
	 * @param target set collecting the normalized recipients
	 * @param source emails to add; null/blank entries are skipped
	 */
	void addAll(Set<String> target, List<String> source) {
		if (source == null) {
			return;
		}
		for (String email : source) {
			if (email != null && !email.isBlank()) {
				target.add(email.trim().toLowerCase(Locale.ROOT));
			}
		}
	}
}
