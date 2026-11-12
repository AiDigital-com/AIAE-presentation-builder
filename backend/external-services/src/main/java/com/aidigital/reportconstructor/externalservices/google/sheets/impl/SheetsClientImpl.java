package com.aidigital.reportconstructor.externalservices.google.sheets.impl;

import com.aidigital.reportconstructor.externalservices.google.GoogleCallTimer;
import com.aidigital.reportconstructor.externalservices.google.GoogleCredentialsFactory;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real Google Sheets v4 client. Activated automatically when {@link GoogleCredentialsFactory} is on the
 * context (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is set); otherwise {@link StubSheetsClient} is
 * the only {@link SheetsClient}.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class SheetsClientImpl implements SheetsClient {

	private static final String APPLICATION_NAME = "Report Constructor — AI Digital";
	private static final String META_FIELDS = "properties.title,sheets.properties.title,sheets.properties.hidden";
	private static final String UNTITLED = "Untitled";
	private static final String FULL_TAB_RANGE = "!A1:ZZ";

	private final GoogleCredentialsFactory creds;
	private final Sheets client;
	private final GoogleCallTimer googleCalls;

	/**
	 * Creates the client with a service-account Sheets client.
	 *
	 * @param creds       shared Google transport, JSON factory and service-account credentials
	 * @param googleCalls times every Sheets call on the shared external-call timer
	 */
	public SheetsClientImpl(GoogleCredentialsFactory creds, GoogleCallTimer googleCalls) {
		this.creds = creds;
		this.googleCalls = googleCalls;
		this.client = new Sheets.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public SheetsTabValues readTab(String spreadsheetId, String tab, String userAccessToken) {
		boolean asUser = userAccessToken != null && !userAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userAccessToken) : client;
		try {
			Spreadsheet meta = googleCalls.execute(sheetsClient.spreadsheets().get(spreadsheetId)
					.setFields(META_FIELDS));
			String title = meta.getProperties() == null ? UNTITLED : meta.getProperties().getTitle();
			// Expose visible tabs only. Media plans routinely keep stale copies of
			// "Proposal"/"Estimates" as hidden tabs (often duplicating the visible
			// tab's name modulo a trailing space); including them lets the tool read
			// an outdated proposal. The latest data always lives on a visible tab.
			List<String> tabs = visibleTabs(meta);

			// Verify the tab exists before reading its range. Optional tabs (e.g. "Geo")
			// are often absent, and Google reports a missing sheet as a confusing
			// "Unable to parse range" 400. Resolve against the visible tabs, tolerating
			// trailing whitespace/case, so a request for "Proposal" lands on a visible
			// "Proposal " rather than a hidden exact-name duplicate.
			String resolvedTab = resolveVisibleTab(tab, tabs);
			if (resolvedTab == null) {
				throw new SheetsTabNotFoundException(tab, tabs);
			}

			String range = "'" + resolvedTab.replace("'", "''") + "'" + FULL_TAB_RANGE;
			ValueRange vr = googleCalls.execute(sheetsClient.spreadsheets().values().get(spreadsheetId, range));
			return new SheetsTabValues(spreadsheetId, title, resolvedTab, tabs, toStrings(vr.getValues()));
		} catch (GoogleJsonResponseException ex) {
			int code = ex.getStatusCode();
			String googleMsg = ex.getDetails() != null && ex.getDetails().getMessage() != null
					? ex.getDetails().getMessage()
					: ex.getStatusMessage();
			log.error("[sheets] fetch failed for {} tab={} asUser={} (HTTP {})", spreadsheetId, tab, asUser, code, ex);
			throw new SheetsExternalException(code, googleMsg, ex);
		} catch (IOException ex) {
			log.error("[sheets] fetch failed for {} tab={}", spreadsheetId, tab, ex);
			throw new SheetsExternalException(null, ex.getMessage(), ex);
		}
	}

	/**
	 * Lists the titles of the spreadsheet's visible (non-hidden) tabs, in sheet order.
	 *
	 * @param meta spreadsheet metadata carrying the tab properties
	 * @return visible tab titles
	 */
	List<String> visibleTabs(Spreadsheet meta) {
		List<String> tabs = new ArrayList<>();
		if (meta.getSheets() == null) {
			return tabs;
		}
		for (Sheet s : meta.getSheets()) {
			SheetProperties props = s.getProperties();
			if (props != null && props.getTitle() != null && !Boolean.TRUE.equals(props.getHidden())) {
				tabs.add(props.getTitle());
			}
		}
		return tabs;
	}

	/**
	 * Converts the API's cell values to strings, rendering empty cells as {@code ""}.
	 *
	 * @param values cell values as returned by the API; may be {@code null} for an empty tab
	 * @return the rows as strings; empty when the tab has no values
	 */
	List<List<String>> toStrings(List<List<Object>> values) {
		if (values == null) {
			return List.of();
		}
		List<List<String>> rows = new ArrayList<>(values.size());
		for (List<Object> r : values) {
			List<String> row = new ArrayList<>(r.size());
			for (Object cell : r) {
				row.add(cell == null ? "" : cell.toString());
			}
			rows.add(row);
		}
		return rows;
	}

	/**
	 * Resolves a requested tab name against the visible tabs of the spreadsheet.
	 * Prefers an exact match; falls back to a trimmed, case-insensitive match so a
	 * request for {@code "Proposal"} still resolves when the current tab is named
	 * {@code "Proposal "} (a common pattern where an old exact-named tab was
	 * hidden and a fresh copy with a trailing space kept visible).
	 *
	 * @param requested   the tab name the caller asked for
	 * @param visibleTabs the titles of the spreadsheet's visible tabs
	 * @return the actual visible tab title to read, or {@code null} when none match
	 */
	String resolveVisibleTab(String requested, List<String> visibleTabs) {
		if (requested == null) {
			return null;
		}
		if (visibleTabs.contains(requested)) {
			return requested;
		}
		String normalized = requested.trim().toLowerCase(Locale.ROOT);
		for (String candidate : visibleTabs) {
			if (candidate.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Builds a Sheets client authenticated as the signed-in user via their
	 * short-lived Google OAuth access token (Clerk-brokered), so the spreadsheet
	 * only needs to be viewable by that user rather than shared with the service
	 * account. Mirrors {@code RealSlidesProvider#buildSlides}.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return a Sheets client bound to the user's credentials
	 */
	Sheets buildSheets(String accessToken) {
		return new Sheets.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	/**
	 * Wraps a raw Google OAuth access token as an HTTP request initializer that
	 * authenticates each request as the token's owner.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return an initializer bound to that user's credentials
	 */
	HttpRequestInitializer userInitializer(String accessToken) {
		return creds.withTimeout(new HttpCredentialsAdapter(
				GoogleCredentials.create(new AccessToken(accessToken, null))));
	}
}
